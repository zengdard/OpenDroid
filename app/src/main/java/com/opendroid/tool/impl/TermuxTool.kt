package com.opendroid.tool.impl

import com.opendroid.data.model.ToolParameter
import com.opendroid.data.model.ToolSpec
import com.opendroid.termux.TermuxBridge
import com.opendroid.tool.Args
import com.opendroid.tool.Tool
import com.opendroid.tool.ToolContext
import com.opendroid.tool.ToolResult
import org.json.JSONObject

/**
 * Outil `termux` : exécute une commande dans l'environnement Termux du téléphone
 * (bash, `pkg`/`apt`, Python, git… installés par l'utilisateur).
 */
class TermuxTool : Tool {
    override val spec = ToolSpec(
        id = "termux",
        description = "Exécuter une commande dans l'environnement Termux (bash, pkg/apt, python, git…). " +
            "Nécessite Termux installé, allow-external-apps=true et la permission RUN_COMMAND.",
        category = "system",
        parameters = listOf(
            ToolParameter("command", "string", "Commande bash à exécuter dans Termux"),
            ToolParameter("timeout", "integer", "Timeout en secondes (défaut 120)", required = false)
        ),
        dangerousByDefault = true
    )
    override val permissionKey = "termux"
    override fun permissionInput(args: JSONObject) = Args.str(args, "command")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val command = Args.str(args, "command") ?: return ToolResult.error("Paramètre 'command' requis")
        if (!TermuxBridge.isInstalled(context.appContext)) {
            return ToolResult.error(
                "Termux n'est pas installé. Installe Termux (F-Droid), puis active " +
                    "allow-external-apps=true et accorde la permission RUN_COMMAND à OpenDroid."
            )
        }
        val timeout = Args.int(args, "timeout", 120).toLong()
        val result = TermuxBridge.run(context.appContext, command, timeout)
        if (result.timedOut) return ToolResult.error("Commande Termux interrompue (timeout ${timeout}s)")

        val output = buildString {
            if (result.stdout.isNotBlank()) append(result.stdout.trim().take(6000))
            if (result.stderr.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append("[stderr]\n").append(result.stderr.trim().take(2000))
            }
            if (isEmpty()) append("(aucune sortie)")
        }
        return ToolResult(
            output = "exit=${result.exitCode}\n$output",
            isError = !result.ok,
            data = mapOf("exit_code" to result.exitCode)
        )
    }
}
