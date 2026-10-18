package com.opendroid.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendroid.accessibility.OpenDroidAccessibilityService
import com.opendroid.agent.AgentEvent
import com.opendroid.agent.AgentRunner
import com.opendroid.agent.EngineCallbacks
import com.opendroid.agent.PermissionRequest
import com.opendroid.data.model.AgentConfig
import com.opendroid.data.model.Message
import com.opendroid.data.model.PermissionConfig
import com.opendroid.data.model.PermissionLevel
import com.opendroid.data.model.ProviderConfig
import com.opendroid.data.model.Session
import com.opendroid.data.model.Todo
import com.opendroid.data.registry.BuiltInAgents
import com.opendroid.data.session.SessionStore
import com.opendroid.data.settings.SettingsStore
import com.opendroid.provider.ProviderFactory
import com.opendroid.tool.UserQuestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Activité d'outil affichée en direct pendant l'exécution. */
data class ToolActivity(
    val callId: String,
    val name: String,
    val preview: String,
    val done: Boolean = false,
    val isError: Boolean = false
)

/** Réponse à une question posée par l'agent (`question`). */
data class PendingQuestion(
    val question: UserQuestion,
    val deferred: CompletableDeferred<String>
)

data class OpenDroidUiState(
    val sessionId: String = "",
    val title: String = "",
    val messages: List<Message> = emptyList(),
    val todos: List<Todo> = emptyList(),
    val isRunning: Boolean = false,
    val streamingText: String = "",
    val streamingThinking: String = "",
    val activities: List<ToolActivity> = emptyList(),
    val pendingPermission: PermissionRequest? = null,
    val pendingQuestion: UserQuestion? = null,
    val error: String? = null,
    val agentId: String = "build",
    val providerId: String = "on-device",
    val model: String = "",
    val autoApprove: Boolean = false
)

