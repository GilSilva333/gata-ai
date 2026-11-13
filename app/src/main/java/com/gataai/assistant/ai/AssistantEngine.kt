
package com.gataai.assistant.ai

import com.gataai.assistant.core.ConversationMessage

interface AssistantEngine {

    suspend fun generateResponse(
        conversation: List<ConversationMessage>
    ): String

    suspend fun initialize(): Boolean

    fun isReady(): Boolean

    fun release()
}
