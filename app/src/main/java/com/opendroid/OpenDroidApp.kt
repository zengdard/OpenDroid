package com.opendroid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.opendroid.data.settings.SettingsStore
import com.opendroid.provider.local.LocalInference
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class OpenDroidApp : Application() {

    @Inject lateinit var settingsStore: SettingsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        LocalInference.init(this)
        preloadOnDevice()
    }

    private fun preloadOnDevice() {
        scope.launch {
            runCatching {
                val path = settingsStore.onDeviceModelPath.first()
                if (!path.isNullOrBlank()) LocalInference.loadModel(path)
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "OpenDroid", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications de l'agent OpenDroid"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "opendroid_agent"
    }
}
