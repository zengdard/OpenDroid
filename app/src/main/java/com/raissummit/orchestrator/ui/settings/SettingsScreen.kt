package com.raissummit.orchestrator.ui.settings

import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raissummit.orchestrator.ui.chat.ChatViewModel
import com.raissummit.orchestrator.ui.chat.LiteRTInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class ModelDownloadState {
    IDLE, DOWNLOADING, DONE, ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isModelDownloaded by viewModel.settingsRepository.isModelDownloaded.collectAsStateWithLifecycle(
        initialValue = false
    )
    var downloadState by remember { mutableStateOf(ModelDownloadState.IDLE) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Model section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Modèle Gemma 4 E2B",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Taille : 2.6 Go",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isModelDownloaded) Icons.Default.CheckCircle
                            else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isModelDownloaded) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isModelDownloaded) "Modèle prêt" else "Non téléchargé",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    if (downloadState == ModelDownloadState.DOWNLOADING) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Téléchargement… $downloadProgress%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (downloadState == ModelDownloadState.ERROR && errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Erreur : $errorMessage",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!isModelDownloaded || downloadState == ModelDownloadState.ERROR) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        downloadState = ModelDownloadState.DOWNLOADING
                                        errorMessage = null
                                        val result = downloadGemmaModel(context) { progress ->
                                            downloadProgress = progress
                                        }
                                        if (result.isSuccess) {
                                            viewModel.settingsRepository.setModelDownloaded(
                                                result.getOrThrow()
                                            )
                                            downloadState = ModelDownloadState.DONE
                                        } else {
                                            downloadState = ModelDownloadState.ERROR
                                            errorMessage = result.exceptionOrNull()?.message
                                                ?: "Erreur inconnue"
                                        }
                                    }
                                },
                                enabled = downloadState != ModelDownloadState.DOWNLOADING
                            ) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Télécharger")
                            }
                        }

                        if (isModelDownloaded) {
                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Supprimer")
                            }
                        }
                    }
                }
            }

            // Conversations section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Conversations",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.resetConversation() },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text("Nouvelle conversation", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Accessibility section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Service d'accessibilité",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nécessaire pour le tap, la saisie et les captures d'écran.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            )
                        }
                    ) {
                        Text("Ouvrir les paramètres d'accessibilité")
                    }
                }
            }

            // About section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "À propos",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "RAIS Orchestrator v1.0.0",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Orchestrateur IA on-device avec Gemma 4 E2B",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "RAIS Orchestrator — IA on-device, 100% privé",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Supprimer le modèle") },
            text = { Text("Le modèle sera supprimé. Vous devrez le retélécharger pour utiliser l'assistant.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            deleteModel(context)
                            viewModel.settingsRepository.clearModel()
                            LiteRTInference.close()
                            showDeleteConfirm = false
                            downloadState = ModelDownloadState.IDLE
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

private suspend fun deleteModel(context: android.content.Context) {
    withContext(Dispatchers.IO) {
        val modelDir = File(context.filesDir, "models")
        val modelFile = File(modelDir, "gemma-4-E2B-it.litertlm")
        if (modelFile.exists()) {
            modelFile.delete()
            Log.i("SettingsScreen", "Model deleted: ${modelFile.absolutePath}")
        }
    }
}

private suspend fun downloadGemmaModel(
    context: android.content.Context,
    onProgress: (Int) -> Unit
): Result<String> {
    return withContext(Dispatchers.IO) {
        try {
            val modelUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
            val modelDir = File(context.filesDir, "models")
            modelDir.mkdirs()
            val modelFile = File(modelDir, "gemma-4-E2B-it.litertlm")

            if (modelFile.exists()) {
                modelFile.delete()
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.MINUTES)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val request = Request.Builder()
                .url(modelUrl)
                .header("User-Agent", "RAISOrchestrator/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()?.take(200) ?: "No body"
                return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty body"))
            val contentLength = body.contentLength()

            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(modelFile)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (contentLength > 0) {
                    val progress = ((totalBytesRead * 100) / contentLength).toInt()
                    onProgress(progress.coerceIn(0, 100))
                }
            }

            outputStream.close()
            inputStream.close()

            LiteRTInference.loadModel(modelFile.absolutePath)
            Result.success(modelFile.absolutePath)
        } catch (e: Exception) {
            Log.e("SettingsScreen", "Download error", e)
            Result.failure(e)
        }
    }
}
