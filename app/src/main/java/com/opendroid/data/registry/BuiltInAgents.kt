package com.opendroid.data.registry

import com.opendroid.data.model.AgentConfig
import com.opendroid.data.model.AgentMode
import com.opendroid.data.model.PermissionConfig
import com.opendroid.data.model.PermissionLevel
import com.opendroid.data.model.PermissionRule

/**
 * Agents intégrés, calqués sur opencode :
 *  - build   (primary, tous les outils)
 *  - plan    (primary, lecture seule : édition et shell demandent/refusent)
 *  - general (subagent, tous les outils, tâches multi-étapes)
 *  - explore (subagent, lecture seule, exploration rapide)
 *  - scout   (subagent, lecture seule, recherche externe / docs)
 */
object BuiltInAgents {

    private fun rules(vararg pairs: Pair<String, PermissionLevel>) =
        PermissionConfig(pairs.associate { (k, v) -> k to listOf(PermissionRule("*", v)) })

    private const val COMMON_RULES = """
Tu opères sur un appareil Android. Tu disposes d'outils pour interagir avec le système :
ouvrir des applications, agir sur l'interface (taps, saisie, navigation), lire/modifier des
fichiers, exécuter des commandes shell, envoyer des SMS, passer des appels, consulter
l'appareil (batterie, réseau, localisation, notifications, presse-papiers, contacts),
rechercher sur le web, programmer des alarmes, et déléguer des sous-tâches à d'autres agents.

Principes :
- Réfléchis avant d'agir, une action à la fois, et observe le résultat avant de continuer.
- Ne devine jamais un identifiant : utilise `list_apps` / `app_info` pour découvrir les paquets.
- Pour exécuter une commande shell : utilise **`termux`** dès que l'utilisateur parle de
  Termux ou que la commande a besoin de l'environnement Termux (`pkg`, `apt`, `python`,
  `pip`, `git`, `node`, ${'$'}PREFIX…). Utilise `bash` pour le shell minimal de l'app
  (fichiers, `ls`, `cat`, `dumpsys`, `pm`, `am`) ou avec `root=true`.
- Pour agir sur l'écran : appelle `screen_dump`, puis utilise `tap_id` avec l'index `#N`
  de l'élément (plus fiable que des coordonnées). Refais `screen_dump` après un changement d'écran.
- Vérifie tes checkpoints avec `verify` (texte présent/absent, paquet actif) au lieu de supposer.
- Pour une tâche multi-étapes, tiens un plan avec `todowrite` et un champ `verify` par étape.
- En fin de tâche complexe, tu peux faire auditer le résultat par le sous-agent `checker` (outil `task`).
- Si une action est sensible (SMS, appel, suppression, commande shell destructive), demande
  confirmation via `question` si un doute existe.
- Réponds dans la langue de l'utilisateur (français par défaut).

Aujourd'hui : {date}
"""

    val chat = AgentConfig(
        id = "chat",
        name = "Chat",
        description = "Conversation simple, sans outils ni action sur l'appareil.",
        mode = AgentMode.PRIMARY,
        systemPrompt = "Tu es un assistant conversationnel utile, direct et naturel. " +
            "Réponds dans la langue de l'utilisateur, sans appeler d'outil et sans agir sur l'appareil. " +
            "Tu peux analyser une capture d'écran si elle t'est fournie.\n\nAujourd'hui : {date}",
        // Aucun outil : tout est refusé → mode chatbot pur.
        permission = rules("*" to PermissionLevel.DENY),
        temperature = 0.7f,
        builtIn = true
    )

    val build = AgentConfig(
        id = "build",
        name = "Build",
        description = "Agent par défaut avec tous les outils activés. Pour les tâches complètes.",
        mode = AgentMode.PRIMARY,
        systemPrompt = "Tu es OpenDroid, un agent autonome capable de contrôler un appareil Android " +
            "et d'exécuter des tâches de bout en bout.\n$COMMON_RULES",
        temperature = 0.3f,
        builtIn = true
    )

    val plan = AgentConfig(
        id = "plan",
        name = "Plan",
        description = "Agent d'analyse : lecture seule, aucune modification ni action sensible.",
        mode = AgentMode.PRIMARY,
        systemPrompt = "Tu es OpenDroid en mode Plan. Tu analyses, explores et proposes un plan " +
            "détaillé SANS modifier l'appareil. Tu ne dois pas exécuter d'action mutante : " +
            "explique ce que tu ferais puis laisse l'utilisateur basculer sur l'agent Build.\n$COMMON_RULES",
        permission = rules(
            "edit" to PermissionLevel.DENY,
            "bash" to PermissionLevel.ASK,
            "open_app" to PermissionLevel.DENY,
            "tap" to PermissionLevel.DENY,
            "long_press" to PermissionLevel.DENY,
            "swipe" to PermissionLevel.DENY,
            "type_text" to PermissionLevel.DENY,
            "press_key" to PermissionLevel.DENY,
            "send_sms" to PermissionLevel.DENY,
            "make_call" to PermissionLevel.DENY,
            "media_control" to PermissionLevel.DENY
        ),
        temperature = 0.1f,
        builtIn = true
    )

