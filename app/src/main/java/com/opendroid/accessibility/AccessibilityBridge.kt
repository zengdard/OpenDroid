package com.opendroid.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import android.util.Log

/**
 * Pont vers le service d'accessibilité : gestes, lecture de l'arbre de vues,
 * actions système (retour, accueil, récents), recherche par texte.
 */
class AccessibilityBridge {

    @Volatile
    var service: OpenDroidAccessibilityService? = null

    val isReady: Boolean get() = service != null

    fun rootNode(): AccessibilityNodeInfo? = service?.rootInActiveWindow
    fun packageName(): String? = service?.rootInActiveWindow?.packageName?.toString()

    // ---- Gestes ----
    fun tap(x: Float, y: Float): Boolean {
        val svc = service ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x, y); lineTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build()
        return svc.dispatchGesture(gesture, null, null)
    }

    fun longPress(x: Float, y: Float, durationMs: Long = 600): Boolean {
        val svc = service ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x, y); lineTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs)).build()
        return svc.dispatchGesture(gesture, null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300): Boolean {
        val svc = service ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs)).build()
        return svc.dispatchGesture(gesture, null, null)
    }

    fun scrollDown(): Boolean = gestureSwipe(0.5f, 0.7f, 0.5f, 0.3f)
    fun scrollUp(): Boolean = gestureSwipe(0.5f, 0.3f, 0.5f, 0.7f)

    private fun gestureSwipe(fx: Float, fy: Float, tx: Float, ty: Float): Boolean {
        val svc = service ?: return false
        val dm = svc.resources.displayMetrics
        return swipe(fx * dm.widthPixels, fy * dm.heightPixels, tx * dm.widthPixels, ty * dm.heightPixels)
    }

    // ---- Actions globales ----
    fun pressBack(): Boolean = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false
    fun pressHome(): Boolean = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false
    fun pressRecents(): Boolean = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false
    fun pressNotifications(): Boolean = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) ?: false

    fun setText(text: String): Boolean {
        val root = rootNode() ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focused.recycle()
        return ok
    }

    // ---- Arbre de vues ----
    /** Sérialise l'arbre visible en texte, avec coordonnées, pour le modèle. */
    fun dumpScreen(maxNodes: Int = 120): String {
        val root = rootNode() ?: return "(aucune fenêtre active)"
        val sb = StringBuilder()
        val count = intArrayOf(0)
        walk(root, sb, 0, count, maxNodes)
        return if (sb.isEmpty()) "(écran vide)" else sb.toString()
    }

    private fun walk(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int, count: IntArray, max: Int) {
        if (count[0] >= max) return
        count[0]++
        val label = node.text?.toString()?.take(80)
            ?: node.contentDescription?.toString()?.take(80)
            ?: ""
        val cls = node.className?.toString()?.substringAfterLast('.') ?: ""
        if (label.isNotBlank() || node.isClickable) {
            val rect = Rect().also { node.getBoundsInScreen(it) }
            val cx = (rect.left + rect.right) / 2
            val cy = (rect.top + rect.bottom) / 2
            sb.append("  ".repeat(depth))
            sb.append("- ").append(cls.ifBlank { "node" })
            if (label.isNotBlank()) sb.append(" \"").append(label).append("\"")
            sb.append(" @(").append(cx).append(",").append(cy).append(")")
            if (node.isClickable) sb.append(" [clickable]")
            if (node.isEditable) sb.append(" [editable]")
            sb.append('\n')
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, sb, depth + 1, count, max)
            child.recycle()
        }
    }

    /** Trouve un nœud cliquable par texte (ou contenu). Renvoie le centre, ou null. */
    fun findNodeCenter(query: String): Pair<Int, Int>? {
        val root = rootNode() ?: return null
        val found = findNode(root, query) ?: return null
        val rect = Rect().also { found.getBoundsInScreen(it) }
        found.recycle()
        return (rect.left + rect.right) / 2 to (rect.top + rect.bottom) / 2
    }

    private fun findNode(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val q = query.lowercase()
        val text = node.text?.toString()?.lowercase()
        val desc = node.contentDescription?.toString()?.lowercase()
        val match = (text?.contains(q) == true) || (desc?.contains(q) == true)
        if (match && (node.isClickable || node.isFocusable || node.isEnabled)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val r = findNode(child, query)
            child.recycle()
            if (r != null) return r
        }
        return null
    }

    fun findAndTap(query: String): Boolean {
        val center = findNodeCenter(query) ?: return false
        return tap(center.first.toFloat(), center.second.toFloat())
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun takeScreenshot(): android.graphics.Bitmap? {
        val svc = service ?: return null
        var bitmap: android.graphics.Bitmap? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        try {
            svc.takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                svc.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        bitmap = android.graphics.Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer, screenshot.colorSpace
                        )
                        screenshot.hardwareBuffer.close()
                        latch.countDown()
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w("AccessibilityBridge", "screenshot failed: $errorCode")
                        latch.countDown()
                    }
                }
            )
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w("AccessibilityBridge", "screenshot error", e)
        }
        return bitmap
    }
}
