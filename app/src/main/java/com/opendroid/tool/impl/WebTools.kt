package com.opendroid.tool.impl

import com.opendroid.data.model.ToolParameter
import com.opendroid.data.model.ToolSpec
import com.opendroid.provider.Http
import com.opendroid.tool.Args
import com.opendroid.tool.Tool
import com.opendroid.tool.ToolContext
import com.opendroid.tool.ToolResult
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

/** Outil `webfetch` : récupère et nettoie le contenu d'une page web. */
class WebFetchTool : Tool {
    override val spec = ToolSpec(
        id = "webfetch",
        description = "Récupérer le contenu textuel d'une URL (page web, doc, API).",
        category = "web",
        parameters = listOf(
            ToolParameter("url", "string", "URL à récupérer"),
            ToolParameter("format", "string", "text|markdown|html (défaut text)", required = false)
        )
    )
    override val permissionKey = "webfetch"
    override fun permissionInput(args: JSONObject) = Args.str(args, "url")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        var url = Args.str(args, "url") ?: return ToolResult.error("Paramètre 'url' requis")
        if (!url.startsWith("http")) url = "https://$url"
        return try {
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) OpenDroid")
                .build()
            Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ToolResult.error("HTTP ${response.code} pour $url")
                val body = response.body?.string().orEmpty()
                val text = htmlToText(body).take(8000)
                ToolResult.ok(if (text.isBlank()) "(contenu vide)" else text)
            }
        } catch (e: Exception) {
            ToolResult.error("Échec du fetch : ${e.message}")
        }
    }

    private fun htmlToText(html: String): String {
        return html
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("(?is)<head.*?</head>"), " ")
            .replace(Regex("(?s)<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&quot;", "\"")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n\\s*\\n+"), "\n\n")
            .trim()
    }
}

/** Outil `websearch` : recherche web via DuckDuckGo (sans clé API). */
class WebSearchTool : Tool {
    override val spec = ToolSpec(
        id = "websearch",
        description = "Rechercher sur le web et renvoyer les principaux résultats (titre, lien, extrait).",
        category = "web",
        parameters = listOf(
            ToolParameter("query", "string", "Requête de recherche")
        )
    )
    override val permissionKey = "websearch"
    override fun permissionInput(args: JSONObject) = Args.str(args, "query")

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        val query = Args.str(args, "query") ?: return ToolResult.error("Paramètre 'query' requis")
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://html.duckduckgo.com/html/?q=$encoded")
                .header("User-Agent", "Mozilla/5.0 (Android) OpenDroid")
                .build()
            Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ToolResult.error("HTTP ${response.code}")
                val html = response.body?.string().orEmpty()
                val results = parseResults(html)
                if (results.isEmpty()) ToolResult.ok("Aucun résultat pour « $query »")
                else ToolResult.ok(results.joinToString("\n\n") { it })
            }
        } catch (e: Exception) {
            ToolResult.error("Recherche impossible : ${e.message}")
        }
    }

    private fun parseResults(html: String): List<String> {
        val regex = Regex("""(?s)<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""")
        val snippetRegex = Regex("""(?s)<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""")
        val snippets = snippetRegex.findAll(html).map { it.groupValues[1] }.toList()
        return regex.findAll(html).take(6).mapIndexed { i, m ->
            val link = decodeDdg(m.groupValues[1])
            val title = m.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
            val snippet = snippets.getOrNull(i)?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty()
            "• $title\n  $link\n  $snippet"
        }.toList()
    }

    private fun decodeDdg(url: String): String {
        val marker = "uddg="
        val idx = url.indexOf(marker)
        if (idx == -1) return url
        val raw = url.substring(idx + marker.length).substringBefore('&')
        return try { URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
    }
}
