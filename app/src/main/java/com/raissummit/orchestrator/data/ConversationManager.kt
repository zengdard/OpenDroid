package com.raissummit.orchestrator.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.raissummit.orchestrator.orchestrator.ConversationHistory
import com.raissummit.orchestrator.orchestrator.ConversationTurn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.conversationStore: DataStore<Preferences> by preferencesDataStore(name = "conversations")

data class ConversationData(
    val id: String = java.util.UUID.randomUUID().toString().take(8),
    val title: String = "Nouvelle conversation",
    val turns: List<ConversationTurn> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

class ConversationManager(private val context: Context) {
    private val gson = Gson()

    companion object {
        private val CONVERSATIONS = stringPreferencesKey("conversations")
        private val ACTIVE_ID = stringPreferencesKey("active_conversation_id")
    }

    val conversations: Flow<List<ConversationData>> = context.conversationStore.data.map { prefs ->
        val json = prefs[CONVERSATIONS] ?: return@map defaultConversations()
        val type = object : TypeToken<List<ConversationData>>() {}.type
        val list: List<ConversationData> = try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            defaultConversations()
        }
        if (list.isEmpty()) defaultConversations() else list
    }

    val activeConversationId: Flow<String> = context.conversationStore.data.map { prefs ->
        prefs[ACTIVE_ID] ?: ""
    }

    private fun defaultConversations(): List<ConversationData> {
        return listOf(
            ConversationData(
                id = "default",
                title = "Conversation 1",
                turns = emptyList()
            )
        )
    }

    suspend fun createConversation(): String {
        val id = java.util.UUID.randomUUID().toString().take(8)
        context.conversationStore.edit { prefs ->
            val json = prefs[CONVERSATIONS] ?: return@edit
            val type = object : TypeToken<List<ConversationData>>() {}.type
            val list = try {
                gson.fromJson<List<ConversationData>>(json, type).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            list.add(ConversationData(id = id, title = "Conversation ${list.size + 1}"))
            prefs[CONVERSATIONS] = gson.toJson(list)
            prefs[ACTIVE_ID] = id
        }
        return id
    }

    suspend fun switchConversation(id: String) {
        context.conversationStore.edit { prefs ->
            prefs[ACTIVE_ID] = id
        }
    }

    suspend fun deleteConversation(id: String) {
        context.conversationStore.edit { prefs ->
            val json = prefs[CONVERSATIONS] ?: return@edit
            val type = object : TypeToken<List<ConversationData>>() {}.type
            val list = try {
                gson.fromJson<List<ConversationData>>(json, type).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            list.removeAll { it.id == id }
            if (list.isEmpty()) {
                list.add(ConversationData(id = "default", title = "Conversation 1"))
            }
            prefs[CONVERSATIONS] = gson.toJson(list)
            if (prefs[ACTIVE_ID] == id) {
                prefs[ACTIVE_ID] = list.first().id
            }
        }
    }

    suspend fun updateConversationTitle(id: String, title: String) {
        context.conversationStore.edit { prefs ->
            val json = prefs[CONVERSATIONS] ?: return@edit
            val type = object : TypeToken<List<ConversationData>>() {}.type
            val list = try {
                gson.fromJson<List<ConversationData>>(json, type).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(title = title)
                prefs[CONVERSATIONS] = gson.toJson(list)
            }
        }
    }

    suspend fun saveTurns(id: String, turns: List<ConversationTurn>) {
        context.conversationStore.edit { prefs ->
            val json = prefs[CONVERSATIONS] ?: return@edit
            val type = object : TypeToken<List<ConversationData>>() {}.type
            val list = try {
                gson.fromJson<List<ConversationData>>(json, type).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) {
                val existing = list[idx]
                val firstUserMsg = turns.firstOrNull { it.role == "user" }?.content?.take(50)?.replace("\n", " ")
                val title = if (existing.title.startsWith("Conversation") && firstUserMsg != null) {
                    firstUserMsg.take(40) + if (firstUserMsg.length > 40) "..." else ""
                } else existing.title
                list[idx] = existing.copy(turns = turns, title = title)
                prefs[CONVERSATIONS] = gson.toJson(list)
            }
        }
    }
}
