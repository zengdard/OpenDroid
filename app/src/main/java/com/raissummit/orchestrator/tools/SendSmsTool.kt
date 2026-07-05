package com.raissummit.orchestrator.tools

import android.content.Context
import android.telephony.SmsManager

class SendSmsTool : Tool(
    name = "send_sms",
    description = "Envoyer un message SMS"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        val recipient = parameters["recipient"] as? String ?: return ToolResult.Error("Destinataire manquant")
        val message = parameters["message"] as? String ?: return ToolResult.Error("Message manquant")
        return try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(recipient, null, message, null, null)
            ToolResult.Success
        } catch (e: SecurityException) {
            ToolResult.Error("Permission SEND_SMS non accordée")
        } catch (e: Exception) {
            ToolResult.Error("Erreur d'envoi SMS : ${e.message}")
        }
    }
}
