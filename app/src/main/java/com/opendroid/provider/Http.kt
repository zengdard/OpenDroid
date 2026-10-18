package com.opendroid.provider

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** Client HTTP partagé pour tous les providers distants. */
object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    /** Construit une requête POST JSON. */
    fun jsonRequest(url: String, body: String, headers: Map<String, String> = emptyMap()): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(okhttp3.RequestBody.create("application/json".toMediaType(), body))
        headers.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }

    /**
     * Lit un flux SSE ligne par ligne et renvoie les payloads `data:`.
     * Les lignes vides et les commentaires `:` sont ignorés.
     */
    fun sseLines(response: Response): Sequence<String> = sequence {
        val source = response.body?.source() ?: return@sequence
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            when {
                line.isEmpty() -> {}
                line.startsWith("data:") -> yield(line.removePrefix("data:").trim())
                line.startsWith(":") -> {}
                else -> yield(line)
            }
        }
    }

}
