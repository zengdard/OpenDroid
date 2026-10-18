package com.opendroid.data.model

/**
 * Niveau de permission, à la manière d'opencode :
 * ALLOW (exécution directe), ASK (demande), DENY (interdit).
 */
enum class PermissionLevel {
    ALLOW, ASK, DENY;

    val label: String
        get() = when (this) {
            ALLOW -> "Autoriser"
            ASK -> "Demander"
            DENY -> "Refuser"
        }

    val shortLabel: String get() = name.lowercase()
}

/**
 * Une règle de permission : un motif (glob) associé à un niveau.
 * Les motifs utilisent `*` (zéro ou plusieurs caractères) et `?` (un caractère).
 */
data class PermissionRule(
    val pattern: String,
    val level: PermissionLevel
)

/**
 * Configuration de permissions façon opencode.
 * Clé = nom de l'outil (`read`, `edit`, `bash`, `open_app`, ...) ou `*` (défaut global).
 * Pour un outil donné, les règles sont évaluées dans l'ordre : **la dernière règle
 * correspondante l'emporte**. Le motif est comparé à l'entrée de l'outil quand elle
 * existe (commande bash, URL, chemin de fichier…), sinon au nom de l'outil.
 */
data class PermissionConfig(
    val entries: Map<String, List<PermissionRule>> = emptyMap()
) {
    /** Fusion : les entrées de [override] remplacent celles de `this` pour les mêmes clés. */
    fun merge(override: PermissionConfig): PermissionConfig {
        if (override.entries.isEmpty()) return this
        return PermissionConfig(entries + override.entries)
    }

    /**
     * Résout le niveau pour [key] avec l'entrée éventuelle [input].
     * Renvoie null si aucune règle ne s'applique (l'appelant utilise alors le défaut).
     */
    fun resolve(key: String, input: String? = null): PermissionLevel? {
        entries[key]?.let { rules ->
            matchRules(rules, input ?: key)?.let { return it }
        }
        if (key != "*") {
            entries["*"]?.let { rules ->
                matchRules(rules, input ?: key)?.let { return it }
            }
        }
        return null
    }

    private fun matchRules(rules: List<PermissionRule>, value: String): PermissionLevel? {
        var result: PermissionLevel? = null
        rules.forEach { rule ->
            if (glob(rule.pattern, value)) result = rule.level
        }
        return result
    }

    fun level(key: String): PermissionLevel? =
        resolve(key) ?: entries.entries.firstOrNull { it.key == key }?.value?.lastOrNull()?.level

    companion object {
        /** Défauts permissifs d'opencode : la plupart « allow », doom_loop/external_directory « ask ». */
        val ASK_BY_DEFAULT = setOf("doom_loop", "external_directory", "task")

        fun defaultFor(key: String): PermissionLevel =
            if (key in ASK_BY_DEFAULT) PermissionLevel.ASK else PermissionLevel.ALLOW

        /** Outils en lecture seule : ne modifient pas l'état. */
        val READ_ONLY_TOOLS = setOf(
            "read", "grep", "glob", "list", "webfetch", "websearch", "screen_dump",
            "list_apps", "app_info", "device_info", "get_battery_info", "get_network_status",
            "contacts_search", "notifications_list", "clipboard_get", "todoread", "question",
            "get_location", "list_events"
        )
    }
}

/** Correspondance glob simple : `*` = zéro+ caractères, `?` = un caractère. */
fun glob(pattern: String, value: String): Boolean {
    val regex = buildString {
        append('^')
        var i = 0
        while (i < pattern.length) {
            when (val c = pattern[i]) {
                '*' -> append(".*")
                '?' -> append('.')
                else -> append(Regex.escape(c.toString()))
            }
            i++
        }
        append('$')
    }
    return Regex(regex).matches(value)
}