@HiltViewModel
class OpenDroidViewModel @Inject constructor(
    private val app: Application,
    private val settingsStore: SettingsStore,
    private val sessionStore: SessionStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(OpenDroidUiState())
    val uiState: StateFlow<OpenDroidUiState> = _uiState.asStateFlow()

    val sessions = sessionStore.sessions

    @Volatile private var permissionDeferred: CompletableDeferred<Boolean>? = null
    @Volatile private var questionDeferred: CompletableDeferred<String>? = null
    private var runJob: Job? = null

    @Volatile private var providers: List<ProviderConfig> = emptyList()
    @Volatile private var permissionConfig: PermissionConfig = PermissionConfig()
    @Volatile private var onDevicePath: String? = null
    @Volatile private var activeModel: String = ""
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
        viewModelScope.launch { sessionStore.load() }
        viewModelScope.launch {
            providers = settingsStore.providers.first()
            permissionConfig = settingsStore.permissionConfig.first()
            onDevicePath = settingsStore.onDeviceModelPath.first()
            activeModel = settingsStore.activeModel.first()
            _uiState.value = _uiState.value.copy(
                agentId = settingsStore.activeAgentId.first(),
                providerId = settingsStore.activeProviderId.first(),
                model = activeModel,
                autoApprove = settingsStore.autoApprove.first()
            )
        }
        // Collecteurs continus
        viewModelScope.launch { settingsStore.providers.collect { providers = it } }
        viewModelScope.launch { settingsStore.permissionConfig.collect { permissionConfig = it } }
        viewModelScope.launch { settingsStore.onDeviceModelPath.collect { onDevicePath = it } }
        viewModelScope.launch { settingsStore.activeModel.collect { activeModel = it } }
        viewModelScope.launch { settingsStore.autoApprove.collect { v -> _uiState.value = _uiState.value.copy(autoApprove = v) } }
        // Session initiale
        viewModelScope.launch {
            sessionStore.load()
            val id = sessionStore.activeSessionId().ifBlank { sessionStore.sessions.value.firstOrNull()?.id ?: "" }
            if (id.isNotBlank()) loadSession(id)
        }
    }

    // ---- Sessions ----

    fun loadSession(id: String) {
        runJob?.cancel()
        viewModelScope.launch {
            val session = sessionStore.get(id) ?: return@launch
            sessionStore.setActive(id)
            _uiState.value = _uiState.value.copy(
                sessionId = session.id,
                title = session.title,
                messages = session.messages,
                todos = session.todos,
                streamingText = "",
                streamingThinking = "",
                activities = emptyList(),
                agentId = session.agentId.ifBlank { _uiState.value.agentId },
                providerId = session.providerId.ifBlank { _uiState.value.providerId },
                model = session.modelId.ifBlank { _uiState.value.model },
                error = null
            )
        }
    }

    fun newSession() {
        runJob?.cancel()
        viewModelScope.launch {
            val agent = currentAgent()
            val provider = currentProvider()
            val session = sessionStore.create(agent.id, provider?.id ?: "on-device", currentModel())
            loadSession(session.id)
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            sessionStore.delete(id)
            if (id == _uiState.value.sessionId) {
                sessionStore.sessions.value.firstOrNull()?.let { loadSession(it.id) }
            }
        }
    }

    fun renameSession(id: String, title: String) {
        viewModelScope.launch { sessionStore.rename(id, title) }
    }

    // ---- Envoi / agent ----

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isRunning) return
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) {
            viewModelScope.launch {
                val session = sessionStore.create(currentAgent().id, currentProvider()?.id ?: "on-device", currentModel())
                loadSession(session.id)
                sendMessage(text)
            }
            return
        }

        runJob = viewModelScope.launch {
            val userMessage = Message.user(text.trim())
            val currentMessages = _uiState.value.messages + userMessage
            _uiState.value = _uiState.value.copy(
                messages = currentMessages,
                isRunning = true,
                streamingText = "",
                streamingThinking = "",
                activities = emptyList(),
                error = null
            )
            sessionStore.appendMessages(sessionId, listOf(userMessage))

            try {
                runEngine(sessionId, currentMessages)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Erreur inconnue")
            } finally {
                _uiState.value = _uiState.value.copy(isRunning = false, streamingText = "", streamingThinking = "")
            }
        }
    }

    private suspend fun runEngine(sessionId: String, history: List<Message>) {
        val providerConfig = currentProvider()
        if (providerConfig == null) {
            _uiState.value = _uiState.value.copy(error = "Aucun provider configuré.")
            return
        }
        if (providerConfig.kind == com.opendroid.data.model.ProviderKind.ON_DEVICE && onDevicePath.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(error = "Modèle on-device non téléchargé (voir Paramètres).")
            return
        }
        if (providerConfig.kind.isRemote && providerConfig.apiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Clé API manquante pour ${providerConfig.name} (voir Paramètres).")
            return
        }

        val provider = ProviderFactory.create(providerConfig, onDevicePath)
        val agent = currentAgent()
        val model = currentModel().ifBlank { providerConfig.models.firstOrNull() ?: "gpt-4o" }

        val callbacks = EngineCallbacks(
            appContext = app,
            accessibility = OpenDroidAccessibilityService.bridge,
            onEvent = { event -> handleEvent(sessionId, event) },
            requestPermission = { req -> awaitPermission(req) },
            askUser = { q -> awaitQuestion(q) },
            updateTodos = { todos -> sessionStore.setTodos(sessionId, todos); _uiState.value = _uiState.value.copy(todos = todos) },
            readTodos = { sessionStore.get(sessionId)?.todos ?: emptyList() }
        )

        val runner = AgentRunner(
            provider = provider,
            globalPermission = permissionConfig,
            autoApprove = _uiState.value.autoApprove,
            callbacks = callbacks,
            sessionId = sessionId
        )
        val produced = withContext(Dispatchers.Default) {
            runner.run(agent, history, model)
        }
        sessionStore.appendMessages(sessionId, produced)
    }

    private fun handleEvent(sessionId: String, event: AgentEvent) {
        when (event) {
            is AgentEvent.TextDelta ->
                _uiState.value = _uiState.value.copy(streamingText = _uiState.value.streamingText + event.text)
            is AgentEvent.ThinkingDelta ->
                _uiState.value = _uiState.value.copy(streamingThinking = _uiState.value.streamingThinking + event.text)
            is AgentEvent.AssistantCommitted ->
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + event.message,
                    streamingText = "",
                    streamingThinking = ""
                )
            is AgentEvent.ToolStarted ->
                _uiState.value = _uiState.value.copy(
                    activities = _uiState.value.activities + ToolActivity(event.callId, event.name, event.preview)
                )
            is AgentEvent.ToolFinished -> {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + event.message,
                    activities = _uiState.value.activities.map {
                        if (it.callId == event.message.toolCallId || it.name == event.message.toolName)
                            it.copy(done = true, isError = event.message.isError) else it
                    }
                )
            }
            is AgentEvent.ToolDenied ->
                _uiState.value = _uiState.value.copy(
                    activities = _uiState.value.activities.map {
                        if (it.name == event.name) it.copy(done = true, isError = true) else it
                    }
                )
            is AgentEvent.TodosUpdated ->
                _uiState.value = _uiState.value.copy(todos = event.todos)
            is AgentEvent.Error ->
                _uiState.value = _uiState.value.copy(error = event.message)
            AgentEvent.Completed -> {}
        }
    }

    private suspend fun awaitPermission(req: PermissionRequest): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        permissionDeferred = deferred
        _uiState.value = _uiState.value.copy(pendingPermission = req)
        return try {
            deferred.await()
        } finally {
            permissionDeferred = null
            _uiState.value = _uiState.value.copy(pendingPermission = null)
        }
    }

    private suspend fun awaitQuestion(q: UserQuestion): String {
        val deferred = CompletableDeferred<String>()
        questionDeferred = deferred
        _uiState.value = _uiState.value.copy(pendingQuestion = q)
        return try {
            deferred.await()
        } finally {
            questionDeferred = null
            _uiState.value = _uiState.value.copy(pendingQuestion = null)
        }
    }

    fun resolvePermission(granted: Boolean) {
        permissionDeferred?.complete(granted)
    }

    fun resolveQuestion(answer: String) {
        questionDeferred?.complete(answer)
    }

    fun stop() {
        runJob?.cancel()
        permissionDeferred?.complete(false)
        questionDeferred?.complete("(annulé)")
        _uiState.value = _uiState.value.copy(isRunning = false, pendingPermission = null, pendingQuestion = null)
    }

    // ---- Sélection agent / modèle ----

    fun setAgent(agentId: String) {
        viewModelScope.launch {
            settingsStore.setActiveAgent(agentId)
            _uiState.value = _uiState.value.copy(agentId = agentId)
            val sid = _uiState.value.sessionId
            sessionStore.get(sid)?.let { sessionStore.update(it.copy(agentId = agentId)) }
        }
    }

    fun setProvider(providerId: String) {
        viewModelScope.launch {
            settingsStore.setActiveProvider(providerId)
            _uiState.value = _uiState.value.copy(providerId = providerId)
        }
    }

    fun setModel(model: String) {
        viewModelScope.launch {
            settingsStore.setActiveModel(model)
            _uiState.value = _uiState.value.copy(model = model)
            val sid = _uiState.value.sessionId
            sessionStore.get(sid)?.let { sessionStore.update(it.copy(modelId = model)) }
        }
    }

    fun toggleAutoApprove(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAutoApprove(enabled)
            _uiState.value = _uiState.value.copy(autoApprove = enabled)
        }
    }

    fun currentAgent(): AgentConfig = BuiltInAgents.byId(_uiState.value.agentId)
    fun currentProvider(): ProviderConfig? = providers.firstOrNull { it.id == _uiState.value.providerId }
    private fun currentModel(): String = _uiState.value.model.ifBlank { activeModel }

    fun providerList(): List<ProviderConfig> = providers
    fun settings(): SettingsStore = settingsStore
    fun sessionsStore(): SessionStore = sessionStore
}
