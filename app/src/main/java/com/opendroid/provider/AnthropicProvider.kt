package com.opendroid.provider

import com.opendroid.data.model.Message
import com.opendroid.data.model.ProviderConfig
import com.opendroid.data.model.ProviderKind
import com.opendroid.data.model.Role
import com.opendroid.data.model.ToolCallPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject

/** Provider Anthropic (Claude) — API Messages avec streaming SSE. */
class AnthropicProvider(private val config: ProviderConfig) : LlmProvider {
    override val id: String = config.id
    override val kind: ProviderKind = ProviderKind.ANTHROPIC
    override val supportsNativeTools: Boolean = true

    override fun stream(request: ChatRequest): Flow<ProviderEvent> = flow {
        val url = config.baseUrl.trimEnd('/') + "/messages"
        val headers = mapOf(
            "x-api-key" to config.apiKey,
            "anthropic-version" to "2023-06-01",
            "anthropic-beta" to "interleaved-thinking-2025-05-14"
        )
        val call = Http.client.newCall(Http.jsonRequest(url, buildBody(request).toString(), headers))
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    emit(ProviderEvent.Error("HTTP ${response.code}: ${response.body?.string()?.take(500)}"))
                    return@use
                }
                val text = StringBuilder()
                val thinking = StringBuilder()
                val calls = LinkedHashMap<Int, ToolAccum>()
                var finish = "end_turn"

                for (payload in Http.sseLines(response)) {
                    val json = try { JSONObject(payload) } catch (_: Exception) { continue }
                    when (json.optString("type")) {
                        "error" -> {
                            emit(ProviderEvent.Error(json.optJSONObject("error")?.optString("message") ?: payload))
                            return@use
                        }
                        "content_block_start" -> {
                            val idx = json.optInt("index")
                            val block = json.optJSONObject("content_block") ?: continue
                            if (block.optString("type") == "tool_use") {
                                calls[idx] = ToolAccum(block.optString("id"), block.optString("name"))
                            }
                        }
                        "content_block_delta" -> {
                            val idx = json.optInt("index")
                            val delta = json.optJSONObject("delta") ?: continue
                            when (delta.optString("type")) {
                                "text_delta" -> delta.optString("text").takeIf { it.isNotEmpty() }?.let {
                                    text.append(it); emit(ProviderEvent.TextDelta(it))
                                }
                                "thinking_delta" -> delta.optString("thinking").takeIf { it.isNotEmpty() }?.let {
                                    thinking.append(it); emit(ProviderEvent.ThinkingDelta(it))
                                }
                                "input_json_delta" -> calls.getOrPut(idx) { ToolAccum() }
                                    .args.append(delta.optString("partial_json"))
                            }
                        }
                        "message_delta" -> {
                            json.optJSONObject("delta")?.optString("stop_reason")?.takeIf { it.isNotBlank() && it != "null" }
                                ?.let { finish = it }
                        }
                        "message_stop" -> {}
                    }
                }
                val toolCalls = calls.entries.sortedBy { it.key }.mapNotNull { (_, a) ->
                    if (a.name.isBlank()) null else ToolCallPart(id = a.id.ifBlank { "call_${a.name}" }, name = a.name, arguments = a.args.toString().ifBlank { "{}" })
                }
                emit(ProviderEvent.Completed(text.toString(), thinking.toString().ifBlank { null }, toolCalls, finish))
            }
        } catch (e: Exception) {
            emit(ProviderEvent.Error(e.message ?: "Erreur réseau"))
        }
    }.flowOn(Dispatchers.IO)

    private fun buildBody(request: ChatRequest): JSONObject {
        val messages = JSONArray()
        var pendingToolResults = JSONArray()

        fun flushToolResults() {
            if (pendingToolResults.length() > 0) {
                messages.put(JSONObject().put("role", "user").put("content", pendingToolResults))
                pendingToolResults = JSONArray()
            }
        }

        request.messages.forEach { m ->
            when (m.role) {
                Role.SYSTEM -> {}
                Role.USER -> {
                    flushToolResults()
                    if (m.attachments.isEmpty()) {
                        messages.put(JSONObject().put("role", "user").put("content", m.text))
                    } else {
                        val content = JSONArray()
                        if (m.text.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", m.text))
                        m.attachments.forEach { attachment ->
                            Images.readBase64(attachment.path)?.let { b64 ->
                                content.put(
                                    JSONObject().put("type", "image").put(
                                        "source",
                                        JSONObject().put("type", "base64")
                                            .put("media_type", attachment.mimeType)
                                            .put("data", b64)
                                    )
                                )
                            }
                        }
                        messages.put(JSONObject().put("role", "user").put("content", content))
                    }
                }
                Role.ASSISTANT -> {
                    flushToolResults()
                    if (m.text.isBlank() && m.toolCalls.isEmpty()) return@forEach
                    val content = JSONArray()
                    if (m.text.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", m.text))
                    m.toolCalls.forEach { tc ->
                        content.put(
                            JSONObject().put("type", "tool_use").put("id", tc.id).put("name", tc.name)
                                .put("input", parseArgs(tc.arguments))
                        )
                    }
                    messages.put(JSONObject().put("role", "assistant").put("content", content))
                }
                Role.TOOL -> {
                    pendingToolResults.put(
                        JSONObject().put("type", "tool_result").put("tool_use_id", m.toolCallId ?: "")
                            .put("content", m.text).put("is_error", m.isError)
                    )
                }
            }
        }
        flushToolResults()

        val body = JSONObject()
            .put("model", request.model)
            .put("max_tokens", request.maxTokens)
            .put("stream", true)
            .put("temperature", request.temperature)
            .put("messages", messages)
        if (request.system.isNotBlank()) body.put("system", request.system)
        if (request.tools.isNotEmpty()) body.put("tools", Schema.anthropicTools(request.tools))
        return body
    }

    private fun parseArgs(args: String): Any = try {
        if (args.isBlank()) JSONObject() else JSONObject(args)
    } catch (_: Exception) {
        JSONObject()
    }

    private class ToolAccum(val id: String = "", val name: String = "") {
        val args = StringBuilder()
    }
}
