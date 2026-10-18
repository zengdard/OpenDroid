package com.opendroid.provider

import com.opendroid.data.model.ToolCallPart

/**
 * Parser du protocole d'outils XML utilisé par les modèles locaux (Gemma via LiteRT)
 * qui ne disposent pas de tool-calling natif.
 *
 * Format attendu :
 * ```
 * <thinking>…</thinking>
 * <tool_call name="open_app" app_package_name="com.android.settings"></tool_call>
 * <answer>…</answer>
 * ```
 */
object XmlToolParser {

    data class Parsed(
        val thinking: String?,
        val answer: String?,
        val toolCalls: List<ToolCallPart>
    )

    fun parse(text: String): Parsed = Parsed(
        thinking = extractContent(text, "thinking")?.trim()?.ifBlank { null },
        answer = extractContent(text, "answer")?.trim()?.ifBlank { null },
        toolCalls = extractToolCalls(text)
    )

    fun extractThinking(text: String): String? = extractContent(text, "thinking")
    fun extractAnswer(text: String): String? = extractContent(text, "answer")

    /** Retire les balises pour l'affichage "propre". */
    fun clean(text: String): String {
        val parsed = parse(text)
        if (parsed.answer != null) return parsed.answer
        if (parsed.thinking != null || parsed.toolCalls.isNotEmpty()) return ""
        return text.trim()
    }

    private fun extractContent(text: String, tag: String): String? {
        val open = "<$tag>"
        val close = "</$tag>"
        val start = text.indexOf(open)
        if (start == -1) return null
        val from = start + open.length
        val end = text.indexOf(close, from)
        return if (end == -1) text.substring(from).trim() else text.substring(from, end).trim()
    }

    private fun extractToolCalls(text: String): List<ToolCallPart> {
        val regex = Regex("<tool_call\\s+([^>]*?)/?>")
        return regex.findAll(text).mapNotNull { match ->
            val attrs = parseAttributes(match.groupValues[1])
            val name = attrs["name"] ?: return@mapNotNull null
            if (name.isBlank()) return@mapNotNull null
            val params = attrs.filterKeys { it != "name" }
            val json = org.json.JSONObject()
            params.forEach { (k, v) -> json.put(k, coerce(v)) }
            ToolCallPart(name = name, arguments = json.toString())
        }.toList()
    }

    private fun coerce(value: String): Any = when {
        value.equals("true", true) || value.equals("false", true) -> value.toBoolean()
        value.toIntOrNull() != null -> value.toInt()
        value.toDoubleOrNull() != null -> value.toDouble()
        else -> value
    }

    private fun parseAttributes(attrs: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val regex = Regex("""(\w+)\s*=\s*"([^"]*)"""")
        regex.findAll(attrs).forEach { map[it.groupValues[1]] = it.groupValues[2] }
        return map
    }
}
