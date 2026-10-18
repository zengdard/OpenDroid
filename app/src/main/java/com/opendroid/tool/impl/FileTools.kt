package com.opendroid.tool.impl

import com.opendroid.data.model.ToolParameter
import com.opendroid.data.model.ToolSpec
import com.opendroid.tool.Args
import com.opendroid.tool.Tool
import com.opendroid.tool.ToolContext
import com.opendroid.tool.ToolResult
import org.json.JSONObject
import java.io.File

/** Outils de fichiers, calqués sur opencode : read, write, edit, glob, grep, list. */

private fun resolve(path: String): File {
    val f = File(path)
    return if (f.isAbsolute) f else File("/sdcard", path)
}

class ReadTool : Tool {
    override val spec = ToolSpec(
        id = "read",
        description = "Lire le contenu d'un fichier. Paramètres : chemin absolu (ou relatif à /sdcard), et éventuellement offset/limit en lignes.",
        category = "file",
        parameters = listOf(
            ToolParameter("path", "string", "Chemin du fichier"),
            ToolParameter("offset", "integer", "Ligne de départ (1-based)", required = false),
            ToolParameter("limit", "integer", "Nombre de lignes à lire", required = false)
        )
    )
    override val permissionKey = "read"
    override fun permissionInput(args: JSONObject) = Args.str(args, "path")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val path = Args.str(args, "path") ?: return ToolResult.error("Paramètre 'path' requis")
        val file = resolve(path)
        if (!file.exists()) return ToolResult.error("Fichier introuvable : $path")
        if (file.isDirectory) return ToolResult.error("$path est un répertoire (utilisez `list`)")
        return try {
            val lines = file.readLines()
            val offset = (Args.int(args, "offset", 1) - 1).coerceAtLeast(0)
            val limit = Args.int(args, "limit", 2000).coerceAtLeast(1)
            val slice = lines.drop(offset).take(limit)
            val numbered = slice.mapIndexed { i, l -> "%6d| %s".format(offset + i + 1, l) }
            ToolResult.ok(numbered.joinToString("\n"))
        } catch (e: Exception) {
            ToolResult.error("Lecture impossible : ${e.message}")
        }
    }
}

class WriteTool : Tool {
    override val spec = ToolSpec(
        id = "write",
        description = "Créer ou écraser un fichier avec le contenu fourni.",
        category = "file",
        parameters = listOf(
            ToolParameter("path", "string", "Chemin du fichier"),
            ToolParameter("content", "string", "Contenu à écrire")
        ),
        dangerousByDefault = true
    )
    override val permissionKey = "edit"
    override fun permissionInput(args: JSONObject) = Args.str(args, "path")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val path = Args.str(args, "path") ?: return ToolResult.error("Paramètre 'path' requis")
        val content = args.optString("content", "")
        return try {
            val file = resolve(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            ToolResult.ok("Écrit ${content.length} caractères dans $path")
        } catch (e: Exception) {
            ToolResult.error("Écriture impossible : ${e.message}")
        }
    }
}

class EditTool : Tool {
    override val spec = ToolSpec(
        id = "edit",
        description = "Remplacer une chaîne exacte par une autre dans un fichier.",
        category = "file",
        parameters = listOf(
            ToolParameter("path", "string", "Chemin du fichier"),
            ToolParameter("oldString", "string", "Texte exact à remplacer"),
            ToolParameter("newString", "string", "Texte de remplacement"),
            ToolParameter("replaceAll", "boolean", "Remplacer toutes les occurrences", required = false)
        ),
        dangerousByDefault = true
    )
    override val permissionKey = "edit"
    override fun permissionInput(args: JSONObject) = Args.str(args, "path")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val path = Args.str(args, "path") ?: return ToolResult.error("Paramètre 'path' requis")
        val old = args.optString("oldString", "")
        val new = args.optString("newString", "")
        if (old.isEmpty()) return ToolResult.error("Paramètre 'oldString' requis")
        val file = resolve(path)
        if (!file.exists()) return ToolResult.error("Fichier introuvable : $path")
        return try {
            val content = file.readText()
            val count = content.windowed(old.length).count { it == old }
            if (count == 0) return ToolResult.error("Chaîne introuvable dans $path")
            val replaceAll = Args.bool(args, "replaceAll")
            if (count > 1 && !replaceAll) {
                return ToolResult.error("$count occurrences trouvées ; précisez replaceAll=true ou un contexte plus large")
            }
            val updated = if (replaceAll) content.replace(old, new) else content.replaceFirst(old, new)
            file.writeText(updated)
            ToolResult.ok("Modifié $path (${if (replaceAll) count else 1} remplacement(s))")
        } catch (e: Exception) {
            ToolResult.error("Édition impossible : ${e.message}")
        }
    }
}

