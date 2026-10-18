package com.opendroid.tool.impl

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.opendroid.data.model.ToolParameter
import com.opendroid.data.model.ToolSpec
import com.opendroid.tool.Args
import com.opendroid.tool.Tool
import com.opendroid.tool.ToolContext
import com.opendroid.tool.ToolResult
import org.json.JSONObject

/** Outils d'applications : list_apps, app_info, open_app, open_url. */

class ListAppsTool : Tool {
    override val spec = ToolSpec(
        id = "list_apps",
        description = "Lister les applications installées, filtrées éventuellement par nom. " +
            "Utilise-le pour découvrir dynamiquement les apps au lieu de deviner les paquets.",
        category = "app",
        parameters = listOf(
            ToolParameter("query", "string", "Filtre optionnel par nom ou paquet", required = false)
        )
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val query = args.optString("query", "").lowercase()
        val pm = context.appContext.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        val apps = resolved.map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .filter { query.isBlank() || it.first.lowercase().contains(query) || it.second.lowercase().contains(query) }
            .sortedBy { it.first }
            .take(30)
        if (apps.isEmpty()) return ToolResult.ok("Aucune application trouvée pour « $query »")
        return ToolResult.ok(apps.joinToString("\n") { "- ${it.first} (${it.second})" })
    }
}

class AppInfoTool : Tool {
    override val spec = ToolSpec(
        id = "app_info",
        description = "Obtenir les détails d'une application (nom, version, paquet).",
        category = "app",
        parameters = listOf(
            ToolParameter("package", "string", "Package name Android")
        )
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val pkg = Args.str(args, "package") ?: return ToolResult.error("Paramètre 'package' requis")
        val pm = context.appContext.packageManager
        return try {
            val info = pm.getPackageInfo(pkg, 0)
            val appInfo = info.applicationInfo ?: return ToolResult.error("Informations d'application indisponibles : $pkg")
            val label = pm.getApplicationLabel(appInfo).toString()
            ToolResult.ok(
                "Nom: $label\nPaquet: $pkg\nVersion: ${info.versionName ?: "?"} (${info.longVersionCode})\n" +
                    "UID: ${appInfo.uid}\nActivé: ${appInfo.enabled}"
            )
        } catch (e: PackageManager.NameNotFoundException) {
            ToolResult.error("Application introuvable : $pkg")
        }
    }
}

class OpenAppTool : Tool {
    override val spec = ToolSpec(
        id = "open_app",
        description = "Ouvrir une application par son package name. Utilise list_apps d'abord.",
        category = "app",
        parameters = listOf(
            ToolParameter("package", "string", "Package name Android")
        )
    )
    override val permissionKey = "open_app"
    override fun permissionInput(args: JSONObject) = Args.str(args, "package")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val pkg = Args.str(args, "package") ?: return ToolResult.error("Paramètre 'package' requis")
        val launch = context.appContext.packageManager.getLaunchIntentForPackage(pkg)
            ?: return ToolResult.error("Application non lançable : $pkg")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.appContext.startActivity(launch)
            ToolResult.ok("Application ouverte : $pkg")
        } catch (e: Exception) {
            ToolResult.error("Ouverture impossible : ${e.message}")
        }
    }
}

class OpenUrlTool : Tool {
    override val spec = ToolSpec(
        id = "open_url",
        description = "Ouvrir une URL dans le navigateur ou l'application appropriée.",
        category = "app",
        parameters = listOf(
            ToolParameter("url", "string", "URL à ouvrir")
        )
    )
    override val permissionKey = "open_url"
    override fun permissionInput(args: JSONObject) = Args.str(args, "url")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        var url = Args.str(args, "url") ?: return ToolResult.error("Paramètre 'url' requis")
        if (!url.contains("://")) url = "https://$url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.appContext.startActivity(intent)
            ToolResult.ok("Ouverture de $url")
        } catch (e: Exception) {
            ToolResult.error("Ouverture impossible : ${e.message}")
        }
    }
}

/** Outil utilitaire : ouvrir un écran de réglages Android. */
class OpenSettingsTool : Tool {
    override val spec = ToolSpec(
        id = "open_settings",
        description = "Ouvrir un écran de paramètres Android (accessibility, wifi, bluetooth, apps, battery...).",
        category = "app",
        parameters = listOf(
            ToolParameter("screen", "string", "Écran", enumValues = listOf("main", "accessibility", "wifi", "bluetooth", "apps", "battery", "display", "sound", "location"))
        )
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val action = when (Args.str(args, "screen", "main")) {
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.appContext.startActivity(intent)
            ToolResult.ok("Paramètres ouverts : ${Args.str(args, "screen", "main")}")
        } catch (e: Exception) {
            ToolResult.error("Ouverture impossible : ${e.message}")
        }
    }
}
