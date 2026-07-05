package com.raissummit.orchestrator.tools

import android.content.Context
import android.content.Intent
import android.net.Uri

class SearchWebTool : Tool(
    name = "search_web",
    description = "Effectuer une recherche web"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        val query = parameters["query"] as? String ?: return ToolResult.Error("Requête manquante")
        return try {
            val encodedQuery = Uri.encode(query)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encodedQuery"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult.Success
        } catch (e: Exception) {
            ToolResult.Error("Erreur de recherche : ${e.message}")
        }
    }
}
