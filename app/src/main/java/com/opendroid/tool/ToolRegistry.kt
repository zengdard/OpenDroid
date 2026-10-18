package com.opendroid.tool

import com.opendroid.data.model.ToolSpec
import com.opendroid.tool.impl.AppInfoTool
import com.opendroid.tool.impl.BashTool
import com.opendroid.tool.impl.BatteryTool
import com.opendroid.tool.impl.ClipboardGetTool
import com.opendroid.tool.impl.ClipboardSetTool
import com.opendroid.tool.impl.ContactsSearchTool
import com.opendroid.tool.impl.CreateEventTool
import com.opendroid.tool.impl.DeviceInfoTool
import com.opendroid.tool.impl.EditTool
import com.opendroid.tool.impl.FindTapTool
import com.opendroid.tool.impl.GlobTool
import com.opendroid.tool.impl.GrepTool
import com.opendroid.tool.impl.ListAppsTool
import com.opendroid.tool.impl.ListEventsTool
import com.opendroid.tool.impl.ListTool
import com.opendroid.tool.impl.LocationTool
import com.opendroid.tool.impl.LongPressTool
import com.opendroid.tool.impl.MakeCallTool
import com.opendroid.tool.impl.MediaControlTool
import com.opendroid.tool.impl.NetworkStatusTool
import com.opendroid.tool.impl.NotificationListTool
import com.opendroid.tool.impl.NotificationSendTool
import com.opendroid.tool.impl.OpenAppTool
import com.opendroid.tool.impl.OpenSettingsTool
import com.opendroid.tool.impl.OpenUrlTool
import com.opendroid.tool.impl.PressKeyTool
import com.opendroid.tool.impl.QuestionTool
import com.opendroid.tool.impl.ReadTool
import com.opendroid.tool.impl.ScreenDumpTool
import com.opendroid.tool.impl.ScreenshotTool
import com.opendroid.tool.impl.SendSmsTool
import com.opendroid.tool.impl.SetAlarmTool
import com.opendroid.tool.impl.ShareTextTool
import com.opendroid.tool.impl.SwipeTool
import com.opendroid.tool.impl.TapIdTool
import com.opendroid.tool.impl.TapTool
import com.opendroid.tool.impl.TaskTool
import com.opendroid.tool.impl.TermuxTool
import com.opendroid.tool.impl.TodoReadTool
import com.opendroid.tool.impl.TodoWriteTool
import com.opendroid.tool.impl.TypeTextTool
import com.opendroid.tool.impl.VerifyTool
import com.opendroid.tool.impl.WebFetchTool
import com.opendroid.tool.impl.WebSearchTool
import com.opendroid.tool.impl.WriteTool

/**
 * Registre de tous les outils disponibles.
 * Composé des outils intégrés + des outils dynamiques (ex. serveurs MCP).
 */
object ToolRegistry {

    private val builtin: List<Tool> = listOf(
        // Fichiers (façon opencode)
        ReadTool(), WriteTool(), EditTool(), GlobTool(), GrepTool(), ListTool(),
        // Shell
        BashTool(), TermuxTool(),
        // Web
        WebFetchTool(), WebSearchTool(),
        // Agents
        TaskTool(), QuestionTool(), TodoWriteTool(), TodoReadTool(),
        // Applications
        ListAppsTool(), AppInfoTool(), OpenAppTool(), OpenUrlTool(), OpenSettingsTool(),
        // Interface
        ScreenDumpTool(), TapTool(), TapIdTool(), LongPressTool(), SwipeTool(),
        TypeTextTool(), PressKeyTool(), FindTapTool(), VerifyTool(),
        // Appareil
        DeviceInfoTool(), BatteryTool(), NetworkStatusTool(), LocationTool(),
        ClipboardGetTool(), ClipboardSetTool(), NotificationSendTool(), NotificationListTool(),
        MediaControlTool(), ScreenshotTool(),
        // Communication / productivité
        SendSmsTool(), MakeCallTool(), ShareTextTool(), ContactsSearchTool(),
        SetAlarmTool(), CreateEventTool(), ListEventsTool()
    )

    private val dynamic = LinkedHashMap<String, Tool>()

    /** Enregistre (ou remplace) les outils dynamiques, ex. ceux exposés par MCP. */
    @Synchronized
    fun setDynamicTools(tools: List<Tool>) {
        dynamic.clear()
        tools.forEach { dynamic[it.spec.id] = it }
    }

    @Synchronized
    fun clearDynamicTools() {
        dynamic.clear()
    }

    val tools: List<Tool>
        get() = synchronized(this) { builtin + dynamic.values }

    val byId: Map<String, Tool>
        get() = synchronized(this) { tools.associateBy { it.spec.id } }

    fun spec(id: String): ToolSpec? = byId[id]?.spec

    fun get(id: String): Tool? = byId[id]

    val allSpecs: List<ToolSpec> get() = tools.map { it.spec }

    /** Clés de permission affichées dans l'écran Permissions. */
    val permissionKeys: List<String> get() = tools.map { it.permissionKey }.distinct().sorted()

    val categories: List<String> get() = tools.map { it.spec.category }.distinct().sorted()
}
