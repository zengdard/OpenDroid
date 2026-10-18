package com.opendroid.data.session

import android.content.Context
import com.google.gson.Gson
import com.opendroid.data.model.Message
import com.opendroid.data.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gestion des sessions (façon opencode) : persistance JSON sur disque,
 * liste triée par récence, session active.
 */
class SessionStore(private val context: Context) {

    private val gson = Gson()
    private val mutex = Mutex()
    private val _sessions = mutableListOf<Session>()
    private val _flow = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _flow.asStateFlow()

    private var activeId: String = ""
    private var loaded = false

    private val dir: File get() = File(context.filesDir, "opendroid_sessions").apply { mkdirs() }

    private fun emit() {
        _flow.value = _sessions.sortedByDescending { it.updatedAt }.map { it.copy(messages = it.messages) }
    }

    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        loaded = true
        withContext(Dispatchers.IO) {
            val files = dir.listFiles { f -> f.extension == "json" }?.sortedByDescending { it.lastModified() } ?: emptyList()
            _sessions.clear()
            for (f in files) {
                try {
                    gson.fromJson(f.readText(), Session::class.java)?.let { _sessions.add(it) }
                } catch (_: Exception) {
                }
            }
        }
        if (_sessions.isEmpty()) _sessions.add(Session(id = "default", title = "Session 1"))
        if (activeId.isBlank() || _sessions.none { it.id == activeId }) activeId = _sessions.first().id
        emit()
    }

    fun activeSessionId(): String = activeId

    fun get(id: String): Session? = _sessions.find { it.id == id }

    fun setActive(id: String) { activeId = id }

    suspend fun create(agentId: String, providerId: String, model: String): Session {
        return mutex.withLock {
            val session = Session(
                title = "Session ${_sessions.size + 1}",
                agentId = agentId, providerId = providerId, modelId = model
            )
            _sessions.add(session)
            activeId = session.id
            emit()
            persist(session)
            session
        }
    }

    suspend fun update(session: Session) = mutex.withLock {
        val idx = _sessions.indexOfFirst { it.id == session.id }
        val updated = session.copy(updatedAt = System.currentTimeMillis())
        if (idx >= 0) _sessions[idx] = updated else _sessions.add(updated)
        emit()
        persist(updated)
    }

    suspend fun appendMessages(sessionId: String, messages: List<Message>) = mutex.withLock {
        val idx = _sessions.indexOfFirst { it.id == sessionId }
        if (idx < 0) return@withLock
        val existing = _sessions[idx]
        val merged = existing.messages + messages
        val title = refineTitle(existing, merged)
        val updated = existing.copy(messages = merged, title = title, updatedAt = System.currentTimeMillis())
        _sessions[idx] = updated
        emit()
        persist(updated)
    }

    suspend fun setMessages(sessionId: String, messages: List<Message>) = mutex.withLock {
        val idx = _sessions.indexOfFirst { it.id == sessionId }
        if (idx < 0) return@withLock
        val updated = _sessions[idx].copy(messages = messages, updatedAt = System.currentTimeMillis())
        _sessions[idx] = updated
        emit()
        persist(updated)
    }

    suspend fun setTodos(sessionId: String, todos: List<com.opendroid.data.model.Todo>) = mutex.withLock {
        val idx = _sessions.indexOfFirst { it.id == sessionId }
        if (idx < 0) return@withLock
        _sessions[idx] = _sessions[idx].copy(todos = todos)
        emit()
        persist(_sessions[idx])
    }

    suspend fun rename(sessionId: String, title: String) = mutex.withLock {
        val idx = _sessions.indexOfFirst { it.id == sessionId }
        if (idx >= 0) {
            _sessions[idx] = _sessions[idx].copy(title = title)
            emit(); persist(_sessions[idx])
        }
    }

    suspend fun delete(sessionId: String) = mutex.withLock {
        _sessions.removeAll { it.id == sessionId }
        withContext(Dispatchers.IO) { File(dir, "$sessionId.json").delete() }
        if (_sessions.isEmpty()) _sessions.add(Session(id = "default", title = "Session 1"))
        if (activeId == sessionId) activeId = _sessions.first().id
        emit()
    }

    private fun refineTitle(existing: Session, messages: List<Message>): String {
        if (existing.title != "Nouvelle session" && !existing.title.startsWith("Session ")) return existing.title
        val firstUser = messages.firstOrNull { it.role == com.opendroid.data.model.Role.USER }?.text?.trim()
        if (firstUser.isNullOrBlank()) return existing.title
        val oneLine = firstUser.replace("\n", " ")
        return if (oneLine.length <= 42) oneLine else oneLine.take(42) + "…"
    }

    private fun persist(session: Session) {
        try {
            File(dir, "${session.id}.json").writeText(gson.toJson(session))
        } catch (_: Exception) {
        }
    }

    suspend fun persistAll() = mutex.withLock {
        withContext(Dispatchers.IO) { _sessions.forEach { persist(it) } }
    }
}
