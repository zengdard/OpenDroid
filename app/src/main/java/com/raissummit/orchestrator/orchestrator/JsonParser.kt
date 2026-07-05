package com.raissummit.orchestrator.orchestrator

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.raissummit.orchestrator.data.models.OrchestratorResponse

class JsonParser {
    private val gson = Gson()

    fun parseResponse(json: String): Result<OrchestratorResponse> {
        return try {
            val cleanJson = extractJsonBlock(json)
            val response = gson.fromJson(cleanJson, OrchestratorResponse::class.java)
            if (response.tool_calls == null && response.final_answer == null) {
                Result.failure(IllegalStateException("Missing both tool_calls and final_answer"))
            } else {
                Result.success(response)
            }
        } catch (e: JsonSyntaxException) {
            Result.failure(e)
        }
    }

    private fun extractJsonBlock(raw: String): String {
        val jsonStart = raw.indexOf("{")
        val jsonEnd = raw.lastIndexOf("}")
        if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
            return raw
        }
        return raw.substring(jsonStart, jsonEnd + 1)
    }
}
