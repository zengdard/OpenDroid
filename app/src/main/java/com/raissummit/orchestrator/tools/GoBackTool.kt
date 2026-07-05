package com.raissummit.orchestrator.tools

import android.content.Context

class GoBackTool : Tool(
    name = "go_back",
    description = "Simuler le bouton retour système"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        return ToolResult.Error("Le retour système nécessite le service d'accessibilité. Activez-le dans Paramètres.")
    }
}
