package com.opendroid.data.model

/**
 * Mode d'un agent (façon opencode) :
 * PRIMARY   : agent principal sélectionnable dans l'UI.
 * SUBAGENT  : uniquement invocable par un autre agent via l'outil `task`.
 * ALL       : les deux.
 */
enum class AgentMode { PRIMARY, SUBAGENT, ALL }

/**
 * Configuration d'un agent, à la manière d'opencode.
 * Un agent possède un prompt système, un modèle (optionnel), des permissions
 * (qui déterminent aussi quels outils sont accessibles), et un mode.
 */
data class AgentConfig(
    val id: String,
    val name: String,
    val description: String,
    val mode: AgentMode = AgentMode.ALL,
    val systemPrompt: String,
    /** Surcharge de permission par outil (s'applique après la config globale). */
    val permission: PermissionConfig = PermissionConfig(),
    /** Surcharge de modèle (vide = modèle de la session). */
    val model: String = "",
    val temperature: Float? = null,
    /** Nb max d'itérations agentiques avant réponse forcée (null = illimité). */
    val steps: Int? = null,
    /** Masqué de la liste `@` (invoquable seulement via l'outil task). */
    val hidden: Boolean = false,
    /** Couleur d'accent (hex ou nom de thème). */
    val color: String = "",
    val builtIn: Boolean = false
) {
    /**
     * Un outil est exposé au modèle si sa permission n'est pas DENY
     * (règle de l'agent d'abord, puis config globale).
     */
    fun exposesTool(toolId: String, global: PermissionConfig): Boolean {
        val resolved = resolvePermission(toolId, global)
        return resolved != PermissionLevel.DENY
    }

    fun resolvePermission(toolId: String, global: PermissionConfig): PermissionLevel =
        permission.resolve(toolId) ?: global.resolve(toolId) ?: PermissionConfig.defaultFor(toolId)
}
