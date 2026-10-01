package com.lumo.hub.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class ChatRole(val label: String) { NONE("Без роли"), MENTOR("Наставник"), TRANSLATOR("Переводчик"), CODER("Программист") }
data class ChatSummary(val id: String, val title: String, val preview: String, val time: String, val role: ChatRole = ChatRole.NONE, val timestamp: Long = 0L)
data class ChatMessage(val id: String, val text: String, val isUser: Boolean, val time: String, val timestamp: Long = 0L)
data class Conversation(val id: String, val title: String, val role: ChatRole = ChatRole.NONE, val model: String = "", val messages: List<ChatMessage>, val timestamp: Long = 0L)

/** Durable local-first store. All instances in one process share the same application store. */
class ChatRepository(context: Context) {
    private val store = stores.getOrPut(context.applicationContext) { Store(context.applicationContext) }
    val chats: StateFlow<List<ChatSummary>> = store.chats

    fun conversation(id: String): Conversation = store.read().firstOrNull { it.id == id }
        ?: Conversation(id, "Новый чат", messages = emptyList())

    fun newChat(): ChatSummary {
        val chat = Conversation("chat-${System.currentTimeMillis()}", "Новый чат", messages = emptyList(), timestamp = System.currentTimeMillis())
        store.write(listOf(chat) + store.read())
        return summary(chat)
    }

    fun deleteChat(id: String) { store.write(store.read().filterNot { it.id == id }) }
    fun setRole(id: String, role: ChatRole) { store.write(store.read().map { if (it.id == id) it.copy(role = role) else it }) }
    fun setModel(id: String, model: String) { store.write(store.read().map { if (it.id == id) it.copy(model = model) else it }) }

    fun appendMessage(id: String, text: String, isUser: Boolean): ChatMessage {
        val timestamp = System.currentTimeMillis()
        val message = ChatMessage("message-${System.nanoTime()}", text, isUser, clock(timestamp), timestamp)
        store.write(store.read().map { if (it.id == id) it.copy(messages = it.messages + message, title = if (it.title == "Новый чат" && isUser) autoTitle(text) else it.title, timestamp = timestamp) else it })
        return message
    }

    fun updateMessage(id: String, messageId: String, text: String) {
        store.write(store.read().map { c -> if (c.id == id) c.copy(messages = c.messages.map { if (it.id == messageId) it.copy(text = text) else it }) else c })
    }

    private fun summary(c: Conversation) = ChatSummary(c.id, c.title, c.messages.lastOrNull()?.text ?: "Черновик первого сообщения…", c.messages.lastOrNull()?.time ?: "только что", c.role, c.timestamp)
    private fun autoTitle(text: String) = text.trim().replace(Regex("\\s+"), " ").take(36).let { if (text.trim().length > 36) "$it…" else it }.ifBlank { "Новый чат" }
    private fun clock(timestamp: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

    private class Store(context: Context) {
        private val lock = ReentrantLock()
        private val file = File(context.filesDir, FILE_NAME)
        private val _chats = MutableStateFlow(emptyList<ChatSummary>())
        val chats: StateFlow<List<ChatSummary>> = _chats.asStateFlow()
        private var items: List<Conversation>

        init {
            items = lock.withLock { loadOrMigrate(context) }
            _chats.value = items.sortedByDescending { it.timestamp }.map(::summary)
        }

        fun read(): List<Conversation> = lock.withLock { items }

        fun write(value: List<Conversation>) = lock.withLock {
            // Replace only after the complete JSON has reached disk. This avoids
            // losing the previous history on process death or a partial write.
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            FileOutputStream(temp).use { output ->
                output.write(encode(value).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            check(temp.renameTo(file)) { "Unable to atomically save chat history" }
            items = value
            _chats.value = value.sortedByDescending { it.timestamp }.map(::summary)
        }

        private fun loadOrMigrate(context: Context): List<Conversation> {
            if (file.exists()) return decode(file.readText())
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val legacy = prefs.getString(KEY, null)
            // Do not seed the durable production store with showcase/demo conversations.
            val value = if (legacy != null) decode(legacy) else emptyList()
            // Migration is complete only after the durable file is written.
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            FileOutputStream(temp).use { output -> output.write(encode(value).toByteArray(Charsets.UTF_8)); output.fd.sync() }
            check(temp.renameTo(file)) { "Unable to migrate chat history" }
            if (legacy != null) prefs.edit().remove(KEY).commit()
            return value
        }

        private fun decode(raw: String): List<Conversation> = runCatching {
            JSONArray(raw).let { a -> (0 until a.length()).mapNotNull { index -> runCatching { decode(a.getJSONObject(index)) }.getOrNull() } }
        }.getOrElse { emptyList() }
        private fun decode(file: File): List<Conversation> = decode(file.readText())
        private fun encode(items: List<Conversation>) = JSONArray().apply { items.forEach { c -> put(JSONObject().apply { put("id", c.id); put("title", c.title); put("role", c.role.name); put("model", c.model); put("timestamp", c.timestamp); put("messages", JSONArray().apply { c.messages.forEach { m -> put(JSONObject().apply { put("id", m.id); put("text", m.text); put("user", m.isUser); put("time", m.time); put("timestamp", m.timestamp) }) } }) }) } }.toString()
        private fun decode(o: JSONObject): Conversation {
            val messages = o.optJSONArray("messages")?.let { array ->
                (0 until array.length()).mapNotNull { index -> runCatching {
                    val m = array.getJSONObject(index)
                    ChatMessage(m.getString("id"), m.getString("text"), m.getBoolean("user"), m.optString("time"), m.optLong("timestamp", 0L))
                }.getOrNull() }
            } ?: emptyList()
            val timestamp = o.optLong("timestamp", 0L).takeIf { it > 0L } ?: messages.maxOfOrNull { it.timestamp } ?: 0L
            return Conversation(o.getString("id"), o.getString("title"), runCatching { ChatRole.valueOf(o.optString("role")) }.getOrDefault(ChatRole.NONE), o.optString("model"), messages, timestamp)
        }
        private fun summary(c: Conversation) = ChatSummary(c.id, c.title, c.messages.lastOrNull()?.text ?: "Черновик первого сообщения…", c.messages.lastOrNull()?.time ?: "только что", c.role, c.timestamp)
    }

    companion object {
        private const val FILE_NAME = "lumo_chats.json"
        private const val PREFS_NAME = "lumo_chats"
        private const val KEY = "conversations"
        private val stores = ConcurrentHashMap<Context, Store>()
        fun get(context: Context): ChatRepository = ChatRepository(context.applicationContext)
    }
}
