package com.opendroid.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.opendroid.data.model.PermissionConfig
import com.opendroid.data.model.PermissionLevel
import com.opendroid.data.model.PermissionRule
import com.opendroid.data.model.McpServerConfig
import com.opendroid.data.model.ProviderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "opendroid_settings")

/**
 * Réglages persistants d'OpenDroid : providers, agent/modèle actifs, permissions, modèle on-device.
 * Le stockage reprend les conventions d'opencode (config JSON local).
 */
class SettingsStore(private val context: Context) {

    private val gson = Gson()

    private object Keys {
        val PROVIDERS = stringPreferencesKey("providers_json")
        val ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
        val ACTIVE_MODEL = stringPreferencesKey("active_model")
        val ACTIVE_AGENT = stringPreferencesKey("active_agent")
        val PERMISSIONS = stringPreferencesKey("permissions_json")
        val ON_DEVICE_PATH = stringPreferencesKey("on_device_model_path")
        val ON_DEVICE_DOWNLOADED = booleanPreferencesKey("on_device_downloaded")
        val AUTO_APPROVE = booleanPreferencesKey("auto_approve")
        val MCP_SERVERS = stringPreferencesKey("mcp_servers_json")
    }

    private val listType = object : TypeToken<List<ProviderConfig>>() {}.type
    private val permType = object : TypeToken<Map<String, List<PermissionRule>>>() {}.type
    private val mcpType = object : TypeToken<List<McpServerConfig>>() {}.type

    // ---- Providers ----
    val providers: Flow<List<ProviderConfig>> = context.dataStore.data.map { decodeProviders(it[Keys.PROVIDERS]) }
    val activeProviderId: Flow<String> = context.dataStore.data.map { it[Keys.ACTIVE_PROVIDER] ?: "on-device" }
    val activeModel: Flow<String> = context.dataStore.data.map { it[Keys.ACTIVE_MODEL] ?: "" }
    val activeAgentId: Flow<String> = context.dataStore.data.map { it[Keys.ACTIVE_AGENT] ?: "build" }

    // ---- Permissions ----
    val permissionConfig: Flow<PermissionConfig> = context.dataStore.data.map { decodePermissions(it[Keys.PERMISSIONS]) }
    val autoApprove: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_APPROVE] ?: false }

    // ---- On-device ----
    val onDeviceModelPath: Flow<String?> = context.dataStore.data.map { it[Keys.ON_DEVICE_PATH]?.takeIf { p -> p.isNotBlank() } }
    val onDeviceDownloaded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ON_DEVICE_DOWNLOADED] ?: false }

    // ---- MCP ----
    val mcpServers: Flow<List<McpServerConfig>> = context.dataStore.data.map { decodeMcp(it[Keys.MCP_SERVERS]) }

    suspend fun upsertMcpServer(server: McpServerConfig) {
        context.dataStore.edit { prefs ->
            val current = decodeMcp(prefs[Keys.MCP_SERVERS]).toMutableList()
            val idx = current.indexOfFirst { it.id == server.id }
            if (idx >= 0) current[idx] = server else current.add(server)
            prefs[Keys.MCP_SERVERS] = gson.toJson(current)
        }
    }

    suspend fun removeMcpServer(id: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MCP_SERVERS] = gson.toJson(decodeMcp(prefs[Keys.MCP_SERVERS]).filterNot { it.id == id })
        }
    }

    suspend fun setMcpEnabled(id: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = decodeMcp(prefs[Keys.MCP_SERVERS]).map { if (it.id == id) it.copy(enabled = enabled) else it }
            prefs[Keys.MCP_SERVERS] = gson.toJson(current)
        }
    }

    suspend fun saveProviders(providers: List<ProviderConfig>) {
        context.dataStore.edit { it[Keys.PROVIDERS] = gson.toJson(providers) }
    }

    suspend fun upsertProvider(provider: ProviderConfig) {
        context.dataStore.edit { prefs ->
            val current = decodeProviders(prefs[Keys.PROVIDERS]).toMutableList()
            val idx = current.indexOfFirst { it.id == provider.id }
            if (idx >= 0) current[idx] = provider else current.add(provider)
            prefs[Keys.PROVIDERS] = gson.toJson(current)
        }
    }

    suspend fun removeProvider(id: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROVIDERS] = gson.toJson(decodeProviders(prefs[Keys.PROVIDERS]).filterNot { it.id == id })
            if (prefs[Keys.ACTIVE_PROVIDER] == id) prefs[Keys.ACTIVE_PROVIDER] = "on-device"
        }
    }

    suspend fun setActiveProvider(id: String) {
        context.dataStore.edit { it[Keys.ACTIVE_PROVIDER] = id }
    }

    suspend fun setActiveModel(model: String) {
        context.dataStore.edit { it[Keys.ACTIVE_MODEL] = model }
    }

    suspend fun setActiveAgent(id: String) {
        context.dataStore.edit { it[Keys.ACTIVE_AGENT] = id }
    }

    suspend fun setAutoApprove(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_APPROVE] = enabled }
    }

    /** Écrit une règle « shorthand » : un niveau unique pour tous les motifs de l'outil. */
    suspend fun setPermission(key: String, level: PermissionLevel) {
        setPermissionRules(key, listOf(PermissionRule("*", level)))
    }

    suspend fun setPermissionRules(key: String, rules: List<PermissionRule>) {
        context.dataStore.edit { prefs ->
            val current = decodePermissions(prefs[Keys.PERMISSIONS]).entries.toMutableMap()
            current[key] = rules
            prefs[Keys.PERMISSIONS] = gson.toJson(current)
        }
    }

    suspend fun setPermissionConfig(config: PermissionConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PERMISSIONS] = gson.toJson(config.entries)
        }
    }

    suspend fun setOnDeviceModel(path: String, downloaded: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ON_DEVICE_PATH] = path
            prefs[Keys.ON_DEVICE_DOWNLOADED] = downloaded
        }
    }

    suspend fun clearOnDeviceModel() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ON_DEVICE_PATH] = ""
            prefs[Keys.ON_DEVICE_DOWNLOADED] = false
        }
    }

    private fun decodeProviders(raw: String?): List<ProviderConfig> {
        val parsed = try {
            if (raw.isNullOrBlank()) emptyList() else gson.fromJson<List<ProviderConfig>>(raw, listType)
        } catch (_: Exception) {
            emptyList()
        }
        val byId = (ProviderConfig.presets() + parsed).associateBy { it.id }.toMutableMap()
        byId.putIfAbsent("on-device", ProviderConfig.onDevice())
        return byId.values.toList()
    }

    private fun decodePermissions(raw: String?): PermissionConfig {
        val entries: Map<String, List<PermissionRule>> = try {
            if (raw.isNullOrBlank()) emptyMap() else gson.fromJson(raw, permType) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
        return PermissionConfig(entries)
    }

    private fun decodeMcp(raw: String?): List<McpServerConfig> = try {
        if (raw.isNullOrBlank()) emptyList()
        else gson.fromJson<List<McpServerConfig>>(raw, mcpType) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}
