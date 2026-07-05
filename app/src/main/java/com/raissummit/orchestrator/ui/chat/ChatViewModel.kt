package com.raissummit.orchestrator.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.raissummit.orchestrator.data.ConversationManager
import com.raissummit.orchestrator.data.models.OrchestratorResponse
import com.raissummit.orchestrator.data.repository.SettingsRepository
import com.raissummit.orchestrator.orchestrator.ConversationHistory
import com.raissummit.orchestrator.orchestrator.EngineEvent
import com.raissummit.orchestrator.orchestrator.EngineState
import com.raissummit.orchestrator.orchestrator.OrchestratorEngine
import com.raissummit.orchestrator.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val confirmationMessage: String? = null,
    val followUpQuestion: String? = null,
    val engineState: EngineState = EngineState.Idle,
    val streamingText: String? = null,
    val streamingThinking: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val settingsRepository = SettingsRepository(application)
    val conversationManager = ConversationManager(application)
    private val gson = Gson()

    private val tools = mapOf(
        "get_installed_apps" to GetInstalledAppsTool(),
        "send_sms" to SendSmsTool(),
        "make_call" to MakeCallTool(),
        "share_text" to ShareTextTool(),
        "open_app" to OpenAppTool(),
        "tap" to TapTool(),
        "type_text" to TypeTextTool(),
        "scroll" to ScrollTool(),
        "go_back" to GoBackTool(),
        "set_alarm" to SetAlarmTool(),
        "take_screenshot" to TakeScreenshotTool(),
        "get_device_location" to GetDeviceLocationTool(),
        "get_battery_info" to GetBatteryInfoTool(),
        "get_network_status" to GetNetworkStatusTool(),
        "search_web" to SearchWebTool(),
        "set_note" to SetNoteTool(),
        "ask_user_confirmation" to AskUserConfirmationTool(),
        "ask_followup_question" to AskFollowupQuestionTool()
    )

    private var activeHistory: ConversationHistory? = null
    private var isProcessingMessage = false

    suspend fun loadConversation(id: String) {
        activeHistory = null
        _uiState.value = ChatUiState()
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || isProcessingMessage) return
        isProcessingMessage = true

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + ChatMessage.User(text),
            isProcessing = true
        )

        viewModelScope.launch {
            try {
                val path = withContext(Dispatchers.IO) {
                    settingsRepository.modelPath.first()
                }
                if (path.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + ChatMessage.Error("Modèle non téléchargé. Allez dans Paramètres."),
                        isProcessing = false
                    )
                    isProcessingMessage = false
                    return@launch
                }

                val history = getOrCreateHistory()
                history.addUserMessage(text)
                val prompt = buildPrompt(history)

                val fullResponse = runInference(prompt, path)
                if (fullResponse == null) {
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + ChatMessage.Assistant("⚠ L'inférence a pris trop de temps."),
                        streamingText = null,
                        streamingThinking = null,
                        isProcessing = false
                    )
                    isProcessingMessage = false
                    return@launch
                }

                Log.d("ChatViewModel", "Full response (${fullResponse.length} chars): ${fullResponse.take(1000)}")
                val parsed = tryParseResponse(fullResponse)
                if (parsed == null) {
                    Log.w("ChatViewModel", "Parse failed, showing raw: ${fullResponse.take(300)}")
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + ChatMessage.Assistant(fullResponse.take(800)),
                        streamingText = null,
                        streamingThinking = null,
                        isProcessing = false
                    )
                    isProcessingMessage = false
                    return@launch
                }

                processParsedResponse(parsed, history)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + ChatMessage.Error("Erreur : ${e.message}"),
                    streamingText = null,
                    streamingThinking = null,
                    isProcessing = false
                )
            }
            isProcessingMessage = false
        }
    }

    private suspend fun runInference(prompt: String, path: String): String? {
        var accumulated = ""

        val streamResult = withTimeoutOrNull(120_000) {
            LiteRTInference.generateStreaming(prompt, path).collect { token ->
                if (token == "__LOADING__") return@collect
                accumulated += token
                val parsed = tryParseResponse(accumulated)
                if (parsed != null) {
                    _uiState.value = _uiState.value.copy(
                        streamingText = parsed.final_answer ?: "[Tool calls pending...]",
                        streamingThinking = parsed.thinking
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        streamingText = accumulated.takeLast(150)
                    )
                }
            }
            accumulated
        }

        return streamResult
    }

    private suspend fun processParsedResponse(
        response: OrchestratorResponse,
        history: ConversationHistory
    ) {
        if (response.final_answer != null) {
            val msgs = _uiState.value.messages + ChatMessage.Assistant(
                text = response.final_answer,
                thinking = response.thinking
            )
            history.addAssistantMessage(response.final_answer)
            _uiState.value = _uiState.value.copy(
                messages = msgs,
                streamingText = null,
                streamingThinking = null,
                isProcessing = false
            )
            persistHistory(history)
            return
        }

        if (response.tool_calls.isNotEmpty()) {
            var msgs = _uiState.value.messages
            var errorOccurred = false

            for (toolCall in response.tool_calls) {
                if (errorOccurred) break

                val tool = tools[toolCall.name]
                if (tool == null) {
                    msgs = msgs + ChatMessage.Error("Outil inconnu : ${toolCall.name}")
                    break
                }

                val result = withContext(Dispatchers.IO) {
                    tool.execute(getApplication(), toolCall.parameters)
                }

                val resultStr = when (result) {
                    is ToolResult.Success -> "✓ Succès"
                    is ToolResult.SuccessWithData -> "✓ ${result.data.toString().take(200)}"
                    is ToolResult.Error -> "✗ ${result.message}"
                    else -> ""
                }
                msgs = msgs + ChatMessage.ToolResult(toolCall.name, resultStr)
                history.addToolResult(toolCall.name, result.toString())

                if (result is ToolResult.Error) {
                    errorOccurred = true
                }
            }

            if (!errorOccurred) {
                msgs = msgs + ChatMessage.Assistant("Toutes les actions ont été exécutées.")
            }

            _uiState.value = _uiState.value.copy(
                messages = msgs,
                streamingText = null,
                streamingThinking = null,
                isProcessing = false
            )
            persistHistory(history)
        }
    }

    private fun tryParseResponse(text: String): OrchestratorResponse? {
        val jsonStart = text.indexOf("{")
        val jsonEnd = text.lastIndexOf("}")
        if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) return null
        val json = text.substring(jsonStart, jsonEnd + 1)
        return try {
            gson.fromJson(json, OrchestratorResponse::class.java)
        } catch (_: Exception) { null }
    }

    private fun getOrCreateHistory(): ConversationHistory {
        val existing = activeHistory
        if (existing != null) return existing
        val h = ConversationHistory()
        activeHistory = h
        return h
    }

    private fun buildPrompt(history: ConversationHistory): String {
        val systemPrompt = com.raissummit.orchestrator.orchestrator.SystemPromptBuilder().build(
            com.raissummit.orchestrator.data.models.ToolSchema.allTools
        )
        val historyText = history.getHistory().joinToString("\n") { "[${it.role}]: ${it.content}" }
        return "$systemPrompt\n\n# HISTORIQUE\n$historyText\n\n[assistant]:"
    }

    private suspend fun persistHistory(history: ConversationHistory) {
        try {
            val id = conversationManager.activeConversationId.first()
            if (id.isNotBlank()) {
                conversationManager.saveTurns(id, history.getHistory())
            }
        } catch (_: Exception) { }
    }

    fun confirmAction() {
        val msg = _uiState.value.confirmationMessage ?: return
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + ChatMessage.Assistant("✅ Confirmé : $msg"),
            confirmationMessage = null,
            isProcessing = false
        )
    }

    fun cancelAction() {
        _uiState.value = _uiState.value.copy(
            confirmationMessage = null,
            followUpQuestion = null,
            isProcessing = false,
            streamingText = null,
            streamingThinking = null
        )
    }

    fun provideFollowUpAnswer(answer: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + ChatMessage.User(answer),
            followUpQuestion = null,
            isProcessing = true
        )
    }

    fun resetConversation() {
        activeHistory = null
        _uiState.value = ChatUiState()
    }

    override fun onCleared() {
        super.onCleared()
        LiteRTInference.close()
    }
}
