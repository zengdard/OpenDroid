package com.opendroid.data.model

/** Un paramètre d'outil décrit de façon déclarative (converti en JSON Schema par les providers). */
data class ToolParameter(
    val name: String,
    val type: String, // "string" | "integer" | "number" | "boolean"
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String> = emptyList()
)

/** Description d'un outil exposée au modèle. */
data class ToolSpec(
    val id: String,
    val description: String,
    val parameters: List<ToolParameter> = emptyList(),
    /** Catégorie d'affichage : "app", "ui", "device", "comm", "system", "agent"... */
    val category: String = "misc",
    /** Necessite l'accord explicite de l'utilisateur par défaut. */
    val dangerousByDefault: Boolean = false
)
