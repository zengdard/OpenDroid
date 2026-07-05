package com.raissummit.orchestrator.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class GetInstalledAppsTool : Tool(
    name = "get_installed_apps",
    description = "Lister les applications installées correspondant à une requête"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        val query = (parameters["query"] as? String)?.lowercase() ?: ""
        return try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val activities = pm.queryIntentActivities(intent, 0)
            val apps = activities
                .map { resolveInfo ->
                    val appName = resolveInfo.loadLabel(pm).toString()
                    val pkg = resolveInfo.activityInfo.packageName
                    AppInfo(appName, pkg)
                }
                .distinctBy { it.packageName }
                .filter { query.isBlank() || it.name.lowercase().contains(query) || it.packageName.lowercase().contains(query) }
                .sortedBy { it.name }
                .take(20)

            val data = mapOf(
                "apps" to apps.map { mapOf("name" to it.name, "package" to it.packageName) },
                "count" to apps.size,
                "query" to query
            )
            ToolResult.SuccessWithData(data)
        } catch (e: Exception) {
            ToolResult.Error("Erreur liste apps : ${e.message}")
        }
    }

    data class AppInfo(val name: String, val packageName: String)
}
