package com.opendroid.agent

import android.content.Context
import android.util.Log
import com.opendroid.accessibility.AccessibilityBridge
import com.opendroid.data.model.AgentConfig
import com.opendroid.data.model.Attachment
import com.opendroid.data.model.Message
import com.opendroid.data.model.PermissionConfig
import com.opendroid.data.model.PermissionLevel
import com.opendroid.data.model.Role
import com.opendroid.data.model.Todo
import com.opendroid.data.model.ToolCallPart
import com.opendroid.data.registry.BuiltInAgents
import com.opendroid.provider.ChatRequest
import com.opendroid.provider.LlmProvider
import com.opendroid.provider.ProviderEvent
import com.opendroid.tool.Tool
import com.opendroid.tool.ToolContext
import com.opendroid.tool.ToolRegistry
import com.opendroid.tool.ToolResult
import com.opendroid.tool.UserQuestion
import org.json.JSONObject

/** Demande de permission remontée à l'UI. */
data class PermissionRequest(
    val toolId: String,
    val toolName: String,
    val input: String?,
    val level: PermissionLevel,
    val argumentsJson: String
)

/** Callbacks fournis par le ViewModel au moteur. */
class EngineCallbacks(
    val appContext: Context,
    val accessibility: AccessibilityBridge,
    val onEvent: (AgentEvent) -> Unit,
    val requestPermission: suspend (PermissionRequest) -> Boolean,
    val askUser: suspend (UserQuestion) -> String,
    val updateTodos: suspend (List<Todo>) -> Unit,
    val readTodos: suspend () -> List<Todo>
)

/**
 * Boucle d'agent façon opencode : LLM → outils → LLM, avec permissions,
 * sous-agents (outil `task`) et suivi de tâches.
 */
