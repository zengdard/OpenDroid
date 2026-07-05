package com.raissummit.orchestrator.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.raissummit.orchestrator.tools.TapTool

class OrchestratorAccessibilityService : AccessibilityService() {

    companion object {
        var instance: OrchestratorAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        TapTool.accessibilityService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        TapTool.accessibilityService = null
        instance = null
    }
}
