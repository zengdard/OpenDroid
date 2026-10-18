package com.opendroid.mcp

import com.opendroid.data.model.ToolParameter
import com.opendroid.data.model.ToolSpec
import com.opendroid.tool.Tool
import com.opendroid.tool.ToolContext
import com.opendroid.tool.ToolResult
import org.json.JSONObject

/**
 * Adapte un outil MCP en [Tool] OpenDroid.
 * Toutes les permissions MCP sont regroupées sous la clé `mcp`.
 */
class McpTool(
    private val serverId: String,
    private val def: McpToolDef,
    private val client: McpClient
) : Tool {

    override val spec = ToolSpec(
        id = sanitize("mcp_${serverId}_${def.name}"),
        description = ("[MCP $serverId] " + def.description.ifBlank { def.name }).take(1024),
        category = "mcp",
        parameters = schemaToParams(def.inputSchema)
    )

    override val permissionKey: String = "mcp"

    override fun permissionInput(args: JSONObject): String = def.name

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult =
        client.callTool(def.name, args).fold(
            onSuccess = { ToolResult(it.text, it.isError) },
            onFailure = { ToolResult.error("MCP ${def.name} : ${it.message}") }
        )

    companion object {
        fun sanitize(value: String): String =
            value.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(60)

        /**
         * Convertit un JSON Schema (MCP) en paramètres plats OpenDroid.
         * Les types complexes (objet/tableau) sont exposés comme chaîne JSON.
         */
        fun schemaToParams(schema: JSONObject?): List<ToolParameter> {
            val properties = schema?.optJSONObject("properties") ?: return emptyList()
            val required = schema.optJSONArray("required")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }.toSet()
            } ?: emptySet()

            return properties.keys().asSequence().map { key ->
                val prop = properties.optJSONObject(key) ?: JSONObject()
                val rawType = prop.optString("type")
                val type = when (rawType) {
                    "integer" -> "integer"
                    "number" -> "number"
                    "boolean" -> "boolean"
                    else -> "string"
                }
                val description = prop.optString("description", "").ifBlank {
                    if (rawType == "object" || rawType == "array") "Valeur JSON ($rawType)" else ""
                }
                ToolParameter(
                    name = key,
                    type = type,
                    description = description,
                    required = key in required
                )
            }.toList()
        }
    }
}
