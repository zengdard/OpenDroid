package com.opendroid.termux

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.opendroid.tool.impl.ShellResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Pont vers l'environnement **Termux** via l'intent officiel `RUN_COMMAND`.
 *
 * Prérequis côté Termux :
 *  - Termux >= 0.109 installé ;
 *  - `allow-external-apps=true` dans `~/.termux/termux.properties` ;
 *  - permission `com.termux.permission.RUN_COMMAND` accordée à OpenDroid.
 *
 * Les commandes tournent en arrière-plan (`EXTRA_BACKGROUND=true`) afin de
 * récupérer stdout, stderr et le code de sortie via un PendingIntent.
 */
object TermuxBridge {

    private const val PKG = "com.termux"
    private const val SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION = "com.termux.RUN_COMMAND"

    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    private const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    private const val EXTRA_RESULT_BUNDLE = "result"
    private const val KEY_STDOUT = "stdout"
    private const val KEY_STDERR = "stderr"
    private const val KEY_EXIT = "exitCode"
    private const val KEY_ERR = "err"
    private const val KEY_ERRMSG = "errmsg"

    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"
    private const val HOME = "/data/data/com.termux/files/home"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PKG, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Exception) {
        false
    }

    suspend fun run(context: Context, command: String, timeoutSec: Long = 120): ShellResult =
        withContext(Dispatchers.IO) {
            if (!isInstalled(context)) {
                return@withContext ShellResult(-1, "", "Termux n'est pas installé sur cet appareil.")
            }

            val action = "com.opendroid.termux.${UUID.randomUUID()}"
            val deferred = CompletableDeferred<Bundle?>()
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    deferred.complete(intent?.getBundleExtra(EXTRA_RESULT_BUNDLE))
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            try {
                val resultIntent = Intent(action).setPackage(context.packageName)
                val flags = PendingIntent.FLAG_ONE_SHOT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, action.hashCode(), resultIntent, flags)

                val intent = Intent().apply {
                    setClassName(PKG, SERVICE)
                    this.action = ACTION
                    putExtra(EXTRA_COMMAND_PATH, BASH)
                    putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", command))
                    putExtra(EXTRA_WORKDIR, HOME)
                    putExtra(EXTRA_BACKGROUND, true)
                    putExtra(EXTRA_SESSION_ACTION, "0")
                    putExtra(EXTRA_COMMAND_LABEL, "OpenDroid")
                    putExtra(EXTRA_PENDING_INTENT, pending)
                }
                context.startService(intent)

                val bundle = withTimeoutOrNull(timeoutSec * 1000) { deferred.await() }
                if (bundle == null) {
                    ShellResult(-1, "", "Timeout Termux après ${timeoutSec}s (Termux est-il autorisé ?)")
                } else {
                    val stdout = bundle.getString(KEY_STDOUT, "").orEmpty()
                    val stderr = bundle.getString(KEY_STDERR, "").orEmpty()
                    val exitCode = bundle.getInt(KEY_EXIT, -1)
                    val errCode = bundle.getInt(KEY_ERR, 0)
                    val errMsg = bundle.getString(KEY_ERRMSG, "").orEmpty()
                    val prefix = if (errCode != 0 && errMsg.isNotBlank()) "[termux err $errCode] $errMsg\n" else ""
                    ShellResult(exitCode, stdout, prefix + stderr)
                }
            } catch (e: Exception) {
                ShellResult(-1, "", "Termux : ${e.message}")
            } finally {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
            }
        }
}
