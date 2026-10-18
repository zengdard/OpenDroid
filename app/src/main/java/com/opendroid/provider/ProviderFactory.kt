package com.opendroid.provider

import com.opendroid.data.model.ProviderConfig
import com.opendroid.data.model.ProviderKind

/** Fabrique un [LlmProvider] à partir d'une configuration. */
object ProviderFactory {
    fun create(config: ProviderConfig, onDeviceModelPath: String?): LlmProvider = when (config.kind) {
        ProviderKind.ON_DEVICE -> OnDeviceProvider(
            id = config.id,
            modelPath = onDeviceModelPath ?: config.model
        )
        ProviderKind.OPENAI_COMPATIBLE -> OpenAiCompatibleProvider(config)
        ProviderKind.ANTHROPIC -> AnthropicProvider(config)
        ProviderKind.GOOGLE -> GoogleProvider(config)
    }
}
