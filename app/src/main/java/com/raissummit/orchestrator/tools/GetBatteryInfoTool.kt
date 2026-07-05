package com.raissummit.orchestrator.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class GetBatteryInfoTool : Tool(
    name = "get_battery_info",
    description = "Obtenir le niveau et l'état de la batterie"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent == null) {
                return ToolResult.Error("Impossible de lire l'état de la batterie")
            }
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percentage = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            val isCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val source = when {
                plugged == BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                plugged == BatteryManager.BATTERY_PLUGGED_AC -> "Secteur"
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Sans fil"
                else -> null
            }

            ToolResult.SuccessWithData(mapOf(
                "battery_percentage" to percentage,
                "is_charging" to isCharging,
                "charge_source" to source
            ))
        } catch (e: Exception) {
            ToolResult.Error("Erreur batterie : ${e.message}")
        }
    }
}
