package com.opendroid.mcp

import com.opendroid.data.model.McpServerConfig
import com.opendroid.provider.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Définition d'un outil exposé par un serveur MCP. */
data class McpToolDef(
    val name: String,
    val description: String,
    val inputSchema: JSONObject?
)

data class McpCallResult(val text: String, val isError: Boolean)

/**
 * Client MCP minimal (JSON-RPC 2.0 sur HTTP, transport « streamable HTTP »).
 * Gère les réponses `application/json` et `text/event-stream`, ainsi que
 * l'en-tête `Mcp-Session-Id`.
 */
class McpClient(private val config: McpServerConfig) {

    @Volatile
    private var sessionId: String? = null
    private var nextId = 1

    @Volatile
    var lastError: String? = null
        private set

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val result = rpc(
                "initialize",
                JSONObject()
                    .put("protocolVersion", "2025-06-18")
                    .put("capabilities", JSONObject())
                    .put("clientInfo", JSONObject().put("name", "OpenDroid").put("version", "1.0.0"))
            )
            if (result == null) {
                Result.failure(Exception(lastError ?: "initialize a échoué"))
            } else {
                notify("notifications/initialized", JSONObject())
                Result.success(Unit)
            }
        } catch (e: Exception) {
            lastError = e.message
            Result.failure(e)
        }
    }

    suspend fun listTools(): Result<List<McpToolDef>> = withContext(Dispatchers.IO) {
        try {
            val result = rpc("tools/list", JSONObject())
                ?: return@withContext Result.failure(Exception(lastError ?: "tools/list a échoué"))
            val array = result.optJSONArray("tools") ?: JSONArray()
            val defs = (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name")
                if (name.isBlank()) null
                else McpToolDef(name, o.optString("description", ""), o.optJSONObject("inputSchema"))
            }
            Result.success(defs)
        } catch (e: Exception) {
            lastError = e.message
            Result.failure(e)
        }
    }

    suspend fun callTool(name: String, arguments: JSONObject): Result<McpCallResult> =
        withContext(Dispatchers.IO) {
            try {
                val result = rpc(
                    "tools/call",
                    JSONObject().put("name", name).put("arguments", arguments)
                ) ?: return@withContext Result.failure(Exception(lastError ?: "tools/call a échoué"))

                val isError = result.optBoolean("isError", false)
                val content = result.optJSONArray("content")
                val text = buildString {
                    if (content != null) {
                        for (i in 0 until content.length()) {
                            val item = content.optJSONObject(i) ?: continue
                            when (item.optString("type")) {
                                "text" -> append(item.optString("text"))
                                else -> append("[${item.optString("type")}]")
                            }
                            append('\n')
                        }
                    }
                    if (isEmpty()) append(result.toString())
                }
                Result.success(McpCallResult(text.trim(), isError))
            } catch (e: Exception) {
                lastError = e.message
                Result.failure(e)
            }
        }

    private fun notify(method: String, params: JSONObject) {
        post(JSONObject().put("jsonrpc", "2.0").put("method", method).put("params", params))
    }

    private fun rpc(method: String, params: JSONObject): JSONObject? {
        val id = nextId++
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        val payload = post(body) ?: return null
        if (payload.has("error")) {
            lastError = payload.optJSONObject("error")?.optString("message") ?: payload.toString()
            return null
        }
        return payload.optJSONObject("result")
    }

    private fun post(body: JSONObject): JSONObject? {
        val builder = Request.Builder()
            .url(config.url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json, text/event-stream")
            .header("User-Agent", "opendroid/1.0")
        config.headers.forEach { (k, v) -> builder.header(k, v) }
        sessionId?.let { builder.header("Mcp-Session-Id", it) }

        return try {
            Http.client.newCall(builder.build()).execute().use { response ->
                response.header("Mcp-Session-Id")?.let { sessionId = it }
                if (!response.isSuccessful) {
                    lastError = "HTTP ${response.code}: ${response.body?.string()?.take(300)}"
                    return null
                }
                val contentType = response.header("Content-Type").orEmpty()
                val bodyText = response.body?.string().orEmpty()
                if (contentType.contains("text/event-stream")) parseSse(bodyText)
                else JSONObject(bodyText)
            }
        } catch (e: Exception) {
            lastError = e.message
            null
        }
    }

    /** Prend la première réponse JSON-RPC valide d'un flux SSE. */
    private fun parseSse(body: String): JSONObject? {
        for (line in body.lineSequence()) {
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty() || payload == "[DONE]") continue
            try {
                val json = JSONObject(payload)
                if (json.has("id") && (json.has("result") || json.has("error"))) return json
            } catch (_: Exception) {
            }
        }
        return null
    }
}
