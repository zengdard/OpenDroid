package com.opendroid.provider

import com.opendroid.data.model.ProviderKind
import com.opendroid.data.model.Role
import com.opendroid.data.model.ToolCallPart
import com.opendroid.provider.local.LocalInference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Provider on-device (Gemma via LiteRT-LM).
 * Pas de tool-calling natif : on utilise le protocole XML (voir [XmlToolParser]).
 */
class OnDeviceProvider(
    override val id: String = "on-device",
    private val modelPath: String
) : LlmProvider {
    override val kind: ProviderKind = ProviderKind.ON_DEVICE
    override val supportsNativeTools: Boolean = false

    override fun stream(request: ChatRequest): Flow<ProviderEvent> = flow {
        if (modelPath.isBlank()) {
            emit(ProviderEvent.Error("Aucun modèle on-device configuré. Téléchargez-le dans les paramètres."))
            return@flow
        }
        if (!LocalInference.isLoaded) {
            LocalInference.loadModel(modelPath)
        }
        val prompt = buildPrompt(request)
        val acc = StringBuilder()
        LocalInference.generate(prompt, modelPath).collect { token ->
            if (token == "__NO_MODEL__") {
                emit(ProviderEvent.Error("Modèle on-device non chargé."))
                return@collect
            }
            acc.append(token)
            // Émet les deltas en tentant d'extraire proprement thinking/answer.
            val thinking = XmlToolParser.extractThinking(acc.toString())
            val answer = XmlToolParser.extractAnswer(acc.toString())
            if (!answer.isNullOrBlank()) emit(ProviderEvent.TextDelta(token))
            else if (!thinking.isNullOrBlank() && XmlToolParser.extractAnswer(acc.toString()) == null) {
                emit(ProviderEvent.ThinkingDelta(token))
            }
        }
        val raw = acc.toString()
        val parsed = XmlToolParser.parse(raw)
        val text = parsed.answer ?: if (parsed.toolCalls.isEmpty() && parsed.thinking == null) raw.trim() else ""
        emit(
            ProviderEvent.Completed(
                text = text,
                thinking = parsed.thinking,
                toolCalls = parsed.toolCalls,
                finishReason = if (parsed.toolCalls.isEmpty()) "stop" else "tool_calls"
            )
        )
    }

    private fun buildPrompt(request: ChatRequest): String {
        val sb = StringBuilder()
        if (request.system.isNotBlank()) sb.append(request.system).append("\n\n")
        if (request.tools.isNotEmpty()) {
            sb.append("# OUTILS DISPONIBLES\n")
            sb.append("Pour appeler un outil, réponds EXACTEMENT avec :\n")
            sb.append("<tool_call name=\"nom_outil\" parametre=\"valeur\"></tool_call>\n")
            sb.append("Pour répondre à l'utilisateur : <answer>ta réponse</answer>\n")
            sb.append("Réfléchis d'abord dans <thinking>…</thinking>.\n\n")
            request.tools.forEach { t ->
                val params = t.parameters.joinToString(", ") { p ->
                    "${p.name}:${p.type}${if (p.required) "" else "?"}"
                }
                sb.append("- ${t.id}($params) — ${t.description}\n")
            }
            sb.append('\n')
        }
        sb.append("# CONVERSATION\n")
        request.messages.forEach { m ->
            when (m.role) {
                Role.USER -> {
                    sb.append("<user>\n${m.text}\n</user>\n")
                    if (m.attachments.isNotEmpty()) {
                        sb.append("<note>${m.attachments.size} capture(s) jointe(s), non analysable(s) en local</note>\n")
                    }
                }
                Role.ASSISTANT -> {
                    sb.append("<assistant>\n")
                    m.thinking?.let { sb.append("<thinking>$it</thinking>\n") }
                    m.toolCalls.forEach { tc -> sb.append("<tool_call name=\"${tc.name}\"></tool_call>\n") }
                    if (m.text.isNotBlank()) sb.append("<answer>${m.text}</answer>\n")
                    sb.append("</assistant>\n")
                }
                Role.TOOL -> sb.append("<tool_result name=\"${m.toolName}\">\n${m.text}\n</tool_result>\n")
                Role.SYSTEM -> {}
            }
        }
        sb.append("\n<assistant>")
        return sb.toString()
    }
}
