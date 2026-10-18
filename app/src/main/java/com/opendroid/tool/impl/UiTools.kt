package com.opendroid.tool.impl

import com.opendroid.data.model.ToolParameter
import com.opendroid.data.model.ToolSpec
import com.opendroid.tool.Args
import com.opendroid.tool.Tool
import com.opendroid.tool.ToolContext
import com.opendroid.tool.ToolResult
import org.json.JSONObject

/** Outils d'interaction avec l'interface Android (service d'accessibilité requis). */

private fun noAccessibility() = ToolResult.error(
    "Service d'accessibilité non activé. Activez « OpenDroid » dans Paramètres → Accessibilité."
)

class ScreenDumpTool : Tool {
    override val spec = ToolSpec(
        id = "screen_dump",
        description = "Lire l'arbre de l'interface actuelle (textes, éléments cliquables, coordonnées). " +
            "À utiliser avant de taper pour localiser les éléments.",
        category = "ui"
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        if (!context.accessibility.isReady) return noAccessibility()
        val pkg = context.accessibility.packageName() ?: "?"
        return ToolResult.ok("App active: $pkg\n${context.accessibility.dumpScreen()}")
    }
}

class TapTool : Tool {
    override val spec = ToolSpec(
        id = "tap",
        description = "Appuyer à une position de l'écran (coordonnées en pixels).",
        category = "ui",
        parameters = listOf(
            ToolParameter("x", "integer", "Coordonnée X"),
            ToolParameter("y", "integer", "Coordonnée Y")
        ),
        dangerousByDefault = true
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        if (!context.accessibility.isReady) return noAccessibility()
        val x = Args.int(args, "x") ?: return ToolResult.error("Paramètre 'x' requis")
        val y = Args.int(args, "y") ?: return ToolResult.error("Paramètre 'y' requis")
        return if (context.accessibility.tap(x.toFloat(), y.toFloat())) ToolResult.ok("Tap en ($x,$y)")
        else ToolResult.error("Tap échoué en ($x,$y)")
    }
}

class LongPressTool : Tool {
    override val spec = ToolSpec(
        id = "long_press",
        description = "Appui long à une position de l'écran.",
        category = "ui",
        parameters = listOf(
            ToolParameter("x", "integer", "Coordonnée X"),
            ToolParameter("y", "integer", "Coordonnée Y"),
            ToolParameter("duration", "integer", "Durée en ms (défaut 600)", required = false)
        ),
        dangerousByDefault = true
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        if (!context.accessibility.isReady) return noAccessibility()
        val x = Args.int(args, "x") ?: return ToolResult.error("Paramètre 'x' requis")
        val y = Args.int(args, "y") ?: return ToolResult.error("Paramètre 'y' requis")
        val d = Args.int(args, "duration", 600).toLong()
        return if (context.accessibility.longPress(x.toFloat(), y.toFloat(), d)) ToolResult.ok("Appui long en ($x,$y)")
        else ToolResult.error("Appui long échoué")
    }
}

class SwipeTool : Tool {
    override val spec = ToolSpec(
        id = "swipe",
        description = "Balayer l'écran. Fournir soit une direction (up/down/left/right), soit des coordonnées.",
        category = "ui",
        parameters = listOf(
            ToolParameter("direction", "string", "up|down (raccourci)", required = false),
            ToolParameter("x1", "integer", "Départ X", required = false),
            ToolParameter("y1", "integer", "Départ Y", required = false),
            ToolParameter("x2", "integer", "Arrivée X", required = false),
            ToolParameter("y2", "integer", "Arrivée Y", required = false),
            ToolParameter("duration", "integer", "Durée en ms (défaut 300)", required = false)
        ),
        dangerousByDefault = true
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        if (!context.accessibility.isReady) return noAccessibility()
        val dur = Args.int(args, "duration", 300).toLong()
        Args.str(args, "direction")?.lowercase()?.let { dir ->
            val ok = when (dir) {
                "up" -> context.accessibility.scrollUp()
                "down" -> context.accessibility.scrollDown()
                else -> false
            }
            return if (ok) ToolResult.ok("Défilement $dir") else ToolResult.error("Défilement échoué")
        }
        val x1 = Args.int(args, "x1") ?: return ToolResult.error("Direction ou coordonnées requises")
        val y1 = Args.int(args, "y1") ?: return ToolResult.error("'y1' requis")
        val x2 = Args.int(args, "x2") ?: return ToolResult.error("'x2' requis")
        val y2 = Args.int(args, "y2") ?: return ToolResult.error("'y2' requis")
        return if (context.accessibility.swipe(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), dur))
            ToolResult.ok("Balayage ($x1,$y1) → ($x2,$y2)")
        else ToolResult.error("Balayage échoué")
    }
}

class TypeTextTool : Tool {
    override val spec = ToolSpec(
        id = "type_text",
        description = "Saisir du texte dans le champ actuellement focalisé.",
        category = "ui",
        parameters = listOf(
            ToolParameter("text", "string", "Texte à saisir")
        ),
        dangerousByDefault = true
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        if (!context.accessibility.isReady) return noAccessibility()
        val text = args.optString("text", "")
        if (text.isEmpty()) return ToolResult.error("Paramètre 'text' requis")
        return if (context.accessibility.setText(text)) ToolResult.ok("Texte saisi")
        else ToolResult.error("Aucun champ focalisé")
    }
}

class PressKeyTool : Tool {
    override val spec = ToolSpec(
        id = "press_key",
        description = "Appuyer sur une touche système : back, home, recents, notifications.",
        category = "ui",
        parameters = listOf(
            ToolParameter("key", "string", "Touche", enumValues = listOf("back", "home", "recents", "notifications"))
        ),
        dangerousByDefault = true
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        if (!context.accessibility.isReady) return noAccessibility()
        val ok = when (Args.str(args, "key", "back").lowercase()) {
            "home" -> context.accessibility.pressHome()
            "recents" -> context.accessibility.pressRecents()
            "notifications" -> context.accessibility.pressNotifications()
            else -> context.accessibility.pressBack()
        }
        return if (ok) ToolResult.ok("Touche envoyée") else ToolResult.error("Action échouée")
    }
}

class FindTapTool : Tool {
    override val spec = ToolSpec(
        id = "find_tap",
        description = "Trouver un élément par son texte (ou description) et appuyer dessus.",
        category = "ui",
        parameters = listOf(
            ToolParameter("text", "string", "Texte de l'élément à appuyer")
        ),
        dangerousByDefault = true
    )

    override suspend fun execute(context: ToolContext, args: JSONObject): ToolResult {
        if (!context.accessibility.isReady) return noAccessibility()
        val text = Args.str(args, "text") ?: return ToolResult.error("Paramètre 'text' requis")
        return if (context.accessibility.findAndTap(text)) ToolResult.ok("Élément « $text » appuyé")
        else ToolResult.error("Élément « $text » introuvable")
    }
}
