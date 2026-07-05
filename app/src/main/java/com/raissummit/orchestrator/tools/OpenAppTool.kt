package com.raissummit.orchestrator.tools

import android.content.Context
import android.content.Intent

class OpenAppTool : Tool(
    name = "open_app",
    description = "Ouvrir une application"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        val packageName = parameters["app_package_name"] as? String
            ?: return ToolResult.Error("Package name manquant")
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ToolResult.Success
            } else {
                ToolResult.Error("Application non trouvée : $packageName")
            }
        } catch (e: Exception) {
            ToolResult.Error("Erreur d'ouverture : ${e.message}")
        }
    }
}
