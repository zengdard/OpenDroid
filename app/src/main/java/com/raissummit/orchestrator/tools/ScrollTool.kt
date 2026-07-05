package com.raissummit.orchestrator.tools

import android.content.Context

class ScrollTool : Tool(
    name = "scroll",
    description = "Défiler l'écran vers le bas ou le haut"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        val direction = (parameters["direction"] as? String)?.lowercase() ?: "down"
        val amount = (parameters["amount"] as? Number)?.toInt() ?: 300
        return ToolResult.Error("Le défilement nécessite le service d'accessibilité. Activez-le dans Paramètres.")
    }
}