class AgentRunner(
    private val provider: LlmProvider,
    private val globalPermission: PermissionConfig,
    private val autoApprove: Boolean,
    private val callbacks: EngineCallbacks,
    private val tools: Map<String, Tool> = ToolRegistry.byId,
    private val agentLookup: (String) -> AgentConfig = { BuiltInAgents.byId(it) },
    private val silent: Boolean = false,
    /** ID de session/conversation (transmis aux backends qui l'exigent, ex. OpenCode Go). */
    private val sessionId: String = ""
) {
    companion object {
        private const val TAG = "AgentRunner"
        const val MAX_DEPTH = 2
    }

    private fun emit(event: AgentEvent) {
        if (!silent) callbacks.onEvent(event)
    }

    /**
     * Exécute l'agent sur [initial] (historique) et renvoie la liste des nouveaux
     * messages produits (assistant + résultats d'outils).
     */
    suspend fun run(agent: AgentConfig, initial: List<Message>, model: String, depth: Int = 0): List<Message> {
        val produced = mutableListOf<Message>()
        val history = initial.toMutableList()
        val maxSteps = agent.steps ?: 40
        var step = 0

        while (step < maxSteps) {
            step++

            // Compression d'historique si le contexte devient trop gros.
            val compressed = HistoryCompressor.maybeCompress(provider, model, history, sessionId)
            if (compressed !== history && compressed.size != history.size) {
                val before = history.size
                history.clear()
                history.addAll(compressed)
                emit(AgentEvent.ContextCompressed(before, compressed.size))
            }

            // Correspondance stricte assistant(tool_calls) ↔ tool : nettoie les orphelins.
            val sanitized = HistorySanitizer.sanitize(history)
            if (sanitized.size != history.size) {
                history.clear()
                history.addAll(sanitized)
            }

            val specs = tools.values
                .filter { agent.exposesTool(it.spec.id, globalPermission) }
                .map { it.spec }

            val system = AgentPrompt.system(agent, includeToolCatalog = !provider.supportsNativeTools, tools = specs)
            val request = ChatRequest(
                model = model,
                system = system,
                messages = history,
                tools = specs,
                temperature = agent.temperature ?: 0.3f,
                sessionId = sessionId
            )

            var streamedText = StringBuilder()
            var streamedThinking = StringBuilder()
            var completed: ProviderEvent.Completed? = null
            var failed: String? = null

            provider.stream(request).collect { event ->
                when (event) {
                    is ProviderEvent.TextDelta -> {
                        streamedText.append(event.text)
                        emit(AgentEvent.TextDelta(event.text))
                    }
                    is ProviderEvent.ThinkingDelta -> {
                        streamedThinking.append(event.text)
                        emit(AgentEvent.ThinkingDelta(event.text))
                    }
                    is ProviderEvent.Completed -> completed = event
                    is ProviderEvent.Error -> failed = event.message
                }
            }

            if (failed != null) {
                emit(AgentEvent.Error(failed!!))
                break
            }
            val done = completed ?: ProviderEvent.Completed("", null, emptyList(), "stop")

            val text = done.text.ifBlank { streamedText.toString() }
            val thinking = done.thinking ?: streamedThinking.toString().ifBlank { null }
            val assistant = Message.assistant(text = text, thinking = thinking, toolCalls = done.toolCalls)
            history.add(assistant)
            produced.add(assistant)
            emit(AgentEvent.AssistantCommitted(assistant))

            if (done.toolCalls.isEmpty()) break

            for (call in done.toolCalls) {
                val toolMessage = executeTool(agent, call, depth, model)
                history.add(toolMessage)
                produced.add(toolMessage)
                emit(AgentEvent.ToolFinished(toolMessage))
            }
        }

        return produced
    }

    private suspend fun executeTool(agent: AgentConfig, call: ToolCallPart, depth: Int, model: String): Message {
        val tool = tools[call.name]
            ?: return Message.tool(call.id, call.name, "Outil inconnu : ${call.name}", isError = true)

        val args = try {
            if (call.arguments.isBlank()) JSONObject() else JSONObject(call.arguments)
        } catch (e: Exception) {
            return Message.tool(call.id, call.name, "Arguments JSON invalides : ${call.arguments.take(200)}", isError = true)
        }

        val input = tool.permissionInput(args)
        val level = resolvePermission(agent, tool, input)

        if (level == PermissionLevel.DENY) {
            emit(AgentEvent.ToolDenied(call.name))
            return Message.tool(call.id, call.name, "Action refusée par la politique de permissions.", isError = true)
        }

        if (level == PermissionLevel.ASK && !autoApprove) {
            emit(AgentEvent.ToolStarted(call.id, call.name, input ?: args.toString().take(80)))
            val granted = callbacks.requestPermission(
                PermissionRequest(tool.spec.id, tool.spec.id, input, level, args.toString())
            )
            if (!granted) {
                emit(AgentEvent.ToolDenied(call.name))
                return Message.tool(call.id, call.name, "L'utilisateur a refusé cette action.", isError = true)
            }
        } else {
            emit(AgentEvent.ToolStarted(call.id, call.name, input ?: args.toString().take(80)))
        }

        val context = buildContext(depth, model)
        val result: ToolResult = try {
            tool.execute(context, args)
        } catch (e: Exception) {
            Log.e(TAG, "tool ${call.name} failed", e)
            ToolResult.error("Erreur d'exécution : ${e.message}")
        }
        val attachmentPath = result.data["attachment_path"] as? String
        val attachments = attachmentPath?.let { listOf(Attachment(it)) } ?: emptyList()
        return Message.tool(call.id, call.name, result.output, isError = result.isError, attachments = attachments)
    }

    private fun buildContext(depth: Int, model: String): ToolContext = ToolContext(
        appContext = callbacks.appContext,
        accessibility = callbacks.accessibility,
        askUser = { q -> callbacks.askUser(q) },
        runSubagent = { agentId, prompt -> runSubagent(agentId, prompt, depth, model) },
        updateTodos = { todos ->
            callbacks.updateTodos(todos)
            emit(AgentEvent.TodosUpdated(todos))
        },
        readTodos = { callbacks.readTodos() }
    )

    private suspend fun runSubagent(agentId: String, prompt: String, depth: Int, parentModel: String): String {
        if (depth >= MAX_DEPTH) return "Profondeur maximale de sous-agents atteinte."
        val sub = agentLookup(agentId)
        val subRunner = AgentRunner(
            provider = provider,
            globalPermission = globalPermission,
            autoApprove = autoApprove,
            callbacks = callbacks,
            tools = tools,
            agentLookup = agentLookup,
            silent = true,
            sessionId = sessionId
        )
        val model = sub.model.ifBlank { parentModel }
        val produced = subRunner.run(sub, listOf(Message.user(prompt)), model, depth + 1)
        return produced.lastOrNull { it.role == Role.ASSISTANT && it.text.isNotBlank() }?.text
            ?: "(le sous-agent n'a pas produit de réponse texte)"
    }

    private fun resolvePermission(agent: AgentConfig, tool: Tool, input: String?): PermissionLevel {
        agent.permission.resolve(tool.permissionKey, input)?.let { return it }
        globalPermission.resolve(tool.permissionKey, input)?.let { return it }
        return PermissionConfig.defaultFor(tool.permissionKey)
    }
}
