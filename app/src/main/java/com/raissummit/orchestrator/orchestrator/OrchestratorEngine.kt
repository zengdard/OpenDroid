package com.raissummit.orchestrator.orchestrator

import android.content.Context
import android.util.Log
import com.raissummit.orchestrator.data.models.OrchestratorResponse
import com.raissummit.orchestrator.data.models.ToolCall
import com.raissummit.orchestrator.tools.Tool
import com.raissummit.orchestrator.tools.ToolResult

sealed class EngineState {
    data object Idle : EngineState()
    data class AwaitingConfirmation(
        val toolCall: ToolCall,
        val pendingToolCalls: List<ToolCall>,
        val accumulatedAnswer: String?
    ) : EngineState()
    data class AwaitingFollowUp(
        val question: String,
        val pendingToolCalls: List<ToolCall>,
        val accumulatedAnswer: String?
    ) : EngineState()
    data class Processing(val turnCount: Int) : EngineState()
}

sealed class EngineEvent {
    data class TextResponse(val text: String) : EngineEvent()
    data class ToolExecuted(val toolName: String, val result: ToolResult) : EngineEvent()
    data class Error(val message: String) : EngineEvent()
    data class ConfirmationRequested(val message: String) : EngineEvent()
    data class FollowUpRequested(val question: String) : EngineEvent()
}

class OrchestratorEngine(
    private val context: Context,
    private val tools: Map<String, Tool>,
    private val conversationHistory: ConversationHistory = ConversationHistory(),
    private val systemPromptBuilder: SystemPromptBuilder = SystemPromptBuilder(),
    private val jsonParser: JsonParser = JsonParser()
) {
    companion object {
        private const val TAG = "OrchestratorEngine"
        private const val MAX_CONSECUTIVE_ACTIONS = 5
    }

    private var state: EngineState = EngineState.Idle
    private var consecutiveActions = 0

    suspend fun processUserInput(
        input: String,
        modelInference: suspend (String) -> String
    ): List<EngineEvent> {
        val events = mutableListOf<EngineEvent>()
        state = EngineState.Processing(0)

        conversationHistory.addUserMessage(input)
        val prompt = buildPrompt()
        val modelOutput = modelInference(prompt)

        val parseResult = jsonParser.parseResponse(modelOutput)
        if (parseResult.isFailure) {
            val error = parseResult.exceptionOrNull()
            Log.e(TAG, "JSON parse error: ${error?.message}")
            events.add(EngineEvent.Error("Erreur de format : la réponse doit être un JSON valide. Veuillez corriger."))
            state = EngineState.Idle
            return events
        }

        val response = parseResult.getOrThrow()
        events.addAll(processResponse(response))
        return events
    }

    private suspend fun processResponse(response: OrchestratorResponse): List<EngineEvent> {
        val events = mutableListOf<EngineEvent>()

        if (response.final_answer != null) {
            conversationHistory.addAssistantMessage(response.final_answer)
            events.add(EngineEvent.TextResponse(response.final_answer))
            consecutiveActions = 0
            state = EngineState.Idle
            return events
        }

        if (response.tool_calls.isNotEmpty()) {
            consecutiveActions++
            val toolCall = response.tool_calls.first()
            val remainingCalls = response.tool_calls.drop(1)

            val tool = tools[toolCall.name]
            if (tool == null) {
                events.add(EngineEvent.Error("Outil inconnu : ${toolCall.name}"))
                state = EngineState.Idle
                return events
            }

            if (toolCall.name == "ask_user_confirmation") {
                state = EngineState.AwaitingConfirmation(
                    toolCall, remainingCalls, null
                )
                val message = toolCall.parameters["message"] as? String ?: "Confirmez-vous cette action ?"
                events.add(EngineEvent.ConfirmationRequested(message))
                return events
            }

            if (toolCall.name == "ask_followup_question") {
                val question = toolCall.parameters["question"] as? String ?: "Pouvez-vous préciser ?"
                state = EngineState.AwaitingFollowUp(question, remainingCalls, null)
                events.add(EngineEvent.FollowUpRequested(question))
                return events
            }

            val result = tool.execute(context, toolCall.parameters)
            events.add(EngineEvent.ToolExecuted(toolCall.name, result))

            conversationHistory.addToolResult(toolCall.name, result.toString())

            when (result) {
                is ToolResult.Success,
                is ToolResult.SuccessWithData -> {
                    if (consecutiveActions >= MAX_CONSECUTIVE_ACTIONS || remainingCalls.isEmpty()) {
                        consecutiveActions = 0
                        state = EngineState.Idle
                    } else {
                        val remainingResponse = OrchestratorResponse(
                            thinking = "Continuing after successful ${toolCall.name}",
                            tool_calls = remainingCalls,
                            final_answer = null
                        )
                        events.addAll(processResponse(remainingResponse))
                    }
                }
                is ToolResult.Error -> {
                    consecutiveActions = 0
                    events.add(EngineEvent.Error(result.message))
                    state = EngineState.Idle
                }
                else -> {
                    state = EngineState.Idle
                }
            }
        }

        return events
    }

    fun confirmAction(): List<EngineEvent> {
        val currentState = state
        if (currentState !is EngineState.AwaitingConfirmation) {
            return listOf(EngineEvent.Error("Aucune action en attente de confirmation"))
        }

        state = EngineState.Idle
        consecutiveActions = 0
        return listOf(EngineEvent.ToolExecuted(
            currentState.toolCall.name,
            ToolResult.Success
        ))
    }

    fun cancelAction() {
        state = EngineState.Idle
        consecutiveActions = 0
    }

    fun provideFollowUpAnswer(answer: String): List<EngineEvent> {
        val currentState = state
        if (currentState !is EngineState.AwaitingFollowUp) {
            return listOf(EngineEvent.Error("Aucune question en attente"))
        }

        state = EngineState.Idle
        conversationHistory.addUserMessage(answer)
        return listOf(EngineEvent.TextResponse("Réponse enregistrée. Continuez votre requête."))
    }

    fun reset() {
        conversationHistory.clear()
        state = EngineState.Idle
        consecutiveActions = 0
    }

    private fun buildPrompt(): String {
        val systemPrompt = systemPromptBuilder.build(
            com.raissummit.orchestrator.data.models.ToolSchema.allTools
        )
        val history = conversationHistory.getHistory()
        val historyText = history.joinToString("\n") { turn ->
            "[${turn.role}]: ${turn.content}"
        }
        return "$systemPrompt\n\n# HISTORIQUE\n$historyText\n\n[assistant]:"
    }
}
