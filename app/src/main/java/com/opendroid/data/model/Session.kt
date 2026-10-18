package com.opendroid.data.model

import java.util.UUID

/**
 * Une session de conversation, à la manière d'opencode.
 * Persistée en JSON sur disque et reprise à volonté.
 */
data class Session(
    val id: String = UUID.randomUUID().toString().take(8),
    val title: String = "Nouvelle session",
    val agentId: String = "build",
    val providerId: String = "",
    val modelId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<Message> = emptyList(),
    val todos: List<Todo> = emptyList()
) {
    val lastUserMessage: String?
        get() = messages.lastOrNull { it.role == Role.USER }?.text
}

data class Todo(
    val content: String,
    val status: TodoStatus = TodoStatus.PENDING,
    /** Checkpoint de vérification (ex. texte attendu à l'écran). */
    val verify: String? = null
)

enum class TodoStatus { PENDING, IN_PROGRESS, DONE }
