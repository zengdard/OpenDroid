package com.opendroid.tool.impl

import com.opendroid.data.model.Todo
import com.opendroid.data.model.TodoStatus
import com.opendroid.data.model.ToolParameter
import com.opendroid.data.model.ToolSpec
import com.opendroid.tool.Args
import com.opendroid.tool.Tool
import com.opendroid.tool.ToolContext
import com.opendroid.tool.ToolResult
import com.opendroid.tool.UserQuestion
import org.json.JSONArray
import org.json.JSONObject

/** Outil `task` : délègue une tâche à un sous-agent (façon opencode). */
class TaskTool : Tool {
    override val spec = ToolSpec(
        id = "task",
        description = "Lancer un sous-agent pour une tâche spécialisée. Types disponibles : " +
            "general (polyvalent), explore (exploration lecture seule), scout (recherche web). " +
            "Renvoie le résultat du sous-agent.",
        category = "agent",
        parameters = listOf(
            ToolParameter("description", "string", "Description courte de la tâche (3-5 mots)"),
            ToolParameter("prompt", "string", "Instruction détaillée à confier au sous-agent"),
            ToolParameter("subagent_type", "string", "Type de sous-agent", enumValues = listOf("general", "explore", "scout"))
        )
    )
    override val permissionKey = "task"
    override fun permissionInput(args: JSONObject) = Args.str(args, "subagent_type")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val prompt = Args.str(args, "prompt") ?: return ToolResult.error("Paramètre 'prompt' requis")
        val type = Args.str(args, "subagent_type", "general")
        return try {
            val result = context.runSubagent(type, prompt)
            ToolResult.ok(result)
        } catch (e: Exception) {
            ToolResult.error("Échec du sous-agent : ${e.message}")
        }
    }
}

/** Outil `question` : pose une question à l'utilisateur pendant l'exécution. */
class QuestionTool : Tool {
    override val spec = ToolSpec(
        id = "question",
        description = "Poser une question à l'utilisateur pendant l'exécution, avec des options proposées.",
        category = "agent",
        parameters = listOf(
            ToolParameter("header", "string", "En-tête court (max 30 caractères)"),
            ToolParameter("question", "string", "Texte de la question"),
            ToolParameter("options", "string", "Options séparées par des retours à la ligne", required = false),
            ToolParameter("multiple", "boolean", "Autoriser plusieurs choix", required = false)
        )
    )
    override val permissionKey = "question"

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val question = Args.str(args, "question") ?: return ToolResult.error("Paramètre 'question' requis")
        val header = Args.str(args, "header", "Question")
        val options = args.optString("options", "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val multiple = Args.bool(args, "multiple")
        val answer = context.askUser(UserQuestion(header, question, options, multiple))
        return ToolResult.ok(answer)
    }
}

/** Outils `todowrite` / `todoread` : suivi de tâches, façon opencode. */
class TodoWriteTool : Tool {
    override val spec = ToolSpec(
        id = "todowrite",
        description = "Créer ou mettre à jour la liste de tâches de la session. Fournit tous les éléments (remplace la liste).",
        category = "agent",
        parameters = listOf(
            ToolParameter("todos", "string", "Tableau JSON: [{\"content\":\"...\",\"status\":\"pending|in_progress|done\"}]")
        )
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val raw = args.optString("todos", "")
        val todos = mutableListOf<Todo>()
        try {
            val arr = if (raw.trimStart().startsWith("[")) JSONArray(raw) else args.optJSONArray("todos")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val status = when (o.optString("status").lowercase()) {
                        "in_progress" -> TodoStatus.IN_PROGRESS
                        "done", "completed" -> TodoStatus.DONE
                        else -> TodoStatus.PENDING
                    }
                    todos.add(Todo(o.optString("content"), status))
                }
            }
        } catch (_: Exception) {}
        context.updateTodos(todos)
        return ToolResult.ok("Liste de tâches mise à jour (${todos.size} élément(s))")
    }
}

class TodoReadTool : Tool {
    override val spec = ToolSpec(
        id = "todoread",
        description = "Lire la liste de tâches courante de la session.",
        category = "agent"
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val todos = context.readTodos()
        if (todos.isEmpty()) return ToolResult.ok("(aucune tâche)")
        return ToolResult.ok(todos.joinToString("\n") { "- [${it.status.name.lowercase()}] ${it.content}" })
    }
}
