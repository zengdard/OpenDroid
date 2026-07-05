package com.raissummit.orchestrator.tools

import android.content.Context
import android.content.Intent
import android.net.Uri

class MakeCallTool : Tool(
    name = "make_call",
    description = "Passer un appel téléphonique"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        val number = parameters["contact_number"] as? String ?: return ToolResult.Error("Numéro manquant")
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult.Success
        } catch (e: SecurityException) {
            ToolResult.Error("Permission CALL_PHONE non accordée")
        } catch (e: Exception) {
            ToolResult.Error("Erreur d'appel : ${e.message}")
        }
    }
}
