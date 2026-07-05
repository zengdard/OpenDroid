package com.raissummit.orchestrator.tools

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

class TypeTextTool : Tool(
    name = "type_text",
    description = "Saisir du texte"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        val text = parameters["text"] as? String ?: return ToolResult.Error("Texte manquant")

        val service = TapTool.accessibilityService
        if (service == null) {
            return ToolResult.Error("Service d'accessibilité non disponible")
        }

        return try {
            val root = service.rootInActiveWindow ?: return ToolResult.Error("Aucun nœud racine")
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                val args = Bundle()
                args.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                focused.recycle()
                root.recycle()
                ToolResult.Success
            } else {
                root.recycle()
                ToolResult.Error("Aucun champ de texte focalisé")
            }
        } catch (e: Exception) {
            ToolResult.Error("Erreur de saisie : ${e.message}")
        }
    }
}