    val general = AgentConfig(
        id = "general",
        name = "General",
        description = "Agent polyvalent pour la recherche et les tâches multi-étapes complexes.",
        mode = AgentMode.SUBAGENT,
        systemPrompt = "Tu es un sous-agent généraliste d'OpenDroid. Tu accomplis la tâche qui t'est " +
            "confiée de façon autonome et tu renvoies un résultat concis et factuel.\n$COMMON_RULES",
        temperature = 0.3f,
        builtIn = true
    )

    val explore = AgentConfig(
        id = "explore",
        name = "Explore",
        description = "Agent rapide en lecture seule pour explorer l'appareil ou le système de fichiers.",
        mode = AgentMode.SUBAGENT,
        systemPrompt = "Tu es un sous-agent d'exploration d'OpenDroid. Tu es rapide et strictement en " +
            "lecture seule : tu observes, cherches et résumes. Tu ne modifies rien.",
        permission = rules(
            "edit" to PermissionLevel.DENY,
            "bash" to PermissionLevel.DENY,
            "open_app" to PermissionLevel.DENY,
            "tap" to PermissionLevel.DENY,
            "swipe" to PermissionLevel.DENY,
            "type_text" to PermissionLevel.DENY,
            "press_key" to PermissionLevel.DENY,
            "send_sms" to PermissionLevel.DENY,
            "make_call" to PermissionLevel.DENY
        ),
        temperature = 0.1f,
        builtIn = true
    )

    val scout = AgentConfig(
        id = "scout",
        name = "Scout",
        description = "Agent de recherche externe : documentation, web, veille, en lecture seule.",
        mode = AgentMode.SUBAGENT,
        systemPrompt = "Tu es un sous-agent de recherche d'OpenDroid. Tu utilises `websearch` et " +
            "`webfetch` pour rassembler et synthétiser des informations externes fiables. Lecture seule.",
        permission = rules(
            "edit" to PermissionLevel.DENY,
            "bash" to PermissionLevel.DENY,
            "tap" to PermissionLevel.DENY,
            "type_text" to PermissionLevel.DENY,
            "send_sms" to PermissionLevel.DENY,
            "make_call" to PermissionLevel.DENY
        ),
        temperature = 0.2f,
        builtIn = true
    )

    val checker = AgentConfig(
        id = "checker",
        name = "Checker",
        description = "Audite un résultat par rapport à l'objectif initial (lecture seule).",
        mode = AgentMode.SUBAGENT,
        systemPrompt = "Tu es le Checker d'OpenDroid. On te confie un objectif et le résultat obtenu. " +
            "Vérifie factuellement, à l'aide de `screen_dump` et `verify` si nécessaire, que l'objectif " +
            "est réellement atteint. Réponds d'abord par 'OK' si c'est conforme, sinon liste précisément " +
            "les écarts constatés et l'action corrective recommandée. Tu ne modifies jamais l'appareil.",
        permission = rules(
            "edit" to PermissionLevel.DENY,
            "bash" to PermissionLevel.DENY,
            "open_app" to PermissionLevel.DENY,
            "tap" to PermissionLevel.DENY,
            "long_press" to PermissionLevel.DENY,
            "swipe" to PermissionLevel.DENY,
            "type_text" to PermissionLevel.DENY,
            "press_key" to PermissionLevel.DENY,
            "send_sms" to PermissionLevel.DENY,
            "make_call" to PermissionLevel.DENY,
            "media_control" to PermissionLevel.DENY
        ),
        temperature = 0.0f,
        builtIn = true
    )

    val all: List<AgentConfig> = listOf(chat, build, plan, general, explore, scout, checker)

    val primary: List<AgentConfig> = all.filter { it.mode == AgentMode.PRIMARY || it.mode == AgentMode.ALL }
    val subagents: List<AgentConfig> = all.filter { (it.mode == AgentMode.SUBAGENT || it.mode == AgentMode.ALL) && !it.hidden }

    fun byId(id: String): AgentConfig = all.firstOrNull { it.id == id } ?: build
}
