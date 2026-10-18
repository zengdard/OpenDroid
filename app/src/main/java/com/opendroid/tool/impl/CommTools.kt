package com.opendroid.tool.impl

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.telephony.SmsManager
import com.opendroid.data.model.ToolParameter
import com.opendroid.data.model.ToolSpec
import com.opendroid.tool.Args
import com.opendroid.tool.Tool
import com.opendroid.tool.ToolContext
import com.opendroid.tool.ToolResult
import org.json.JSONObject
import java.util.Calendar

/** Outils de communication et productivité. */

class SendSmsTool : Tool {
    override val spec = ToolSpec(
        id = "send_sms",
        description = "Envoyer un SMS à un numéro.",
        category = "comm",
        parameters = listOf(
            ToolParameter("recipient", "string", "Numéro de téléphone"),
            ToolParameter("message", "string", "Contenu du message")
        ),
        dangerousByDefault = true
    )
    override fun permissionInput(args: JSONObject) = Args.str(args, "recipient")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val to = Args.str(args, "recipient") ?: return ToolResult.error("Paramètre 'recipient' requis")
        val msg = Args.str(args, "message") ?: return ToolResult.error("Paramètre 'message' requis")
        return try {
            SmsManager.getDefault().sendTextMessage(to, null, msg, null, null)
            ToolResult.ok("SMS envoyé à $to")
        } catch (e: SecurityException) {
            ToolResult.error("Permission SEND_SMS non accordée")
        } catch (e: Exception) {
            ToolResult.error("Échec de l'envoi : ${e.message}")
        }
    }
}

class MakeCallTool : Tool {
    override val spec = ToolSpec(
        id = "make_call",
        description = "Passer un appel téléphonique.",
        category = "comm",
        parameters = listOf(ToolParameter("number", "string", "Numéro de téléphone")),
        dangerousByDefault = true
    )
    override fun permissionInput(args: JSONObject) = Args.str(args, "number")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val number = Args.str(args, "number") ?: return ToolResult.error("Paramètre 'number' requis")
        return try {
            context.appContext.startActivity(
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ToolResult.ok("Appel lancé vers $number")
        } catch (e: SecurityException) {
            ToolResult.error("Permission CALL_PHONE non accordée")
        }
    }
}

class ShareTextTool : Tool {
    override val spec = ToolSpec(
        id = "share_text",
        description = "Partager du texte vers une autre application.",
        category = "comm",
        parameters = listOf(ToolParameter("text", "string", "Texte à partager"))
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val text = args.optString("text", "")
        if (text.isEmpty()) return ToolResult.error("Paramètre 'text' requis")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.appContext.startActivity(Intent.createChooser(intent, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolResult.ok("Partage lancé")
        } catch (e: Exception) {
            ToolResult.error("Partage impossible : ${e.message}")
        }
    }
}

class ContactsSearchTool : Tool {
    override val spec = ToolSpec(
        id = "contacts_search",
        description = "Rechercher un contact par nom et obtenir son numéro de téléphone.",
        category = "comm",
        parameters = listOf(ToolParameter("query", "string", "Nom du contact"))
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val query = Args.str(args, "query") ?: return ToolResult.error("Paramètre 'query' requis")
        val resolver = context.appContext.contentResolver
        val results = mutableListOf<String>()
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query))
        try {
            resolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )?.use { c ->
                while (c.moveToNext() && results.size < 15) {
                    results.add("${c.getString(0)} — ${c.getString(1)}")
                }
            }
        } catch (e: SecurityException) {
            return ToolResult.error("Permission READ_CONTACTS non accordée")
        }
        return if (results.isEmpty()) ToolResult.ok("Aucun contact pour « $query »")
        else ToolResult.ok(results.joinToString("\n"))
    }
}

class SetAlarmTool : Tool {
    override val spec = ToolSpec(
        id = "set_alarm",
        description = "Programmer une alarme.",
        category = "system",
        parameters = listOf(
            ToolParameter("hour", "integer", "Heure (0-23)"),
            ToolParameter("minute", "integer", "Minute (0-59)"),
            ToolParameter("label", "string", "Libellé", required = false)
        )
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val hour = Args.int(args, "hour") ?: return ToolResult.error("Paramètre 'hour' requis")
        val minute = Args.int(args, "minute") ?: return ToolResult.error("Paramètre 'minute' requis")
        val label = Args.str(args, "label", "Alarme")
        return try {
            ToolResult.ok(Alarms.schedule(context.appContext, hour, minute, label))
        } catch (e: Exception) {
            ToolResult.error("Programmation impossible : ${e.message}")
        }
    }
}

class CreateEventTool : Tool {
    override val spec = ToolSpec(
        id = "create_event",
        description = "Créer un événement dans le calendrier.",
        category = "comm",
        parameters = listOf(
            ToolParameter("title", "string", "Titre"),
            ToolParameter("start_epoch_ms", "integer", "Début (ms epoch)"),
            ToolParameter("duration_minutes", "integer", "Durée en minutes (défaut 60)", required = false),
            ToolParameter("description", "string", "Description", required = false)
        ),
        dangerousByDefault = true
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val title = Args.str(args, "title") ?: return ToolResult.error("Paramètre 'title' requis")
        val start = Args.int(args, "start_epoch_ms")?.toLong() ?: System.currentTimeMillis() + 3_600_000
        val dur = Args.int(args, "duration_minutes", 60)
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, 1)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, Args.str(args, "description", ""))
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, start + dur * 60_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }
        return try {
            val uri = context.appContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) ToolResult.ok("Événement « $title » créé") else ToolResult.error("Création impossible")
        } catch (e: SecurityException) {
            ToolResult.error("Permission calendrier non accordée")
        }
    }
}

class ListEventsTool : Tool {
    override val spec = ToolSpec(
        id = "list_events",
        description = "Lister les prochains événements du calendrier.",
        category = "comm",
        parameters = listOf(ToolParameter("days", "integer", "Horizon en jours (défaut 7)", required = false))
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val days = Args.int(args, "days", 7)
        val now = System.currentTimeMillis()
        val until = now + days * 86_400_000L
        val projection = arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART)
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val out = mutableListOf<String>()
        return try {
            context.appContext.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, projection, selection, arrayOf(now.toString(), until.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { c ->
                while (c.moveToNext() && out.size < 20) {
                    val t = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(c.getLong(1)))
                    out.add("$t — ${c.getString(0)}")
                }
            }
            if (out.isEmpty()) ToolResult.ok("(aucun événement à venir)") else ToolResult.ok(out.joinToString("\n"))
        } catch (e: SecurityException) {
            ToolResult.error("Permission calendrier non accordée")
        }
    }
}
