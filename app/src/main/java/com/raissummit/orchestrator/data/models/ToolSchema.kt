package com.raissummit.orchestrator.data.models

data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true
)

data class ToolSchema(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>
) {
    companion object {
        val allTools: List<ToolSchema> = listOf(
            // Découverte d'applications
            ToolSchema(
                name = "get_installed_apps",
                description = "Lister les applications installées correspondant à un nom. Utilise ceci pour découvrir dynamiquement les apps disponibles au lieu de deviner les package names.",
                parameters = listOf(
                    ToolParameter("query", "string", "Filtre optionnel par nom d'app (ex: sms, photos, musique)")
                )
            ),
            // Action : communication
            ToolSchema(
                name = "send_sms",
                description = "Envoyer un message SMS",
                parameters = listOf(
                    ToolParameter("recipient", "string", "Destinataire (numéro ou nom)"),
                    ToolParameter("message", "string", "Contenu du message")
                )
            ),
            ToolSchema(
                name = "make_call",
                description = "Passer un appel téléphonique",
                parameters = listOf(
                    ToolParameter("contact_number", "string", "Numéro de téléphone")
                )
            ),
            ToolSchema(
                name = "share_text",
                description = "Partager du texte via une autre application",
                parameters = listOf(
                    ToolParameter("text", "string", "Texte à partager")
                )
            ),
            // Action : applications
            ToolSchema(
                name = "open_app",
                description = "Ouvrir une application par son package name Android. Utilise get_installed_apps d'abord pour trouver le bon package.",
                parameters = listOf(
                    ToolParameter("app_package_name", "string", "Package name Android exact de l'application à ouvrir")
                )
            ),
            // Action : UI
            ToolSchema(
                name = "tap",
                description = "Appuyer à une position sur l'écran",
                parameters = listOf(
                    ToolParameter("x", "integer", "Coordonnée X"),
                    ToolParameter("y", "integer", "Coordonnée Y")
                )
            ),
            ToolSchema(
                name = "type_text",
                description = "Saisir du texte dans le champ actif",
                parameters = listOf(
                    ToolParameter("text", "string", "Texte à saisir")
                )
            ),
            ToolSchema(
                name = "scroll",
                description = "Défiler l'écran vers le bas ou le haut",
                parameters = listOf(
                    ToolParameter("direction", "string", "Direction : 'down' ou 'up'"),
                    ToolParameter("amount", "integer", "Distance en pixels (défaut: 300)")
                )
            ),
            ToolSchema(
                name = "go_back",
                description = "Simuler le bouton retour système",
                parameters = emptyList()
            ),
            // Action : système
            ToolSchema(
                name = "set_alarm",
                description = "Programmer une alarme",
                parameters = listOf(
                    ToolParameter("hour", "integer", "Heure (0-23)"),
                    ToolParameter("minute", "integer", "Minute (0-59)"),
                    ToolParameter("label", "string", "Libellé de l'alarme")
                )
            ),
            ToolSchema(
                name = "take_screenshot",
                description = "Prendre une capture d'écran",
                parameters = emptyList()
            ),
            // Contexte
            ToolSchema(
                name = "get_device_location",
                description = "Obtenir la localisation de l'appareil",
                parameters = emptyList()
            ),
            ToolSchema(
                name = "get_battery_info",
                description = "Obtenir le niveau et l'état de la batterie",
                parameters = emptyList()
            ),
            ToolSchema(
                name = "get_network_status",
                description = "Obtenir l'état de la connectivité réseau",
                parameters = emptyList()
            ),
            // Recherche
            ToolSchema(
                name = "search_web",
                description = "Effectuer une recherche web",
                parameters = listOf(
                    ToolParameter("query", "string", "Requête de recherche")
                )
            ),
            // Notes
            ToolSchema(
                name = "set_note",
                description = "Enregistrer une note",
                parameters = listOf(
                    ToolParameter("title", "string", "Titre de la note"),
                    ToolParameter("content", "string", "Contenu de la note")
                )
            ),
            // Interaction utilisateur
            ToolSchema(
                name = "ask_user_confirmation",
                description = "Demander une confirmation à l'utilisateur avant une action sensible",
                parameters = listOf(
                    ToolParameter("message", "string", "Message de confirmation")
                )
            ),
            ToolSchema(
                name = "ask_followup_question",
                description = "Poser une question de clarification à l'utilisateur",
                parameters = listOf(
                    ToolParameter("question", "string", "Question à poser")
                )
            )
        )
    }
}
