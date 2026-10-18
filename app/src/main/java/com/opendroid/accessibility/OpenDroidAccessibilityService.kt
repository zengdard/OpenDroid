package com.opendroid.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Service d'accessibilité d'OpenDroid : permet à l'agent d'observer et d'agir
 * sur l'interface (gestes, saisie, navigation, capture).
 */
class OpenDroidAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        bridge.service = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (bridge.service === this) bridge.service = null
    }

    companion object {
        /** Pont partagé, injecté/appelé par les outils d'interface. */
        val bridge = AccessibilityBridge()

        fun isConnected(): Boolean = bridge.isReady
    }
}
