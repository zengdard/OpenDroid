package com.raissummit.orchestrator.tools

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

class TapTool : Tool(
    name = "tap",
    description = "Appuyer à une position sur l'écran"
) {
    companion object {
        var accessibilityService: android.accessibilityservice.AccessibilityService? = null
    }

    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        val x = (parameters["x"] as? Number)?.toFloat()
            ?: return ToolResult.Error("Coordonnée X manquante")
        val y = (parameters["y"] as? Number)?.toFloat()
            ?: return ToolResult.Error("Coordonnée Y manquante")

        val service = accessibilityService
        if (service == null) {
            return ToolResult.Error("Service d'accessibilité non disponible")
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val path = Path().apply {
                    moveTo(x, y)
                    lineTo(x, y)
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                    .build()
                service.dispatchGesture(gesture, null, null)
                ToolResult.Success
            } else {
                val nodeInfo = service.rootInActiveWindow
                    ?: return ToolResult.Error("Impossible d'obtenir le nœud racine")
                val node = findNodeAtPosition(nodeInfo, x, y)
                if (node != null) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.recycle()
                    ToolResult.Success
                } else {
                    ToolResult.Error("Aucun élément trouvé à la position ($x, $y)")
                }
            }
        } catch (e: Exception) {
            ToolResult.Error("Erreur de tap : ${e.message}")
        }
    }

    private fun findNodeAtPosition(
        node: AccessibilityNodeInfo,
        x: Float,
        y: Float
    ): AccessibilityNodeInfo? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.contains(x.toInt(), y.toInt())) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = findNodeAtPosition(child, x, y)
                child.recycle()
                if (found != null) {
                    node.recycle()
                    return found
                }
            }
            return node
        }
        return null
    }
}
