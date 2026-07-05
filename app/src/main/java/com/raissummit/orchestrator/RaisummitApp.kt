package com.raissummit.orchestrator

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.raissummit.orchestrator.data.repository.SettingsRepository
import com.raissummit.orchestrator.ui.chat.LiteRTInference
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class RaisummitApp : Application() {

    companion object {
        const val CHANNEL_ID = "orchestrator_channel"
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        LiteRTInference.init(this)
        preloadModel()
    }

    private fun preloadModel() {
        appScope.launch {
            try {
                val repo = SettingsRepository(this@RaisummitApp)
                val path = repo.modelPath.first()
                if (!path.isNullOrBlank()) {
                    LiteRTInference.loadModel(path)
                }
            } catch (_: Exception) { }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RAIS Orchestrator",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications de l'assistant orchestrateur"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
