package com.raissummit.orchestrator.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class GetNetworkStatusTool : Tool(
    name = "get_network_status",
    description = "Obtenir l'état de la connectivité réseau (WiFi, données mobiles)"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return ToolResult.SuccessWithData(mapOf(
                "connected" to false,
                "type" to "none"
            ))
            val caps = cm.getNetworkCapabilities(network) ?: return ToolResult.SuccessWithData(mapOf(
                "connected" to false,
                "type" to "unknown"
            ))

            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }

            ToolResult.SuccessWithData(mapOf(
                "connected" to true,
                "type" to type,
                "has_internet" to caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ))
        } catch (e: Exception) {
            ToolResult.Error("Erreur réseau : ${e.message}")
        }
    }
}
