package com.opendroid.data.model

import java.util.UUID

enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

/**
 * Un appel d'outil demandé par le modèle.
 * [arguments] est une chaîne JSON brute (format canonique interne).
 */
data class ToolCallPart(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val arguments: String = "{}"
)

/**
 * Message canonique, indépendant du provider.
 * Les providers convertissent ce format vers leur wire-format respectif.
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String = "",
    val thinking: String? = null,
    val toolCalls: List<ToolCallPart> = emptyList(),
    /** Pour un message TOOL : id de l'appel auquel il répond. */
    val toolCallId: String? = null,
    val toolName: String? = null,
    /** Marque un résultat d'outil en erreur (affichage UI). */
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun user(text: String) = Message(role = Role.USER, text = text)
        fun assistant(
            text: String,
            thinking: String? = null,
            toolCalls: List<ToolCallPart> = emptyList()
        ) = Message(role = Role.ASSISTANT, text = text, thinking = thinking, toolCalls = toolCalls)

        fun tool(callId: String, name: String, result: String, isError: Boolean = false) =
            Message(role = Role.TOOL, text = result, toolCallId = callId, toolName = name, isError = isError)
    }
}
