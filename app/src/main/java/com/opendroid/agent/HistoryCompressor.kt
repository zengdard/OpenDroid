package com.opendroid.agent

import com.opendroid.data.model.Message
import com.opendroid.data.model.Role
import com.opendroid.provider.ChatRequest
import com.opendroid.provider.LlmProvider
import com.opendroid.provider.ProviderEvent

/**
 * Compression d'historique (inspirée d'ARTEMIS/opencode) : quand la conversation
 * devient trop volumineuse, les anciens tours sont résumés en un mémo compact
 * et seuls les [KEEP_RECENT] derniers messages sont conservés tels quels.
 *
 * Objectif : tenir des sessions longues, y compris avec un petit contexte
 * on-device, sans perdre l'état de la tâche.
 */
object HistoryCompressor {

    /** Budget approximatif en caractères (~4 caractères/token). */
    private const val CHAR_BUDGET = 24_000
    private const val MAX_TRANSCRIPT = 18_000
    private const val KEEP_RECENT = 8

    suspend fun maybeCompress(
        provider: LlmProvider,
        model: String,
        history: List<Message>,
        sessionId: String
    ): List<Message> {
        if (history.size <= KEEP_RECENT + 2) return history
        val total = history.sumOf { it.text.length + (it.thinking?.length ?: 0) + it.toolCalls.sumOf { tc -> tc.arguments.length } }
        if (total <= CHAR_BUDGET) return history

        val split = history.size - KEEP_RECENT
        // Ne jamais couper entre un assistant `tool_calls` et ses résultats `tool`
        // (sinon l'API refuse : "tool must be a response to a preceding tool_calls").
        var safeSplit = split
        while (safeSplit > 0 && history[safeSplit].role == Role.TOOL) safeSplit--
        if (safeSplit <= 0) return history

        val recent = history.subList(safeSplit, history.size).toList()
        val transcript = history.subList(0, safeSplit).joinToString("\n") { render(it) }.take(MAX_TRANSCRIPT)

        val request = ChatRequest(
            model = model,
            system = "Tu compresses l'historique d'un agent Android. Sois factuel, concis, sans invention.",
            messages = listOf(
                Message.user(
                    """
Résume les étapes ci-dessous en un mémo compact et exploitable :
- objectif de l'utilisateur,
- actions effectuées et résultats clés,
- état courant de l'appareil/session,
- échecs rencontrés et points restants.
Maximum 400 mots. Ne perds pas les identifiants, paquets, chemins ou valeurs importantes.

$transcript
""".trimIndent()
                )
            ),
            tools = emptyList(),
            temperature = 0.1f,
            maxTokens = 800,
            sessionId = sessionId
        )

        var summary = ""
        try {
            provider.stream(request).collect { event ->
                when (event) {
                    is ProviderEvent.TextDelta -> summary += event.text
                    is ProviderEvent.Completed -> if (summary.isBlank()) summary = event.text
                    else -> {}
                }
            }
        } catch (_: Exception) {
            return history
        }
        if (summary.isBlank()) return history

        val memo = Message(role = Role.USER, text = "[Mémo des étapes précédentes — historique compressé]\n$summary")
        return listOf(memo) + recent
    }

    private fun render(message: Message): String = when (message.role) {
        Role.USER -> "Utilisateur: ${message.text.take(600)}"
        Role.ASSISTANT -> buildString {
            if (!message.thinking.isNullOrBlank()) append("Réflexion: ${message.thinking!!.take(300)}\n")
            if (message.text.isNotBlank()) append("Assistant: ${message.text.take(600)}\n")
            message.toolCalls.forEach { append("Appel ${it.name}(${it.arguments.take(300)})\n") }
        }
        Role.TOOL -> "Résultat ${message.toolName}: ${message.text.take(500)}${if (message.isError) " [ERREUR]" else ""}"
        Role.SYSTEM -> "Système: ${message.text.take(300)}"
    }
}
