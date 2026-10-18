package com.opendroid.data.model

/**
 * Nature du provider. Sert à instancier le bon client HTTP.
 * ON_DEVICE : inférence locale LiteRT-LM (Gemma).
 * OPENAI_COMPATIBLE : toute API /chat/completions (OpenAI, OpenRouter, Groq, Ollama, LM Studio, Mistral, xAI...).
 * ANTHROPIC : API Messages.
 * GOOGLE : API Gemini generateContent.
 */
enum class ProviderKind {
    ON_DEVICE, OPENAI_COMPATIBLE, ANTHROPIC, GOOGLE;

    val isRemote: Boolean get() = this != ON_DEVICE
}

/**
 * Configuration d'un provider distant (clé API, URL de base, modèle).
 * Les clés sont stockées en clair dans les préférences locales de l'app
 * (comme opencode avec son auth.json local).
 */
data class ProviderConfig(
    val id: String,
    val kind: ProviderKind,
    val name: String,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    /** Modèles connus/utilisés récemment. */
    val models: List<String> = emptyList(),
    val enabled: Boolean = true,
    val custom: Boolean = false
) {
    companion object {
        fun onDevice(modelPath: String? = null) = ProviderConfig(
            id = "on-device",
            kind = ProviderKind.ON_DEVICE,
            name = "On-device (Gemma)",
            model = modelPath ?: "gemma-4-E2B-it"
        )

        /** Catalogue de presets proposés dans l'UI. */
        fun presets(): List<ProviderConfig> = listOf(
            ProviderConfig(
                id = "opencode",
                kind = ProviderKind.OPENAI_COMPATIBLE,
                name = "OpenCode Zen",
                baseUrl = "https://opencode.ai/inference/openai/v1",
                models = listOf(
                    "gpt-5.4", "gpt-5.4-pro", "gpt-5.5-pro", "gpt-5.2-codex",
                    "claude-haiku-4-5", "glm-4.6", "qwen3.8-max", "grok-4.7",
                    "kimi-k2.5-free", "glm-4.7-free"
                )
            ),
            ProviderConfig(
                id = "opencode-go",
                kind = ProviderKind.OPENAI_COMPATIBLE,
                name = "OpenCode Go",
                baseUrl = "https://opencode.ai/inference/go/openai/v1",
                // Modèles servis via /chat/completions (endpoints OpenAI-compatibles).
                // (Qwen*, MiniMax M3, Claude Haiku 5.5 → API anthropic ; Grok/GPT Luna/Muse
                //  Spark → /responses ; Gemini → API google : non gérés par ce provider.)
                models = listOf(
                    "deepseek-v4.1-flash", "deepseek-v4-pro", "kimi-k3", "kimi-k2.7-code",
                    "glm-5.3-flash", "glm-5.3", "glm-5.2", "mimo-v2.6-pro", "mimo-v2.6-flash",
                    "mimo-v2.5-pro", "mimo-v2.5", "longcat-2.0", "hy3", "hy4-preview",
                    "space-bunny", "step-5-preview", "longcat-2.5-preview-free", "beam-free"
                )
            ),
            ProviderConfig(
                id = "deepseek", kind = ProviderKind.OPENAI_COMPATIBLE, name = "DeepSeek",
                baseUrl = "https://api.deepseek.com/v1",
                models = listOf("deepseek-chat", "deepseek-reasoner")
            ),
            ProviderConfig(
                id = "openai", kind = ProviderKind.OPENAI_COMPATIBLE, name = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                models = listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1", "o4-mini")
            ),
            ProviderConfig(
                id = "anthropic", kind = ProviderKind.ANTHROPIC, name = "Anthropic",
                baseUrl = "https://api.anthropic.com/v1",
                models = listOf("claude-sonnet-4-20250514", "claude-3-5-haiku-20241022")
            ),
            ProviderConfig(
                id = "google", kind = ProviderKind.GOOGLE, name = "Google Gemini",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                models = listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash")
            ),
            ProviderConfig(
                id = "openrouter", kind = ProviderKind.OPENAI_COMPATIBLE, name = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
                models = listOf("anthropic/claude-sonnet-4", "google/gemini-2.5-pro", "deepseek/deepseek-chat")
            ),
            ProviderConfig(
                id = "groq", kind = ProviderKind.OPENAI_COMPATIBLE, name = "Groq",
                baseUrl = "https://api.groq.com/openai/v1",
                models = listOf("llama-3.3-70b-versatile")
            ),
            ProviderConfig(
                id = "ollama", kind = ProviderKind.OPENAI_COMPATIBLE, name = "Ollama (local)",
                baseUrl = "http://127.0.0.1:11434/v1",
                models = listOf("qwen2.5", "llama3.1", "gemma3")
            ),
            ProviderConfig(
                id = "lmstudio", kind = ProviderKind.OPENAI_COMPATIBLE, name = "LM Studio (local)",
                baseUrl = "http://127.0.0.1:1234/v1", models = listOf("local-model")
            )
        )
    }
}
