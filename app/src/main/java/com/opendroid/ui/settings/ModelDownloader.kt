package com.opendroid.ui.settings

import android.content.Context
import android.util.Log
import com.opendroid.provider.Http
import com.opendroid.provider.local.LocalInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/** Téléchargement / suppression du modèle on-device (Gemma LiteRT-LM). */
object ModelDownloader {

    private const val URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    private const val FILE_NAME = "gemma-4-E2B-it.litertlm"

    private fun modelFile(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }.resolve(FILE_NAME)

    suspend fun downloadGemma(context: Context, onProgress: (Int) -> Unit): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val target = modelFile(context)
                if (target.exists()) target.delete()
                val request = Request.Builder().url(URL).header("User-Agent", "OpenDroid/1.0").build()
                Http.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}"))
                    val body = response.body ?: return@withContext Result.failure(Exception("Réponse vide"))
                    val length = body.contentLength()
                    body.byteStream().use { input ->
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            var total = 0L
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                total += read
                                if (length > 0) onProgress(((total * 100) / length).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
                LocalInference.loadModel(target.absolutePath)
                Result.success(target.absolutePath)
            } catch (e: Exception) {
                Log.e("ModelDownloader", "download failed", e)
                Result.failure(e)
            }
        }

    suspend fun delete(context: Context) = withContext(Dispatchers.IO) {
        LocalInference.close()
        modelFile(context).delete()
        Unit
    }
}
