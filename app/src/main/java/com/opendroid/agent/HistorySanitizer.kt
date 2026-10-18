package com.opendroid.agent

import com.opendroid.data.model.Message
import com.opendroid.data.model.Role

/**
 * Assainit l'historique avant envoi au provider.
 *
 * Les APIs (OpenAI, DeepSeek, Anthropic…) exigent une correspondance stricte :
 *  - un message `tool` doit répondre à un `assistant` précédent contenant le
 *    `tool_call` de même id ;
 *  - chaque `tool_call` d'un `assistant` doit avoir sa réponse `tool`.
 *
 * La compression d'historique, une interruption ou une vieille session peuvent
 * produire des messages orphelins → erreur 400. On répare ici.
 */
object HistorySanitizer {

    fun sanitize(messages: List<Message>): List<Message> {
        val out = ArrayList<Message>(messages.size)
        var i = 0

        while (i < messages.size) {
            val message = messages[i]

            if (message.role == Role.ASSISTANT && message.toolCalls.isNotEmpty()) {
                // Résultats consécutifs qui suivent immédiatement l'assistant.
                val results = mutableListOf<Message>()
                var j = i + 1
                while (j < messages.size && messages[j].role == Role.TOOL) {
                    results.add(messages[j])
                    j++
                }
                val answered = results.mapNotNull { it.toolCallId }.toSet()
                val keptCalls = message.toolCalls.filter { it.id in answered }

                if (keptCalls.isEmpty()) {
                    // Aucun appel n'a de réponse : on conserve le texte éventuel.
                    if (message.text.isNotBlank() || !message.thinking.isNullOrBlank()) {
                        out.add(message.copy(toolCalls = emptyList()))
                    }
                } else {
                    val keptIds = keptCalls.map { it.id }.toSet()
                    out.add(message.copy(toolCalls = keptCalls))
                    results.filter { it.toolCallId in keptIds }.forEach { out.add(it) }
                }
                i = j
            } else if (message.role == Role.TOOL) {
                // `tool` sans assistant parent → on jette.
                i++
            } else {
                out.add(message)
                i++
            }
        }
        return out
    }
}
