package com.raissummit.orchestrator.tools

import android.content.Context

sealed class ToolResult {
    data object Success : ToolResult()
    data class SuccessWithData(val data: Map<String, Any?>) : ToolResult()
    data class Error(val message: String) : ToolResult()
    data class AwaitingConfirmation(val message: String) : ToolResult()
    data class AwaitingFollowUp(val question: String) : ToolResult()
}

abstract class Tool(val name: String, val description: String) {
    abstract suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult
}
