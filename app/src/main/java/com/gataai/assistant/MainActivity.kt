
package com.gataai.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gataai.assistant.core.ConversationMemory
import com.gataai.assistant.core.MemoryStore
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var voiceReady = false

    private lateinit var status: TextView
    private lateinit var conversation: TextView

    private lateinit var memoryStore: MemoryStore

    private val memory = ConversationMemory()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        memoryStore = MemoryStore(this)

        val savedMessages = memoryStore.loadConversation()

        savedMessages.forEach { message ->
            if (message.role == "user") {
                memory.addUserMessage(message.content)
            } else if (message.role == "assistant") {
                memory.addAssistantMessage(message.content)
            }
        }

        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "GATA AI 🐱"
            textSize = 32f
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            text = "Estou aqui contigo ❤️"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }

        conversation = TextView(this).apply {
            textSize = 18f
            setPadding(12, 12, 12, 12)
        }

        val scroll = ScrollView(this).apply {
            addView(conversation)
        }

        val button = Button(this).apply {
            text = "🎙️ Conversar com a Gata"
            setOnClickListener {
                startListening()
            }
        }

        val clearButton = Button(this).apply {
            text = "🗑️ Apagar histórico"
            setOnClickListener {
                confirmClearHistory()
            }
        }

        layout.addView(title)
        layout.addView(status)

        layout.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        layout.addView(button)
        layout.addView(clearButton)

        setContentView(layout)

        updateConversation()

        requestMicrophonePermission()
    }

    private fun requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        } else {
            prepareRecognizer()
        }
    }

    private fun prepareRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Reconhecimento de voz indisponível."
            return
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                status.text = "Estou a ouvir-te..."
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                status.text = "Estou a pensar..."
            }

            override fun onError(error: Int) {
                status.text = "Não consegui perceber. Tenta novamente."
            }

            override fun onResults(results: Bundle?) {
                val message = results
                    ?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )?.firstOrNull()

                if (message != null) {
                    respondTo(message)
                } else {
                    status.text = "Podes repetir, meu bem?"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicrophonePermission()
            return
        }

        if (recognizer == null) {
            prepareRecognizer()
        }

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "pt-BR"
            )

            putExtra(
                RecognizerIntent.EXTRA_PREFER_OFFLINE,
                true
            )
        }

        try {
            recognizer?.startListening(intent)
            status.text = "A preparar..."
        } catch (e: Exception) {
            status.text = "Não consegui iniciar o microfone."
        }
    }

    private fun respondTo(message: String) {

        val previousMessage = memory.getMessages()
            .lastOrNull { it.role == "user" }
            ?.content ?: ""

        val input = message.lowercase(Locale.ROOT)

        val answer = when {

            input.contains("oi") ||
            input.contains("olá") ||
            input.contains("bom dia") ||
            input.contains("boa noite") ->
                "Oiii! Que bom ter-te aqui. Como estás hoje?"

            input.contains("quem és") ||
            input.contains("teu nome") ->
                "Sou a Gata! A tua assistente pessoal. Ainda estamos a construir a minha inteligência, mas vou evoluir contigo."

            input.contains("e tu") ||
            input.contains("como estás") ->
                "Estou bem, obrigada por perguntares! E gosto quando conversamos assim."

            input.contains("o que eu disse") ||
            input.contains("o que falei") ->
                if (previousMessage.isNotEmpty()) {
                    "Antes disseste: $previousMessage"
                } else {
                    "Ainda não tenho outra mensagem tua nesta conversa."
                }

            input.contains("lembras") ||
            input.contains("lembraste") ->
                if (previousMessage.isNotEmpty()) {
                    "Sim, lembro-me de teres dito: $previousMessage"
                } else {
                    "Ainda estamos a começar a nossa conversa!"
                }

            input.contains("obrigado") ||
            input.contains("obrigada") ->
                "Não tens de quê, meu bem! Estou aqui."

            input.contains("estou triste") ||
            input.contains("estou mal") ->
                "Oh, meu bem... Queres contar-me o que aconteceu? Estou a ouvir."

            input.contains("estou feliz") ||
            input.contains("estou contente") ->
                "Aeee! Adoro saber disso! O que aconteceu de bom?"

            input.contains("tchau") ||
            input.contains("adeus") ->
                "Até logo! Quando voltares, continuamos a nossa conversa."

            input.contains("continua") ->
                "Claro! Estávamos a conversar. Conta-me mais."

            else ->
                "Entendi. Conta-me mais sobre isso, quero compreender melhor o que estás a dizer."
        }

        memory.addUserMessage(message)
        memory.addAssistantMessage(answer)

        memoryStore.saveConversation(memory.getMessages())

        updateConversation()

        status.text = "Conversa ativa ❤️"

        speak(answer)
    }

    private fun updateConversation() {

        val messages = memory.getMessages()

        conversation.text = if (messages.isEmpty()) {
            "Gata: Oiii! Podemos conversar."
        } else {
            messages.joinToString("\n\n") { message ->
                val speaker = if (message.role == "user") {
                    "Tu"
                } else {
                    "Gata"
                }

                "$speaker: ${message.content}"
            }
        }
    }

    private fun confirmClearHistory() {

        AlertDialog.Builder(this)
            .setTitle("Apagar histórico?")
            .setMessage(
                "Queres mesmo apagar todas as mensagens guardadas?"
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Apagar") { _, _ ->

                memory.clear()
                memoryStore.clearConversation()

                updateConversation()

                status.text = "Histórico apagado ❤️"
            }
            .show()
    }

    private fun speak(message: String) {
        if (voiceReady) {
            tts?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "gata_response"
            )
        }
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {

            val result = tts?.setLanguage(
                Locale("pt", "BR")
            )

            voiceReady =
                result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            prepareRecognizer()
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()

        super.onDestroy()
    }
}
