package com.opendroid.agent

import com.opendroid.data.model.AgentConfig
import com.opendroid.data.model.ToolSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Construit les prompts système des agents. */
object AgentPrompt {

    fun system(agent: AgentConfig, includeToolCatalog: Boolean, tools: List<ToolSpec>): String {
        val date = SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH).format(Date())
        val base = agent.systemPrompt.replace("{date}", date)
        if (!includeToolCatalog || tools.isEmpty()) return base
        val catalog = buildString {
            append("\n\n# FORMAT DE RÉPONSE\n")
            append("Tu disposes d'outils. Réponds en respectant ces balises :\n")
            append("<thinking>raisonnement</thinking>\n")
            append("<tool_call name=\"outil\" param=\"valeur\"></tool_call>\n")
            append("<answer>réponse finale à l'utilisateur</answer>\n")
            append("Un seul appel d'outil à la fois. Après le résultat, décide : nouvel outil ou réponse finale.\n")
            append("\n# OUTILS\n")
            tools.forEach { t ->
                val params = t.parameters.joinToString(", ") { p -> "${p.name}:${p.type}${if (p.required) "" else "?"}" }
                append("- ${t.id}($params) — ${t.description}\n")
            }
        }
        return base + catalog
    }
}
