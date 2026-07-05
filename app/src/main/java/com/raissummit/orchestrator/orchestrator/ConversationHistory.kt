package com.raissummit.orchestrator.orchestrator

data class ConversationTurn(
    val role: String,
    val content: String
)

class ConversationHistory(
    private val maxTurns: Int = 10
) {
    private val turns = mutableListOf<ConversationTurn>()

    fun addUserMessage(message: String) {
        turns.add(ConversationTurn("user", message))
        trim()
    }

    fun addAssistantMessage(message: String) {
        turns.add(ConversationTurn("assistant", message))
        trim()
    }

    fun addToolResult(toolName: String, result: String) {
        turns.add(ConversationTurn("tool", "$toolName: $result"))
        trim()
    }

    fun getHistory(): List<ConversationTurn> = turns.toList()

    fun clear() {
        turns.clear()
    }

    private fun trim() {
        while (turns.size > maxTurns) {
            turns.removeFirst()
        }
    }
}
