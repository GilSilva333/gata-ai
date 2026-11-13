
package com.gataai.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import com.gataai.assistant.ai.LiteRtAssistantEngine
import com.gataai.assistant.core.ConversationMemory
import com.gataai.assistant.core.MemoryStore
import com.gataai.assistant.core.ModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    companion object {
        private const val MICROPHONE_REQUEST = 100
        private const val MODEL_PICKER_REQUEST = 200
    }

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var voiceReady = false
    private var isGenerating = false

    private var aiEngine: LiteRtAssistantEngine? = null

    private lateinit var status: TextView
    private lateinit var conversation: TextView
    private lateinit var memoryStore: MemoryStore
    private lateinit var modelStore: ModelStore

    private val memory = ConversationMemory()

    private val activityScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        memoryStore = MemoryStore(this)
        modelStore = ModelStore(this)

        memoryStore.loadConversation().forEach { message ->
            when (message.role) {
                "user" -> memory.addUserMessage(message.content)
                "assistant" -> memory.addAssistantMessage(message.content)
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

        val importButton = Button(this).apply {
            text = "🧠 Importar cérebro"
            setOnClickListener {
                openModelPicker()
            }
        }

        val voiceButton = Button(this).apply {
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

        layout.addView(importButton)
        layout.addView(voiceButton)
        layout.addView(clearButton)

        setContentView(layout)

        updateConversation()
        updateModelStatus()
        requestMicrophonePermission()

        if (modelStore.hasModel()) {
            activityScope.launch {
                activateAiEngine()
            }
        }
    }

    private fun openModelPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        try {
            startActivityForResult(intent, MODEL_PICKER_REQUEST)
        } catch (e: Exception) {
            status.text = "Não consegui abrir os ficheiros: ${e.message}"
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            requestCode == MODEL_PICKER_REQUEST &&
            resultCode == Activity.RESULT_OK
        ) {
            val uri = data?.data

            if (uri == null) {
                status.text = "Não consegui localizar o ficheiro."
                return
            }

            importModel(uri)
        }
    }

    private fun importModel(uri: Uri) {
        status.text = "A importar o cérebro... aguarda ❤️"

        activityScope.launch {
            try {
                modelStore.importModel(uri)

                status.text = "Modelo guardado! A preparar a inteligência..."

                val activated = activateAiEngine()

                if (activated) {
                    status.text = "A GATA está pronta! 🧠❤️"
                }

                updateConversation()

            } catch (e: Exception) {
                status.text =
                    "Falha na importação: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    private suspend fun activateAiEngine(): Boolean {
        if (!modelStore.hasModel()) {
            status.text = "Ficheiro do modelo não encontrado."
            return false
        }

        var engine = aiEngine

        if (engine == null) {
            engine = LiteRtAssistantEngine(this)
            aiEngine = engine
        }

        if (engine.isReady()) {
            return true
        }

        status.text = "A carregar o cérebro da GATA... 🧠"

        val initialized = engine.initialize()

        if (!initialized) {
            val error = engine.getInitializationError()
                ?: "Causa não identificada."

            engine.release()

            if (aiEngine === engine) {
                aiEngine = null
            }

            status.text = "ERRO AO CARREGAR O CÉREBRO:\n$error"
            return false
        }

        status.text = "Cérebro ativo! ❤️"
        return true
    }

    private fun updateModelStatus() {
        status.text = if (modelStore.hasModel()) {
            "Cérebro encontrado! A preparar..."
        } else {
            "Estou aqui contigo ❤️"
        }
    }

    private fun requestMicrophonePermission() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MICROPHONE_REQUEST
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

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(
            object : RecognitionListener {

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
                    status.text = when (error) {
                        SpeechRecognizer.ERROR_AUDIO ->
                            "Erro no áudio. Verifica o microfone."

                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Falha no reconhecimento de voz ou na rede."

                        SpeechRecognizer.ERROR_NO_MATCH ->
                            "Não consegui reconhecer as palavras. Tenta novamente."

                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "Não detetei fala. Podes tentar novamente."

                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Permissão do microfone em falta."

                        else ->
                            "Erro no reconhecimento de voz: $error"
                    }
                }

                override fun onResults(results: Bundle?) {
                    val message = results
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        ?.firstOrNull()

                    if (!message.isNullOrBlank()) {
                        respondTo(message)
                    } else {
                        status.text = "Podes repetir, meu bem?"
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            }
        )
    }

    private fun startListening() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicrophonePermission()
            return
        }

        if (isGenerating) {
            status.text = "Espera só um bocadinho, estou a responder ❤️"
            return
        }

        if (recognizer == null) {
            prepareRecognizer()
        }

        val activeRecognizer = recognizer

        if (activeRecognizer == null) {
            status.text = "Não consegui preparar o reconhecimento de voz."
            return
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
            activeRecognizer.startListening(intent)
            status.text = "A preparar..."
        } catch (e: Exception) {
            status.text = "Não consegui iniciar o microfone: ${e.message}"
        }
    }

    private fun respondTo(message: String) {
        if (isGenerating) {
            status.text = "Ainda estou a responder à mensagem anterior."
            return
        }

        val previousMessage = memory.getMessages()
            .lastOrNull { it.role == "user" }
            ?.content ?: ""

        memory.addUserMessage(message)
        memoryStore.saveConversation(memory.getMessages())
        updateConversation()

        isGenerating = true
        status.text = "A GATA está a pensar... 🧠"

        activityScope.launch {
            try {
                val answer: String

                if (modelStore.hasModel()) {
                    val activated = activateAiEngine()

                    if (!activated) {
                        val error = aiEngine?.getInitializationError()
                            ?: "Verifica a mensagem de erro apresentada no ecrã."

                        answer = "Ainda não consegui ativar o meu cérebro. $error"
                    } else {
                        val activeEngine = aiEngine
                            ?: throw IllegalStateException(
                                "O motor de IA não está disponível."
                            )

                        answer = activeEngine.generateResponse(
                            memory.getMessages()
                        )
                    }
                } else {
                    answer = scriptedResponse(message, previousMessage)
                }

                memory.addAssistantMessage(answer)
                memoryStore.saveConversation(memory.getMessages())
                updateConversation()

                status.text = if (aiEngine?.isReady() == true) {
                    "Cérebro ativo ❤️"
                } else {
                    "Modo básico ativo. O cérebro ainda não está disponível."
                }

                speak(answer)

            } catch (e: Exception) {
                val error =
                    "${e.javaClass.simpleName}: ${e.message ?: "Erro desconhecido"}"

                status.text = "Erro ao gerar resposta: $error"

                val messageError =
                    "Desculpa, ocorreu um erro ao responder. $error"

                memory.addAssistantMessage(messageError)
                memoryStore.saveConversation(memory.getMessages())
                updateConversation()
                speak(messageError)

            } finally {
                isGenerating = false
            }
        }
    }

    private fun scriptedResponse(
        message: String,
        previousMessage: String
    ): String {
        val input = message.lowercase(Locale.ROOT)

        return when {
            input.contains("oi") ||
            input.contains("olá") ||
            input.contains("bom dia") ||
            input.contains("boa noite") ->
                "Oiii! Que bom ter-te aqui. Como estás hoje?"

            input.contains("quem és") ||
            input.contains("teu nome") ->
                "Sou a Gata! A tua assistente pessoal. Ainda estamos a construir a minha inteligência."

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
            .setMessage("Queres mesmo apagar todas as mensagens guardadas?")
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
            val result = tts?.setLanguage(Locale("pt", "BR"))

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

        if (
            requestCode == MICROPHONE_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            prepareRecognizer()
        }
    }

    override fun onDestroy() {
        activityScope.cancel()

        recognizer?.destroy()
        recognizer = null

        tts?.stop()
        tts?.shutdown()
        tts = null

        aiEngine?.release()
        aiEngine = null

        super.onDestroy()
    }
}
          Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicrophonePermission()
            return
        }

        if (isGenerating) {
            status.text = "Espera só um bocadinho, estou a responder ❤️"
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
            status.text = "Não consegui iniciar o microfone: ${e.message}"
        }
    }

    private fun respondTo(message: String) {
        if (isGenerating) {
            status.text = "Ainda estou a responder à mensagem anterior."
            return
        }

        val previousMessage = memory.getMessages()
            .lastOrNull { it.role == "user" }
            ?.content ?: ""

        memory.addUserMessage(message)
        memoryStore.saveConversation(memory.getMessages())
        updateConversation()

        isGenerating = true
        status.text = "A GATA está a pensar... 🧠"

        activityScope.launch {
            try {
                val answer: String

                if (modelStore.hasModel()) {
                    val activated = activateAiEngine()

                    if (!activated) {
                        val error = aiEngine?.getInitializationError()
                            ?: "Verifica a mensagem de erro apresentada no ecrã."

                        answer = "Ainda não consegui ativar o meu cérebro. $error"
                    } else {
                        answer = aiEngine!!.generateResponse(
                            memory.getMessages()
                        )
                    }
                } else {
                    answer = scriptedResponse(message, previousMessage)
                }

                memory.addAssistantMessage(answer)
                memoryStore.saveConversation(memory.getMessages())
                updateConversation()

                status.text = if (aiEngine?.isReady() == true) {
                    "Cérebro ativo ❤️"
                } else {
                    "Modo básico ativo. O cérebro ainda não está disponível."
                }

                speak(answer)

            } catch (e: Exception) {
                val error = "${e.javaClass.simpleName}: ${e.message ?: "Erro desconhecido"}"

                status.text = "Erro ao gerar resposta: $error"

                val messageError = "Desculpa, ocorreu um erro ao responder. $error"
                memory.addAssistantMessage(messageError)
                memoryStore.saveConversation(memory.getMessages())
                updateConversation()
                speak(messageError)

            } finally {
                isGenerating = false
            }
        }
    }

    private fun scriptedResponse(
        message: String,
        previousMessage: String
    ): String {
        val input = message.lowercase(Locale.ROOT)

        return when {
            input.contains("oi") ||
            input.contains("olá") ||
            input.contains("bom dia") ||
            input.contains("boa noite") ->
                "Oiii! Que bom ter-te aqui. Como estás hoje?"

            input.contains("quem és") ||
            input.contains("teu nome") ->
                "Sou a Gata! A tua assistente pessoal. Ainda estamos a construir a minha inteligência."

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
    }

    private fun updateConversation() {
        val messages = memory.getMessages()

        conversation.text = if (messages.isEmpty()) {
            "Gata: Oiii! Podemos conversar."
        } else {
            messages.joinToString("\n\n") { message ->
                val speaker = if (message.role == "user") "Tu" else "Gata"
                "$speaker: ${message.content}"
            }
        }
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle("Apagar histórico?")
            .setMessage("Queres mesmo apagar todas as mensagens guardadas?")
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
            val result = tts?.setLanguage(Locale("pt", "BR"))

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

        if (
            requestCode == MICROPHONE_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            prepareRecognizer()
        }
    }

    override fun onDestroy() {
        activityScope.cancel()

        recognizer?.destroy()
        recognizer = null

        tts?.stop()
        tts?.shutdown()
        tts = null

        aiEngine?.release()
        aiEngine = null

        super.onDestroy()
    }
}
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
        if (isGenerating) {
            status.text = "Ainda estou a responder à mensagem anterior."
            return
        }

        val previousMessage = memory.getMessages()
            .lastOrNull { it.role == "user" }
            ?.content ?: ""

        memory.addUserMessage(message)

        memoryStore.saveConversation(memory.getMessages())
        updateConversation()

        isGenerating = true
        status.text = "A GATA está a pensar... 🧠"

        activityScope.launch {
            val answer = try {
                if (modelStore.hasModel()) {
                    val activated = activateAiEngine()

                    if (activated) {
                        aiEngine!!.generateResponse(
                            memory.getMessages()
                        )
                    } else {
                        scriptedResponse(message, previousMessage)
                    }
                } else {
                    scriptedResponse(message, previousMessage)
                }
            } catch (e: Exception) {
                scriptedResponse(message, previousMessage)
            }

            memory.addAssistantMessage(answer)

            memoryStore.saveConversation(memory.getMessages())
            updateConversation()

            isGenerating = false
            status.text = "Conversa ativa ❤️"

            speak(answer)
        }
    }

    private fun scriptedResponse(
        message: String,
        previousMessage: String
    ): String {
        val input = message.lowercase(Locale.ROOT)

        return when {

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

        if (
            requestCode == MICROPHONE_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            prepareRecognizer()
        }
    }

    override fun onDestroy() {
        activityScope.cancel()

        recognizer?.destroy()

        tts?.stop()
        tts?.shutdown()

        aiEngine?.release()
        aiEngine = null

        super.onDestroy()
    }
}