class GlobTool : Tool {
    override val spec = ToolSpec(
        id = "glob",
        description = "Trouver des fichiers par motif glob (ex: **/*.txt). Renvoie les chemins correspondants.",
        category = "file",
        parameters = listOf(
            ToolParameter("pattern", "string", "Motif glob, ex: **/*.md"),
            ToolParameter("path", "string", "Répertoire de base (défaut /sdcard)", required = false)
        )
    )
    override val permissionKey = "glob"
    override fun permissionInput(args: JSONObject) = Args.str(args, "path") ?: Args.str(args, "pattern")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val pattern = Args.str(args, "pattern") ?: return ToolResult.error("Paramètre 'pattern' requis")
        val base = resolve(Args.str(args, "path", "/sdcard"))
        if (!base.isDirectory) return ToolResult.error("Répertoire introuvable : ${base.path}")
        val regex = globToRegex(pattern)
        return try {
            val matches = base.walkTopDown()
                .maxDepth(6)
                .filter { it.isFile && regex.matches(it.relativeTo(base).path) }
                .take(200)
                .map { it.path }
                .toList()
            if (matches.isEmpty()) ToolResult.ok("Aucun fichier ne correspond à $pattern")
            else ToolResult.ok("${matches.size} fichier(s):\n" + matches.joinToString("\n"))
        } catch (e: Exception) {
            ToolResult.error("Recherche impossible : ${e.message}")
        }
    }

    private fun globToRegex(pattern: String): Regex {
        val sb = StringBuilder("^")
        for (c in pattern) when (c) {
            '*' -> sb.append(".*")
            '?' -> sb.append('.')
            '.', '(', ')', '+', '|', '^', '$', '@', '%' -> sb.append("\\").append(c)
            else -> sb.append(c)
        }
        sb.append('$')
        return Regex(sb.toString())
    }
}

class GrepTool : Tool {
    override val spec = ToolSpec(
        id = "grep",
        description = "Rechercher une expression régulière dans le contenu des fichiers.",
        category = "file",
        parameters = listOf(
            ToolParameter("pattern", "string", "Expression régulière"),
            ToolParameter("path", "string", "Répertoire de base (défaut /sdcard)", required = false),
            ToolParameter("include", "string", "Filtre glob de fichiers, ex: *.txt", required = false)
        )
    )
    override val permissionKey = "grep"
    override fun permissionInput(args: JSONObject) = Args.str(args, "pattern")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val pattern = Args.str(args, "pattern") ?: return ToolResult.error("Paramètre 'pattern' requis")
        val base = resolve(Args.str(args, "path", "/sdcard"))
        val include = Args.str(args, "include")
        val regex = try { Regex(pattern) } catch (e: Exception) { return ToolResult.error("Motif invalide: ${e.message}") }
        val includeRegex = include?.let { Regex("^" + it.replace(".", "\\.").replace("*", ".*") + "$") }
        return try {
            val results = mutableListOf<String>()
            base.walkTopDown().maxDepth(5).filter { it.isFile && it.length() < 2_000_000 }
                .forEach { file ->
                    if (results.size >= 200) return@forEach
                    if (includeRegex != null && !includeRegex.matches(file.name)) return@forEach
                    try {
                        file.useLines { lines ->
                            lines.forEachIndexed { i, line ->
                                if (results.size < 200 && regex.containsMatchIn(line)) {
                                    results.add("${file.path}:${i + 1}: ${line.take(200)}")
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            if (results.isEmpty()) ToolResult.ok("Aucun résultat pour /$pattern/")
            else ToolResult.ok(results.joinToString("\n"))
        } catch (e: Exception) {
            ToolResult.error("Recherche impossible : ${e.message}")
        }
    }
}

class ListTool : Tool {
    override val spec = ToolSpec(
        id = "list",
        description = "Lister le contenu d'un répertoire.",
        category = "file",
        parameters = listOf(
            ToolParameter("path", "string", "Chemin du répertoire (défaut /sdcard)", required = false)
        )
    )
    override val permissionKey = "list"
    override fun permissionInput(args: JSONObject) = Args.str(args, "path")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val dir = resolve(Args.str(args, "path", "/sdcard"))
        if (!dir.isDirectory) return ToolResult.error("Répertoire introuvable : ${dir.path}")
        val entries = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        if (entries.isEmpty()) return ToolResult.ok("(répertoire vide)")
        return ToolResult.ok(entries.joinToString("\n") { (if (it.isDirectory) "d " else "- ") + it.name })
    }
}
