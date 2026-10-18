package com.opendroid.tool.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false
) {
    val ok: Boolean get() = exitCode == 0 && !timedOut
}

/**
 * Exécution de commandes shell partagée entre l'outil `bash` et l'écran Terminal.
 * Par défaut, les commandes tournent avec l'UID de l'application ; l'option
 * [root] passe par `su -c` (nécessite un appareil rooté et l'autorisation su).
 */
object Shell {

    fun hasRoot(): Boolean = try {
        val process = ProcessBuilder("sh", "-c", "command -v su").start()
        val finished = process.waitFor(2, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            false
        } else {
            process.exitValue() == 0
        }
    } catch (_: Exception) {
        false
    }

    suspend fun run(command: String, timeoutSec: Long = 30, root: Boolean = false): ShellResult =
        withContext(Dispatchers.IO) {
            try {
                val builder = if (root) ProcessBuilder("su", "-c", command)
                else ProcessBuilder("sh", "-c", command)
                val process = builder.start()

                val stdout = StringBuilder()
                val stderr = StringBuilder()
                val outReader = Thread {
                    try {
                        process.inputStream.bufferedReader().use { stdout.append(it.readText()) }
                    } catch (_: Exception) {
                    }
                }
                val errReader = Thread {
                    try {
                        process.errorStream.bufferedReader().use { stderr.append(it.readText()) }
                    } catch (_: Exception) {
                    }
                }
                outReader.start()
                errReader.start()

                val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    outReader.join(800)
                    errReader.join(800)
                    return@withContext ShellResult(-1, stdout.toString(), stderr.toString(), timedOut = true)
                }
                outReader.join(2000)
                errReader.join(2000)
                ShellResult(process.exitValue(), stdout.toString(), stderr.toString())
            } catch (e: Exception) {
                ShellResult(-1, "", e.message ?: "Erreur d'exécution", timedOut = false)
            }
        }
}
