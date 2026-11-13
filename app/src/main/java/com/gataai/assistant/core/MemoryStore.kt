
package com.gataai.assistant.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class MemoryStore(context: Context) {

    private val preferences = context.getSharedPreferences(
        "gata_ai_memory",
        Context.MODE_PRIVATE
    )

    fun saveConversation(messages: List<ConversationMessage>) {
        val array = JSONArray()

        messages.forEach { message ->
            val item = JSONObject().apply {
                put("role", message.role)
                put("content", message.content)
                put("timestamp", message.timestamp)
            }

            array.put(item)
        }

        preferences.edit()
            .putString("conversation_history", array.toString())
            .apply()
    }

    fun loadConversation(): List<ConversationMessage> {
        val saved = preferences.getString(
            "conversation_history",
            null
        ) ?: return emptyList()

        return try {
            val array = JSONArray(saved)
            val messages = mutableListOf<ConversationMessage>()

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)

                messages.add(
                    ConversationMessage(
                        role = item.getString("role"),
                        content = item.getString("content"),
                        timestamp = item.getLong("timestamp")
                    )
                )
            }

            messages
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearConversation() {
        preferences.edit()
            .remove("conversation_history")
            .apply()
    }
}
