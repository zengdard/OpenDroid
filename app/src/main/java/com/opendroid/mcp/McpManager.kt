package com.opendroid.mcp

import com.opendroid.data.model.McpServerConfig
import com.opendroid.tool.Tool
import com.opendroid.tool.ToolRegistry

/**
 * Gère le cycle de vie des serveurs MCP : initialisation, découverte des outils
 * et enregistrement dans le [ToolRegistry].
 */
class McpManager {

    private val clients = mutableMapOf<String, McpClient>()

    /**
     * (Re)connecte tous les serveurs activés et publie leurs outils.
     * Renvoie un statut lisible par serveur (nom → ✅/❌).
     */
    suspend fun refresh(servers: List<McpServerConfig>): Map<String, String> {
        val statuses = LinkedHashMap<String, String>()
        val tools = mutableListOf<Tool>()

        for (server in servers) {
            if (!server.enabled || server.url.isBlank()) continue
            val client = clients.getOrPut(server.id) { McpClient(server) }

            val init = client.initialize()
            if (init.isFailure) {
                statuses[server.name] = "❌ ${init.exceptionOrNull()?.message ?: "initialisation"}"
                continue
            }
            val listed = client.listTools()
            if (listed.isFailure) {
                statuses[server.name] = "❌ ${listed.exceptionOrNull()?.message ?: "tools/list"}"
                continue
            }
            val defs = listed.getOrThrow()
            defs.forEach { tools += McpTool(server.id, it, client) }
            statuses[server.name] = "✅ ${defs.size} outil(s)"
        }

        ToolRegistry.setDynamicTools(tools)
        return statuses
    }

    fun forget(serverId: String) {
        clients.remove(serverId)
    }
}
