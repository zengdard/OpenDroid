package com.raissummit.orchestrator.tools

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class SetAlarmTool : Tool(
    name = "set_alarm",
    description = "Programmer une alarme"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        val hour = (parameters["hour"] as? Number)?.toInt() ?: return ToolResult.Error("Heure manquante")
        val minute = (parameters["minute"] as? Number)?.toInt() ?: return ToolResult.Error("Minute manquante")
        val label = parameters["label"] as? String ?: "Alarme"

        return try {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("label", label)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, hour * 60 + minute, intent, flags
            )

            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            ToolResult.SuccessWithData(mapOf("time" to "$hour:$minute", "label" to label))
        } catch (e: SecurityException) {
            ToolResult.Error("Permission SET_ALARM non accordée")
        } catch (e: Exception) {
            ToolResult.Error("Erreur de programmation d'alarme : ${e.message}")
        }
    }
}
