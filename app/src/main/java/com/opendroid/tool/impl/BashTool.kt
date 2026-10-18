package com.opendroid.tool.impl

import com.opendroid.data.model.ToolParameter
import com.opendroid.data.model.ToolSpec
import com.opendroid.tool.Args
import com.opendroid.tool.Tool
import com.opendroid.tool.ToolContext
import com.opendroid.tool.ToolResult
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** Outil `bash` : exécute une commande shell (équivalent opencode). */
class BashTool : Tool {
    override val spec = ToolSpec(
        id = "bash",
        description = "Exécuter une commande shell. Utile pour lire/écrire des fichiers, " +
            "lister des répertoires, diagnostiquer le système, ou toute commande disponible.",
        category = "system",
        parameters = listOf(
            ToolParameter("command", "string", "Commande shell à exécuter"),
            ToolParameter("timeout", "integer", "Timeout en secondes (défaut 15)", required = false)
        ),
        dangerousByDefault = true
    )
    override val permissionKey = "bash"
    override fun permissionInput(args: JSONObject) = Args.str(args, "command")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val command = Args.str(args, "command") ?: return ToolResult.error("Paramètre 'command' requis")
        val timeout = Args.int(args, "timeout", 15).toLong()
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val finished = process.waitFor(timeout, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ToolResult.error("Commande interrompue (timeout ${timeout}s)")
            }
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
            val exit = process.exitValue()
            val out = buildString {
                if (stdout.isNotBlank()) append(stdout.take(4000))
                if (stderr.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append("[stderr]\n").append(stderr.take(1000))
                }
                if (isEmpty()) append("(aucune sortie)")
            }
            ToolResult(
                output = "exit=$exit\n$out",
                isError = exit != 0,
                data = mapOf("exit_code" to exit)
            )
        } catch (e: Exception) {
            ToolResult.error("Erreur d'exécution : ${e.message}")
        }
    }
}
