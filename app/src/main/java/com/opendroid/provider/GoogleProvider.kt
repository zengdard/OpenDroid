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

/** Provider Google Gemini — API generateContent / streamGenerateContent. */
class GoogleProvider(private val config: ProviderConfig) : LlmProvider {
    override val id: String = config.id
    override val kind: ProviderKind = ProviderKind.GOOGLE
    override val supportsNativeTools: Boolean = true

    override fun stream(request: ChatRequest): Flow<ProviderEvent> = flow {
        val base = config.baseUrl.trimEnd('/')
        val url = "$base/models/${request.model}:streamGenerateContent?alt=sse&key=${config.apiKey}"
        val call = Http.client.newCall(Http.jsonRequest(url, buildBody(request).toString()))
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    emit(ProviderEvent.Error("HTTP ${response.code}: ${response.body?.string()?.take(500)}"))
                    return@use
                }
                val text = StringBuilder()
                val calls = mutableListOf<ToolCallPart>()
                var finish = "STOP"

                for (payload in Http.sseLines(response)) {
                    val json = try { JSONObject(payload) } catch (_: Exception) { continue }
                    val candidate = json.optJSONArray("candidates")?.optJSONObject(0) ?: continue
                    candidate.optString("finishReason").takeIf { it.isNotBlank() && it != "null" }?.let { finish = it }
                    val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: continue
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        part.optString("text").takeIf { it.isNotEmpty() }?.let {
                            text.append(it); emit(ProviderEvent.TextDelta(it))
                        }
                        part.optJSONObject("functionCall")?.let { fc ->
                            val name = fc.optString("name")
                            val args = fc.optJSONObject("args")?.toString() ?: "{}"
                            if (name.isNotBlank()) calls.add(ToolCallPart(name = name, arguments = args))
                        }
                    }
                }
                emit(ProviderEvent.Completed(text.toString(), null, calls, finish))
            }
        } catch (e: Exception) {
            emit(ProviderEvent.Error(e.message ?: "Erreur réseau"))
        }
    }.flowOn(Dispatchers.IO)

    private fun buildBody(request: ChatRequest): JSONObject {
        val contents = JSONArray()
        request.messages.forEach { m ->
            val parts = JSONArray()
            when (m.role) {
                Role.SYSTEM -> return@forEach
                Role.USER -> {
                    if (m.text.isNotBlank()) parts.put(JSONObject().put("text", m.text))
                    m.attachments.forEach { attachment ->
                        Images.readBase64(attachment.path)?.let { b64 ->
                            parts.put(
                                JSONObject().put(
                                    "inline_data",
                                    JSONObject().put("mime_type", attachment.mimeType).put("data", b64)
                                )
                            )
                        }
                    }
                }
                Role.ASSISTANT -> {
                    if (m.text.isNotBlank()) parts.put(JSONObject().put("text", m.text))
                    m.toolCalls.forEach { tc ->
                        parts.put(
                            JSONObject().put(
                                "functionCall",
                                JSONObject().put("name", tc.name).put("args", parseArgs(tc.arguments))
                            )
                        )
                    }
                    if (parts.length() == 0) return@forEach
                }
                Role.TOOL -> parts.put(
                    JSONObject().put(
                        "functionResponse",
                        JSONObject().put("name", m.toolName ?: "tool")
                            .put("response", JSONObject().put("result", m.text))
                    )
                )
            }
            contents.put(
                JSONObject().put("role", if (m.role == Role.ASSISTANT) "model" else "user").put("parts", parts)
            )
        }

        val body = JSONObject().put("contents", contents)
        if (request.system.isNotBlank()) {
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.system))))
        }
        if (request.tools.isNotEmpty()) body.put("tools", Schema.googleTools(request.tools))
        body.put(
            "generationConfig",
            JSONObject().put("temperature", request.temperature).put("maxOutputTokens", request.maxTokens)
        )
        return body
    }

    private fun parseArgs(args: String): Any = try {
        if (args.isBlank()) JSONObject() else JSONObject(args)
    } catch (_: Exception) {
        JSONObject()
    }
}
