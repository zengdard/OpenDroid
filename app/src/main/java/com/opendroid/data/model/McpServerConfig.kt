package com.opendroid.data.model

import java.util.UUID

/**
 * Un serveur MCP (Model Context Protocol) joignable en HTTP JSON-RPC.
 * Ses outils sont exposés à l'agent avec l'identifiant `mcp_<serveur>_<outil>`.
 */
data class McpServerConfig(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
)
