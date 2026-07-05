package com.raissummit.orchestrator.ui.chat

sealed class ChatMessage {
    data class User(val text: String, val timestamp: Long = System.currentTimeMillis()) : ChatMessage()
    data class Assistant(val text: String, val thinking: String? = null, val timestamp: Long = System.currentTimeMillis()) : ChatMessage()
    data class Error(val text: String, val timestamp: Long = System.currentTimeMillis()) : ChatMessage()
    data class ToolResult(val toolName: String, val result: String, val timestamp: Long = System.currentTimeMillis()) : ChatMessage()
    data class Streaming(val text: String, val timestamp: Long = System.currentTimeMillis()) : ChatMessage()
}
