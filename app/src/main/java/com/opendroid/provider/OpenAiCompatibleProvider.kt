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

/**
 * Provider pour toute API compatible OpenAI `/chat/completions` :
 * OpenAI, OpenRouter, Groq, Together, Mistral, xAI, Ollama (/v1), LM Studio…
 */
class OpenAiCompatibleProvider(private val config: ProviderConfig) : LlmProvider {
    override val id: String = config.id
    override val kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE
    override val supportsNativeTools: Boolean = true

    override fun stream(request: ChatRequest): Flow<ProviderEvent> = flow {
        val url = config.baseUrl.trimEnd('/') + "/chat/completions"
        val body = buildBody(request)
        val headers = buildMap {
            if (config.apiKey.isNotBlank()) put("Authorization", "Bearer ${config.apiKey}")
            // OpenCode Go exige un User-Agent de client dédié (pas un SDK/HTTP lib)
            // et un identifiant de session stable pour le routage/prompt caching.
            if (config.id.startsWith("opencode")) {
                put("User-Agent", "opendroid/1.0")
                if (request.sessionId.isNotBlank()) put("x-opencode-session", request.sessionId)
            }
            if (config.id == "openrouter") {
                put("HTTP-Referer", "https://opendroid.app")
                put("X-Title", "OpenDroid")
            }
        }
        val call = Http.client.newCall(Http.jsonRequest(url, body.toString(), headers))
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    emit(ProviderEvent.Error("HTTP ${response.code}: ${response.body?.string()?.take(500)}"))
                    return@use
                }
                val text = StringBuilder()
                val thinking = StringBuilder()
                val toolAcc = LinkedHashMap<Int, ToolAccum>()
                var finish = "stop"

                for (payload in Http.sseLines(response)) {
                    if (payload == "[DONE]") break
                    val json = try { JSONObject(payload) } catch (_: Exception) { continue }
                    val err = json.optJSONObject("error")
                    if (err != null) { emit(ProviderEvent.Error(err.optString("message", err.toString()))); return@use }
                    val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: continue
                    choice.optString("finish_reason").takeIf { it.isNotBlank() && it != "null" }?.let { finish = it }
                    val delta = choice.optJSONObject("delta") ?: continue
                    delta.optString("content").takeIf { it.isNotEmpty() && it != "null" }?.let {
                        text.append(it); emit(ProviderEvent.TextDelta(it))
                    }
                    (delta.optString("reasoning_content").takeIf { it.isNotEmpty() && it != "null" }
                        ?: delta.optString("reasoning").takeIf { it.isNotEmpty() && it != "null" })?.let {
                        thinking.append(it); emit(ProviderEvent.ThinkingDelta(it))
                    }
                    delta.optJSONArray("tool_calls")?.let { tcs ->
                        for (i in 0 until tcs.length()) {
                            val tc = tcs.optJSONObject(i) ?: continue
                            val index = tc.optInt("index", i)
                            val acc = toolAcc.getOrPut(index) { ToolAccum() }
                            if (tc.has("id")) acc.id = tc.optString("id")
                            val fn = tc.optJSONObject("function")
                            if (fn != null) {
                                if (fn.has("name")) acc.name = fn.optString("name")
                                if (fn.has("arguments")) acc.args.append(fn.optString("arguments"))
                            }
                        }
                    }
                }

                val calls = toolAcc.entries.sortedBy { it.key }.mapNotNull { (_, a) ->
                    if (a.name.isBlank()) null
                    else ToolCallPart(id = a.id.ifBlank { "call_${a.name}" }, name = a.name, arguments = a.args.toString().ifBlank { "{}" })
                }
                emit(ProviderEvent.Completed(text.toString(), thinking.toString().ifBlank { null }, calls, finish))
            }
        } catch (e: Exception) {
            emit(ProviderEvent.Error(e.message ?: "Erreur réseau"))
        }
    }.flowOn(Dispatchers.IO)

    private fun buildBody(request: ChatRequest): JSONObject {
        val messages = JSONArray()
        if (request.system.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", request.system))
        }
        request.messages.forEach { m ->
            when (m.role) {
                Role.SYSTEM -> messages.put(JSONObject().put("role", "system").put("content", m.text))
                Role.USER -> messages.put(JSONObject().put("role", "user").put("content", userContent(m)))
                Role.ASSISTANT -> {
                    val obj = JSONObject().put("role", "assistant")
                    obj.put("content", m.text.ifBlank { JSONObject.NULL })
                    if (m.toolCalls.isNotEmpty()) {
                        val tcs = JSONArray()
                        m.toolCalls.forEach { tc ->
                            tcs.put(
                                JSONObject()
                                    .put("id", tc.id)
                                    .put("type", "function")
                                    .put("function", JSONObject().put("name", tc.name).put("arguments", tc.arguments))
                            )
                        }
                        obj.put("tool_calls", tcs)
                    }
                    messages.put(obj)
                }
                Role.TOOL -> messages.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", m.toolCallId ?: "")
                        .put("content", m.text)
                )
            }
        }
        val body = JSONObject()
            .put("model", request.model)
            .put("messages", messages)
            .put("stream", true)
            .put("temperature", request.temperature)
            .put("max_tokens", request.maxTokens)
        if (request.tools.isNotEmpty()) {
            body.put("tools", Schema.openAiTools(request.tools))
            body.put("tool_choice", "auto")
        }
        return body
    }

    /** Contenu d'un message user : texte simple, ou tableau texte + images. */
    private fun userContent(message: Message): Any {
        if (message.attachments.isEmpty()) return message.text
        val content = JSONArray()
        if (message.text.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", message.text))
        message.attachments.forEach { attachment ->
            Images.readBase64(attachment.path)?.let { b64 ->
                content.put(
                    JSONObject().put("type", "image_url").put(
                        "image_url",
                        JSONObject().put("url", "data:${attachment.mimeType};base64,$b64")
                    )
                )
            }
        }
        return content
    }

    private class ToolAccum {
        var id: String = ""
        var name: String = ""
        val args = StringBuilder()
    }
}
