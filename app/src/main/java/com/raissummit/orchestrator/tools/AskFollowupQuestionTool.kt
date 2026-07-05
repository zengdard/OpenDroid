package com.raissummit.orchestrator.tools

import android.content.Context

class AskFollowupQuestionTool : Tool(
    name = "ask_followup_question",
    description = "Poser une question de clarification"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        val question = parameters["question"] as? String ?: "Pouvez-vous préciser ?"
        return ToolResult.AwaitingFollowUp(question)
    }
}
