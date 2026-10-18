package com.opendroid.tool.impl

import com.opendroid.data.model.ToolParameter
import com.opendroid.data.model.ToolSpec
import com.opendroid.tool.Args
import com.opendroid.tool.Tool
import com.opendroid.tool.ToolContext
import com.opendroid.tool.ToolResult
import org.json.JSONObject

/** Outil `bash` : exécute une commande shell (équivalent opencode). */
class BashTool : Tool {
    override val spec = ToolSpec(
        id = "bash",
        description = "Exécuter une commande shell. Utile pour lire/écrire des fichiers, " +
            "lister des répertoires, diagnostiquer le système, ou toute commande disponible. " +
            "Avec root=true, la commande passe par `su -c` (appareil rooté uniquement).",
        category = "system",
        parameters = listOf(
            ToolParameter("command", "string", "Commande shell à exécuter"),
            ToolParameter("timeout", "integer", "Timeout en secondes (défaut 15)", required = false),
            ToolParameter("root", "boolean", "Exécuter via su (root)", required = false)
        ),
        dangerousByDefault = true
    )
    override val permissionKey = "bash"
    override fun permissionInput(args: JSONObject) = Args.str(args, "command")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val command = Args.str(args, "command") ?: return ToolResult.error("Paramètre 'command' requis")
        val timeout = Args.int(args, "timeout", 15).toLong()
        val root = Args.bool(args, "root", false)

        val result = Shell.run(command, timeout, root)
        if (result.timedOut) {
            return ToolResult.error("Commande interrompue (timeout ${timeout}s)")
        }
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
