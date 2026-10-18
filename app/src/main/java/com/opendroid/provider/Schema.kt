package com.opendroid.provider

import com.opendroid.data.model.ToolSpec
import org.json.JSONArray
import org.json.JSONObject

/** Conversion des ToolSpec vers les formats JSON Schema des différents providers. */
object Schema {

    /** Format OpenAI / OpenAI-compatible : [{type:function, function:{...}}] */
    fun openAiTools(tools: List<ToolSpec>): JSONArray {
        val arr = JSONArray()
        tools.forEach { tool ->
            val fn = JSONObject()
                .put("name", tool.id)
                .put("description", tool.description)
                .put("parameters", parameters(tool))
            arr.put(JSONObject().put("type", "function").put("function", fn))
        }
        return arr
    }

    /** Format Anthropic : [{name, description, input_schema}] */
    fun anthropicTools(tools: List<ToolSpec>): JSONArray {
        val arr = JSONArray()
        tools.forEach { tool ->
            arr.put(
                JSONObject()
                    .put("name", tool.id)
                    .put("description", tool.description)
                    .put("input_schema", parameters(tool))
            )
        }
        return arr
    }

    /** Format Google Gemini : [{functionDeclarations:[...]}] */
    fun googleTools(tools: List<ToolSpec>): JSONArray {
        val decls = JSONArray()
        tools.forEach { tool ->
            decls.put(
                JSONObject()
                    .put("name", tool.id)
                    .put("description", tool.description)
                    .put("parameters", googleParameters(tool))
            )
        }
        return JSONArray().put(JSONObject().put("functionDeclarations", decls))
    }

    private fun parameters(tool: ToolSpec): JSONObject {
        val props = JSONObject()
        val required = JSONArray()
        tool.parameters.forEach { p ->
            val prop = JSONObject().put("type", p.type).put("description", p.description)
            if (p.enumValues.isNotEmpty()) {
                val en = JSONArray(); p.enumValues.forEach { en.put(it) }
                prop.put("enum", en)
            }
            props.put(p.name, prop)
            if (p.required) required.put(p.name)
        }
        return JSONObject()
            .put("type", "object")
            .put("properties", props)
            .put("required", required)
    }

    private fun googleParameters(tool: ToolSpec): JSONObject {
        // Gemini utilise "type" en majuscules et pas de tableau "required" vide.
        val props = JSONObject()
        val required = JSONArray()
        tool.parameters.forEach { p ->
            val type = p.type.uppercase()
            val prop = JSONObject().put("type", type).put("description", p.description)
            if (p.enumValues.isNotEmpty()) {
                val en = JSONArray(); p.enumValues.forEach { en.put(it) }
                prop.put("enum", en)
            }
            props.put(p.name, prop)
            if (p.required) required.put(p.name)
        }
        val obj = JSONObject().put("type", "OBJECT").put("properties", props)
        if (required.length() > 0) obj.put("required", required)
        return obj
    }
}
