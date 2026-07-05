package com.raissummit.orchestrator.ui.chat

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object LiteRTInference {
    private const val TAG = "LiteRTInference"
    private var engine: Engine? = null
    private var currentModelPath: String? = null
    private var cacheDir: String? = null
    private val mutex = Mutex()

    fun init(context: Context) {
        cacheDir = context.cacheDir.absolutePath
    }

    suspend fun loadModel(modelPath: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (currentModelPath == modelPath && engine != null) return@withContext Result.success(Unit)
                engine?.close()
                val eng = Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cacheDir))
                eng.initialize()
                engine = eng
                currentModelPath = modelPath
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    fun generateStreaming(prompt: String, modelPath: String): Flow<String> = callbackFlow {
        val eng = mutex.withLock { engine }
        if (eng == null || currentModelPath != modelPath) {
            send("__LOADING__"); close(); return@callbackFlow
        }

        val conversation = try { eng.createConversation() } catch (e: Exception) {
            Log.e(TAG, "createConversation failed", e)
            send("""{"thinking": "Erreur", "tool_calls": [], "final_answer": "Erreur init conversation"}""")
            close(); return@callbackFlow
        }

        val scope = this
        launch(Dispatchers.IO) {
            try {
                conversation.sendMessageAsync(prompt).collect { message ->
                    val text = message.toString()
                    if (text.isNotBlank()) {
                        scope.send(text)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Streaming error: ${e.message}", e)
            } finally {
                try { conversation.close() } catch (_: Exception) {}
                close()
            }
        }

        awaitClose {
            try { conversation.close() } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    fun close() {
        kotlinx.coroutines.runBlocking {
            mutex.withLock {
                engine?.close()
                engine = null
                currentModelPath = null
            }
        }
    }
}
