
package com.gataai.assistant.core

data class ConversationMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ConversationMemory(
    private val maxMessages: Int = 30
) {

    private val messages = mutableListOf<ConversationMessage>()

    fun addUserMessage(content: String) {
        addMessage("user", content)
    }

    fun addAssistantMessage(content: String) {
        addMessage("assistant", content)
    }

    private fun addMessage(role: String, content: String) {
        if (content.isBlank()) return

        messages.add(
            ConversationMessage(
                role = role,
                content = content.trim()
            )
        )

        while (messages.size > maxMessages) {
            messages.removeAt(0)
        }
    }

    fun getMessages(): List<ConversationMessage> {
        return messages.toList()
    }

    fun getLastMessage(): ConversationMessage? {
        return messages.lastOrNull()
    }

    fun clear() {
        messages.clear()
    }

    fun getConversationText(): String {
        return messages.joinToString("\n") {
            "${it.role}: ${it.content}"
        }
    }
}
