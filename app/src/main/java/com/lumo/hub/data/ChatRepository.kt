package com.lumo.hub.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ChatRole(val label: String) { NONE("Без роли"), MENTOR("Наставник"), TRANSLATOR("Переводчик"), CODER("Программист") }
data class ChatSummary(val id: String, val title: String, val preview: String, val time: String, val role: ChatRole = ChatRole.NONE)
data class ChatMessage(val id: String, val text: String, val isUser: Boolean, val time: String)
data class Conversation(val id: String, val title: String, val role: ChatRole = ChatRole.NONE, val messages: List<ChatMessage>)

/** Local-first chat store. SharedPreferences keeps the MVP usable without a network. */
class ChatRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("lumo_chats", Context.MODE_PRIVATE)
    private val _conversations = MutableStateFlow(load().map(::summary))
    val chats: StateFlow<List<ChatSummary>> = _conversations.asStateFlow()

    fun conversation(id: String): Conversation = load().firstOrNull { it.id == id }
        ?: Conversation(id, "Новый чат", messages = emptyList())

    fun newChat(): ChatSummary {
        val chat = Conversation("chat-${System.currentTimeMillis()}", "Новый чат", messages = emptyList())
        save(listOf(chat) + load()); return summary(chat)
    }

    fun deleteChat(id: String) { save(load().filterNot { it.id == id }) }

    fun setRole(id: String, role: ChatRole) { save(load().map { if (it.id == id) it.copy(role = role) else it }) }

    fun appendMessage(id: String, text: String, isUser: Boolean): ChatMessage {
        val now = clock(); val message = ChatMessage("message-${System.nanoTime()}", text, isUser, now)
        val all = load().map { if (it.id == id) it.copy(messages = it.messages + message, title = if (it.title == "Новый чат" && isUser) autoTitle(text) else it.title) else it }
        save(all); return message
    }

    fun updateMessage(id: String, messageId: String, text: String) { save(load().map { c -> if (c.id == id) c.copy(messages = c.messages.map { if (it.id == messageId) it.copy(text = text) else it }) else c }) }

    private fun save(items: List<Conversation>) { prefs.edit().putString(KEY, JSONArray().apply { items.forEach { put(encode(it)) } }.toString()).apply(); _conversations.value = items.map(::summary) }
    private fun load(): List<Conversation> = runCatching {
        val raw = prefs.getString(KEY, null) ?: return@runCatching defaultChats().map { Conversation(it.id, it.title, it.role, emptyList()) }
        JSONArray(raw).let { a -> (0 until a.length()).map { decode(a.getJSONObject(it)) } }
    }.getOrElse { emptyList() }
    private fun encode(c: Conversation) = JSONObject().apply { put("id", c.id); put("title", c.title); put("role", c.role.name); put("messages", JSONArray().apply { c.messages.forEach { put(JSONObject().apply { put("id", it.id); put("text", it.text); put("user", it.isUser); put("time", it.time) }) } }) }
    private fun decode(o: JSONObject): Conversation = Conversation(o.getString("id"), o.getString("title"), runCatching { ChatRole.valueOf(o.optString("role")) }.getOrDefault(ChatRole.NONE), (0 until o.optJSONArray("messages").length()).map { val m = o.getJSONArray("messages").getJSONObject(it); ChatMessage(m.getString("id"), m.getString("text"), m.getBoolean("user"), m.getString("time")) })
    private fun summary(c: Conversation) = ChatSummary(c.id, c.title, c.messages.lastOrNull()?.text ?: "Черновик первого сообщения…", c.messages.lastOrNull()?.time ?: "только что", c.role)
    private fun autoTitle(text: String) = text.trim().replace(Regex("\\s+"), " ").take(36).let { if (text.trim().length > 36) "$it…" else it }.ifBlank { "Новый чат" }
    private fun clock() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    companion object {
        private const val KEY = "conversations"
        private fun defaultChats() = listOf(ChatSummary("chat-1", "Идеи для утренних ритуалов", "Вот три спокойных сценария, с которых можно начать…", "10:24"), ChatSummary("chat-2", "План на неделю", "Разбил задачи на три блока: дом, работа, отдых…", "Вчера"), ChatSummary("chat-3", "Объясни SSE простыми словами", "Server-Sent Events — это когда сервер сам присылает…", "Пн"), ChatSummary("chat-4", "Рецепт тыквенного супа", "Возьми 500 г тыквы, одну луковицу и…", "Сб", ChatRole.MENTOR))
    }
}
