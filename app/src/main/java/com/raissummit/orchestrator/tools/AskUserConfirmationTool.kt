package com.raissummit.orchestrator.tools

import android.content.Context

class AskUserConfirmationTool : Tool(
    name = "ask_user_confirmation",
    description = "Demander une confirmation à l'utilisateur"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        val message = parameters["message"] as? String ?: "Confirmez-vous cette action ?"
        return ToolResult.AwaitingConfirmation(message)
    }
}
