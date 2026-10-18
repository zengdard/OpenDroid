package com.opendroid.agent

import com.opendroid.data.model.Message

/** Événements émis par la boucle d'agent vers l'UI. */
sealed interface AgentEvent {
    data class TextDelta(val text: String) : AgentEvent
    data class ThinkingDelta(val text: String) : AgentEvent
    /** Un message assistant complet a été committé (texte + appels d'outils). */
    data class AssistantCommitted(val message: Message) : AgentEvent
    /** Un outil démarre. */
    data class ToolStarted(val callId: String, val name: String, val preview: String) : AgentEvent
    /** Un outil a fini. */
    data class ToolFinished(val message: Message) : AgentEvent
    /** Un outil a été refusé (permission). */
    data class ToolDenied(val name: String) : AgentEvent
    /** La liste de tâches a changé. */
    data class TodosUpdated(val todos: List<com.opendroid.data.model.Todo>) : AgentEvent
    data class Error(val message: String) : AgentEvent
    data object Completed : AgentEvent
}
