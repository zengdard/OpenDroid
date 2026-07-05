package com.raissummit.orchestrator.tools

import android.content.Context
import android.content.Intent

class ShareTextTool : Tool(
    name = "share_text",
    description = "Partager du texte via une autre application (SMS, email, réseaux sociaux)"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        val text = parameters["text"] as? String ?: return ToolResult.Error("Texte à partager manquant")
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Partager"))
            ToolResult.Success
        } catch (e: Exception) {
            ToolResult.Error("Erreur partage : ${e.message}")
        }
    }
}
