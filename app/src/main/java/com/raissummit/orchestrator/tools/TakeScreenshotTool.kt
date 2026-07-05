package com.raissummit.orchestrator.tools

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

class TakeScreenshotTool : Tool(
    name = "take_screenshot",
    description = "Prendre une capture d'écran"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        return ToolResult.Error("La capture d'écran nécessite MediaProjection. Utilisez le service d'accessibilité.")
    }

    fun saveScreenshot(bitmap: Bitmap, context: Context): ToolResult {
        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val file = File(dir, "screenshot_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            ToolResult.SuccessWithData(mapOf("uri" to file.toURI().toString()))
        } catch (e: Exception) {
            ToolResult.Error("Erreur de capture : ${e.message}")
        }
    }
}
