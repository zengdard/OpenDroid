package com.opendroid.tool.impl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.view.KeyEvent
import com.opendroid.data.model.ToolParameter
import com.opendroid.data.model.ToolSpec
import com.opendroid.tool.Args
import com.opendroid.tool.Tool
import com.opendroid.tool.ToolContext
import com.opendroid.tool.ToolResult
import org.json.JSONObject

/** Outils de l'appareil : infos, batterie, réseau, localisation, presse-papiers, notifications, média, capture. */

class DeviceInfoTool : Tool {
    override val spec = ToolSpec(
        id = "device_info",
        description = "Informations sur l'appareil (modèle, Android, batterie, stockage...).",
        category = "device"
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val rt = Runtime.getRuntime()
        val freeMb = rt.freeMemory() / (1024 * 1024)
        return ToolResult.ok(
            "Modèle: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "Appareil: ${Build.DEVICE}\n" +
                "Mémoire libre (JVM): ${freeMb} Mo"
        )
    }
}

class BatteryTool : Tool {
    override val spec = ToolSpec(id = "get_battery_info", description = "Niveau et état de la batterie.", category = "device")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val intent = context.appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return ToolResult.error("Batterie indisponible")
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return ToolResult.ok("Batterie : $pct% — ${if (charging) "en charge" else "décharge"}")
    }
}

class NetworkStatusTool : Tool {
    override val spec = ToolSpec(id = "get_network_status", description = "État de la connectivité réseau.", category = "device")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val cm = context.appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return ToolResult.ok("Aucun réseau actif")
        val caps = cm.getNetworkCapabilities(net) ?: return ToolResult.ok("Réseau inconnu")
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "autre"
        }
        val internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return ToolResult.ok("Réseau : $type (${if (internet) "Internet OK" else "sans Internet"})")
    }
}

class LocationTool : Tool {
    override val spec = ToolSpec(id = "get_location", description = "Localisation approximative de l'appareil.", category = "device")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        return try {
            val lm = context.appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            val loc = providers.asSequence().mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.firstOrNull()
                ?: return ToolResult.error("Localisation indisponible (activez le GPS et les permissions)")
            ToolResult.ok("Latitude: %.5f, Longitude: %.5f".format(loc.latitude, loc.longitude))
        } catch (e: SecurityException) {
            ToolResult.error("Permission de localisation non accordée")
        } catch (e: Exception) {
            ToolResult.error("Erreur localisation : ${e.message}")
        }
    }
}

class ClipboardGetTool : Tool {
    override val spec = ToolSpec(id = "clipboard_get", description = "Lire le contenu du presse-papiers.", category = "device")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val cm = context.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context.appContext).toString() else ""
        return ToolResult.ok(text.ifBlank { "(presse-papiers vide)" })
    }
}

class ClipboardSetTool : Tool {
    override val spec = ToolSpec(
        id = "clipboard_set",
        description = "Copier du texte dans le presse-papiers.",
        category = "device",
        parameters = listOf(ToolParameter("text", "string", "Texte à copier"))
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val text = args.optString("text", "")
        if (text.isEmpty()) return ToolResult.error("Paramètre 'text' requis")
        val cm = context.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("OpenDroid", text))
        return ToolResult.ok("Texte copié (${text.length} caractères)")
    }
}

class NotificationSendTool : Tool {
    override val spec = ToolSpec(
        id = "notification_send",
        description = "Afficher une notification locale.",
        category = "device",
        parameters = listOf(
            ToolParameter("title", "string", "Titre"),
            ToolParameter("text", "string", "Contenu")
        )
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val title = Args.str(args, "title", "OpenDroid")
        val text = args.optString("text", "")
        val nm = context.appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = "opendroid_agent"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(id, "OpenDroid", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(context.appContext, id) else @Suppress("DEPRECATION") Notification.Builder(context.appContext)
        notif.setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true)
        return try {
            nm.notify((System.currentTimeMillis() and 0xFFFF).toInt(), notif.build())
            ToolResult.ok("Notification affichée")
        } catch (e: Exception) {
            ToolResult.error("Notification impossible : ${e.message}")
        }
    }
}

class NotificationListTool : Tool {
    override val spec = ToolSpec(id = "notifications_list", description = "Lister les notifications actives (nécessite l'accès aux notifications).", category = "device")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val list = NotificationCollector.active()
        if (list.isEmpty()) return ToolResult.ok("(aucune notification active, ou accès notifications non accordé)")
        return ToolResult.ok(list.joinToString("\n") { "- [${it.packageName}] ${it.title}: ${it.text}" })
    }
}

class MediaControlTool : Tool {
    override val spec = ToolSpec(
        id = "media_control",
        description = "Contrôler la lecture multimédia (play, pause, next, previous, volume_up, volume_down).",
        category = "device",
        parameters = listOf(
            ToolParameter("action", "string", "Action", enumValues = listOf("play", "pause", "next", "previous", "volume_up", "volume_down"))
        )
    )

    @Suppress("DEPRECATION")
    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val am = context.appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return when (Args.str(args, "action", "play").lowercase()) {
            "play" -> { am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)); ToolResult.ok("Lecture") }
            "pause" -> { am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)); ToolResult.ok("Pause") }
            "next" -> { am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)); ToolResult.ok("Piste suivante") }
            "previous" -> { am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS)); ToolResult.ok("Piste précédente") }
            "volume_up" -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); ToolResult.ok("Volume +") }
            "volume_down" -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); ToolResult.ok("Volume -") }
            else -> ToolResult.error("Action inconnue")
        }
    }
}

class ScreenshotTool : Tool {
    override val spec = ToolSpec(id = "take_screenshot", description = "Prendre une capture d'écran et l'enregistrer.", category = "device", dangerousByDefault = true)

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        if (!context.accessibility.isReady) return ToolResult.error("Service d'accessibilité requis pour la capture")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ToolResult.error("Capture nécessite Android 11+")
        val bitmap = context.accessibility.takeScreenshot() ?: return ToolResult.error("Capture échouée")
        return try {
            val dir = context.appContext.getExternalFilesDir("screenshots") ?: context.appContext.filesDir
            dir.mkdirs()
            val file = java.io.File(dir, "screenshot_${System.currentTimeMillis()}.png")
            java.io.FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            ToolResult.ok("Capture enregistrée : ${file.absolutePath}")
        } catch (e: Exception) {
            ToolResult.error("Enregistrement impossible : ${e.message}")
        }
    }
}
