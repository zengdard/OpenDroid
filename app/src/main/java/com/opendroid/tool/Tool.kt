package com.opendroid.tool

import android.content.Context
import com.opendroid.accessibility.AccessibilityBridge
import com.opendroid.data.model.Todo
import com.opendroid.data.model.ToolSpec
import org.json.JSONObject

/** Résultat d'exécution d'un outil, renvoyé au modèle. */
data class ToolResult(
    val output: String,
    val isError: Boolean = false,
    val data: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun ok(output: String, data: Map<String, Any?> = emptyMap()) = ToolResult(output, false, data)
        fun error(message: String) = ToolResult(message, true)
    }
}

/** Demande de question à l'utilisateur (outil `question`). */
data class UserQuestion(
    val header: String,
    val question: String,
    val options: List<String> = emptyList(),
    val multiple: Boolean = false
)

/**
 * Contexte fourni aux outils : accès à l'application, au service d'accessibilité,
 * à la boucle d'agents (sous-agents) et à l'utilisateur (questions, todos).
 */
class ToolContext(
    val appContext: Context,
    val accessibility: AccessibilityBridge,
    val askUser: suspend (UserQuestion) -> String,
    val runSubagent: suspend (agentId: String, prompt: String) -> String,
    val updateTodos: suspend (List<Todo>) -> Unit,
    val readTodos: suspend () -> List<Todo>
)

/**
 * Un outil exposé au modèle.
 * [spec] décrit l'outil ; [permissionKey] est la clé de permission (souvent = id) ;
 * [permissionInput] extrait la valeur comparée aux motifs (commande, url, chemin…).
 */
interface Tool {
    val spec: ToolSpec
    val permissionKey: String get() = spec.id
    /** Valeur comparée aux motifs de permission. null => le nom de l'outil est utilisé. */
    fun permissionInput(args: JSONObject): String? = null
    suspend fun execute(context: ToolContext, args: JSONObject): ToolResult
}

/** Helpers de lecture d'arguments JSON. */
object Args {
    fun str(args: JSONObject, key: String): String? = args.optString(key, "").takeIf { it.isNotBlank() }
    fun str(args: JSONObject, key: String, default: String): String = args.optString(key, default).ifBlank { default }
    fun int(args: JSONObject, key: String): Int? = if (args.has(key)) args.optInt(key) else null
    fun int(args: JSONObject, key: String, default: Int): Int = if (args.has(key)) args.optInt(key, default) else default
    fun bool(args: JSONObject, key: String, default: Boolean = false): Boolean = args.optBoolean(key, default)
}
