
package com.gataai.assistant.ai

import android.content.Context
import com.gataai.assistant.core.ConversationMessage
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

class LiteRtAssistantEngine(
    private val context: Context
) : AssistantEngine {

    private val modelFile = File(
        context.filesDir,
        "models/Qwen3-0.6B.litertlm"
    )

    private val dispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private var engine: Engine? = null

    @Volatile
    private var ready = false

    @Volatile
    private var lastInitializationError: String? = null

    fun getInitializationError(): String? =
        lastInitializationError

    override suspend fun initialize(): Boolean =
        withContext(dispatcher) {

            if (ready) return@withContext true

            if (!modelFile.exists() || modelFile.length() == 0L) {
                lastInitializationError =
                    "Ficheiro do modelo inexistente ou vazio."
                return@withContext false
            }

            var candidate: Engine? = null

            try {
                candidate = Engine(
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = File(
                            context.cacheDir,
                            "litert-cache"
                        ).absolutePath
                    )
                )

                candidate.initialize()

                engine = candidate
                ready = true
                lastInitializationError = null

                true

            } catch (e: Exception) {
                ready = false
                engine = null

                lastInitializationError =
                    "${e.javaClass.simpleName}: ${e.message ?: "Erro sem descrição"}"

                try {
                    candidate?.close()
                } catch (_: Exception) {
                }

                false

            } catch (e: LinkageError) {
                ready = false
                engine = null

                lastInitializationError =
                    "${e.javaClass.simpleName}: ${e.message ?: "Erro de biblioteca nativa"}"

                false
            }
        }

    override suspend fun generateResponse(
        conversation: List<ConversationMessage>
    ): String = withContext(dispatcher) {

        val activeEngine = engine
            ?: throw IllegalStateException(
                "O cérebro ainda não foi inicializado."
            )

        if (!ready) {
            throw IllegalStateException(
                "O modelo ainda não está pronto."
            )
        }

        val lastUserIndex = conversation.indexOfLast {
            it.role == "user"
        }

        if (lastUserIndex < 0) {
            return@withContext "Ainda não recebi uma mensagem tua."
        }

        val previousMessages = conversation
            .take(lastUserIndex)
            .takeLast(12)

        val initialMessages = previousMessages.mapNotNull {
            when (it.role) {
                "user" -> Message.user(it.content)
                "assistant" -> Message.model(it.content)
                else -> null
            }
        }

        val config = ConversationConfig(
            systemInstruction = Contents.of(
                """
                O teu nome é Gata.
                És uma assistente pessoal feminina.
                Conversas em português brasileiro natural,
                com um jeito amigável, carinhoso e descontraído.
                Responde com clareza, inteligência e naturalidade.
                Não inventes informações sobre o utilizador.
                """.trimIndent()
            ),
            initialMessages = initialMessages
        )

        activeEngine.createConversation(config).use { chat ->

            val response = chat.sendMessage(
                conversation[lastUserIndex].content
            )

            val answer = response.toString()

            answer.ifBlank {
                "Desculpa, tive dificuldade em formular uma resposta."
            }
        }
    }

    override fun isReady(): Boolean = ready

    override fun release() {
        runBlocking(dispatcher) {
            ready = false
            engine?.close()
            engine = null
        }

        dispatcher.close()
    }
}
