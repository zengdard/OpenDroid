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
/**
 * Un nœud de l'écran capturé, avec un index stable utilisé par l'outil `tap_id`.
 * L'index reste valable tant que l'écran n'a pas changé ; `tap_id` vérifie
 * que le nœud est toujours présent avant d'agir (garde-fou pré-action).
 */
data class UiNodeInfo(
    val index: Int,
    val depth: Int,
    val className: String,
    val text: String,
    val desc: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val clickable: Boolean,
    val editable: Boolean,
    val packageName: String
) {
    val cx: Int get() = (left + right) / 2
    val cy: Int get() = (top + bottom) / 2
}

/** Relevé de l'écran au moment de `screen_dump`. */
data class UiSnapshot(
    val packageName: String,
    val createdAt: Long,
    val nodes: List<UiNodeInfo>
)

class AccessibilityBridge {

    /** Dernier relevé produit par `screen_dump`, utilisé pour résoudre les `#index`. */
    @Volatile
    var lastSnapshot: UiSnapshot? = null
        private set

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

    /** Capture l'arbre visible et lui attribue des indices stables (#0, #1, …). */
    fun captureSnapshot(maxNodes: Int = 140): UiSnapshot? {
        val root = rootNode() ?: return null
        val pkg = root.packageName?.toString() ?: ""
        val nodes = mutableListOf<UiNodeInfo>()
        collectNodes(root, 0, nodes, maxNodes, pkg)
        val snapshot = UiSnapshot(pkg, System.currentTimeMillis(), nodes)
        lastSnapshot = snapshot
        return snapshot
    }

    private fun collectNodes(
        node: AccessibilityNodeInfo,
        depth: Int,
        out: MutableList<UiNodeInfo>,
        max: Int,
        pkg: String
    ) {
        if (out.size >= max) return
        val text = node.text?.toString()?.take(120).orEmpty()
        val desc = node.contentDescription?.toString()?.take(120).orEmpty()
        val cls = node.className?.toString()?.substringAfterLast('.') ?: ""
        if (text.isNotBlank() || desc.isNotBlank() || node.isClickable || node.isEditable) {
            val rect = Rect().also { node.getBoundsInScreen(it) }
            out.add(
                UiNodeInfo(
                    index = out.size,
                    depth = depth,
                    className = cls,
                    text = text,
                    desc = desc,
                    left = rect.left, top = rect.top, right = rect.right, bottom = rect.bottom,
                    clickable = node.isClickable,
                    editable = node.isEditable,
                    packageName = pkg
                )
            )
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodes(child, depth + 1, out, max, pkg)
            child.recycle()
        }
    }

    /** Sérialise l'arbre visible en texte, avec index et coordonnées, pour le modèle. */
    fun dumpScreen(maxNodes: Int = 140): String {
        val snapshot = captureSnapshot(maxNodes) ?: return "(aucune fenêtre active)"
        if (snapshot.nodes.isEmpty()) return "(écran vide)"
        val sb = StringBuilder()
        snapshot.nodes.forEach { n ->
            sb.append("  ".repeat(n.depth))
            sb.append('#').append(n.index).append(' ').append(n.className.ifBlank { "node" })
            val label = n.text.ifBlank { n.desc }
            if (label.isNotBlank()) sb.append(" \"").append(label).append('"')
            sb.append(" @(").append(n.cx).append(',').append(n.cy).append(')')
            if (n.clickable) sb.append(" [clickable]")
            if (n.editable) sb.append(" [editable]")
            sb.append('\n')
        }
        return sb.toString()
    }

    /**
     * Tape sur l'élément `#index` du dernier relevé, APRÈS vérification :
     *  - le paquet actif n'a pas changé ;
     *  - un nœud vivant existe toujours au même endroit avec le même texte/classe.
     * Renvoie "ok" ou un message d'erreur explicite pour le modèle.
     */
    fun tapIndex(index: Int): String {
        val snapshot = lastSnapshot
            ?: return "Aucun relevé d'écran. Appelez d'abord `screen_dump`."
        val target = snapshot.nodes.firstOrNull { it.index == index }
            ?: return "Index #$index inconnu. Refaites `screen_dump`."
        if (System.currentTimeMillis() - snapshot.createdAt > 60_000) {
            return "Relevé trop ancien (>60 s). Refaites `screen_dump`."
        }
        val currentPkg = packageName()
        if (currentPkg != null && snapshot.packageName.isNotBlank() && currentPkg != snapshot.packageName) {
            return "L'écran a changé (paquet actif: $currentPkg ≠ ${snapshot.packageName}). Refaites `screen_dump`."
        }
        val center = findLiveCenter(target)
            ?: return "Élément #$index introuvable à l'écran (interface modifiée). Refaites `screen_dump`."
        return if (tap(center.first.toFloat(), center.second.toFloat())) "ok"
        else "Tap échoué sur #$index."
    }

    /** Vérifie qu'un élément correspondant est toujours à l'écran. */
    fun isIndexPresent(index: Int): Boolean {
        val target = lastSnapshot?.nodes?.firstOrNull { it.index == index } ?: return false
        return findLiveCenter(target) != null
    }

    private fun findLiveCenter(target: UiNodeInfo): Pair<Int, Int>? {
        val root = rootNode() ?: return null
        return findLiveNode(root, target)
    }

    private fun findLiveNode(node: AccessibilityNodeInfo, target: UiNodeInfo): Pair<Int, Int>? {
        val rect = Rect().also { node.getBoundsInScreen(it) }
        val cx = (rect.left + rect.right) / 2
        val cy = (rect.top + rect.bottom) / 2
        val cls = node.className?.toString()?.substringAfterLast('.') ?: ""
        val text = node.text?.toString()?.take(120).orEmpty()
        val desc = node.contentDescription?.toString()?.take(120).orEmpty()
        val sameBounds = kotlin.math.abs(cx - target.cx) <= 12 && kotlin.math.abs(cy - target.cy) <= 12
        val sameLabel = when {
            target.text.isNotBlank() -> text == target.text
            target.desc.isNotBlank() -> desc == target.desc
            else -> true
        }
        val sameClass = target.className.isBlank() || cls == target.className
        if (sameBounds && sameLabel && sameClass) return cx to cy
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findLiveNode(child, target)
            child.recycle()
            if (found != null) return found
        }
        return null
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
