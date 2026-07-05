package com.raissummit.orchestrator.tools

import android.content.Context
import android.content.ContentValues
import android.provider.MediaStore
import java.io.File

class SetNoteTool : Tool(
    name = "set_note",
    description = "Enregistrer une note"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        val title = parameters["title"] as? String ?: "Note"
        val content = parameters["content"] as? String ?: return ToolResult.Error("Contenu manquant")
        return try {
            val notesDir = File(context.filesDir, "notes")
            notesDir.mkdirs()
            val file = File(notesDir, "${title.replace(" ", "_")}.txt")
            file.writeText(content)
            ToolResult.SuccessWithData(mapOf("path" to file.absolutePath))
        } catch (e: Exception) {
            ToolResult.Error("Erreur d'enregistrement : ${e.message}")
        }
    }
}
