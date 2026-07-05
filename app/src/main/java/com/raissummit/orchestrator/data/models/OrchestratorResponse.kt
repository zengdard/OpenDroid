package com.raissummit.orchestrator.data.models

data class ToolCall(
    val name: String,
    val parameters: Map<String, Any?>
)

data class OrchestratorResponse(
    val thinking: String,
    val tool_calls: List<ToolCall>,
    val final_answer: String?
)
