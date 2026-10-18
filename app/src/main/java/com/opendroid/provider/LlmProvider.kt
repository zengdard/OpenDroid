package com.opendroid.provider

import com.opendroid.data.model.Message
import com.opendroid.data.model.ToolSpec
import kotlinx.coroutines.flow.Flow

/** Requête canonique envoyée à un provider. */
data class ChatRequest(
    val model: String,
    val system: String,
    val messages: List<Message>,
    val tools: List<ToolSpec>,
    val temperature: Float = 0.3f,
    val maxTokens: Int = 8192,
    /**
     * Identifiant stable de conversation, transmis aux backends qui l'exigent
     * (ex. OpenCode Go : header `x-opencode-session`).
     */
    val sessionId: String = ""
)

/** Événements émis par un provider pendant la génération. */
sealed interface ProviderEvent {
    data class TextDelta(val text: String) : ProviderEvent
    data class ThinkingDelta(val text: String) : ProviderEvent
    data class Completed(
        val text: String,
        val thinking: String?,
        val toolCalls: List<com.opendroid.data.model.ToolCallPart>,
        val finishReason: String
    ) : ProviderEvent
    data class Error(val message: String) : ProviderEvent
}

/**
 * Un backend LLM. Implémenté par :
 *  - OnDeviceProvider (LiteRT-LM / Gemma, local)
 *  - OpenAiCompatibleProvider (OpenAI, OpenRouter, Groq, Ollama, LM Studio, ...)
 *  - AnthropicProvider
 *  - GoogleProvider
 */
interface LlmProvider {
    val id: String
    val kind: com.opendroid.data.model.ProviderKind

    /** Vrai si le provider sait utiliser le tool-calling natif. */
    val supportsNativeTools: Boolean

    fun stream(request: ChatRequest): Flow<ProviderEvent>
}
