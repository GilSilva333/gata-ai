
package com.gataai.assistant

import android.Manifest
import android.app.Activity
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
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var status: TextView
    private lateinit var conversation: TextView
    private var voiceReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
        }

        val title = TextView(this).apply {
            text = "GATA AI 🐱"
            textSize = 32f
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            text = "Estou aqui. Vamos conversar!"
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        conversation = TextView(this).apply {
            text = "Gata: Oi, meu bem! Como estás?"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        val button = Button(this).apply {
            text = "🎙️ Conversar com a Gata"
            setOnClickListener {
                startListening()
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(conversation)
        layout.addView(button)

        setContentView(layout)

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
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
            status.text = "O reconhecimento de voz não está disponível."
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                status.text = "Estou a ouvir-te..."
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                status.text = "A processar a tua voz..."
            }

            override fun onError(error: Int) {
                status.text = "Não consegui perceber. Tenta novamente."
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )

                val message = matches?.firstOrNull()

                if (message != null) {
                    respondTo(message)
                } else {
                    status.text = "Não percebi. Podes repetir?"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
            return
        }

        if (speechRecognizer == null) {
            prepareRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        try {
            speechRecognizer?.startListening(intent)
            status.text = "A preparar o microfone..."
        } catch (e: Exception) {
            status.text = "Não consegui iniciar o microfone."
        }
    }

    private fun respondTo(message: String) {
        val input = message.lowercase(Locale.ROOT)

        val answer = when {
            input.contains("oi") || input.contains("olá") ->
                "Oiii! Que bom falar contigo! Como estás?"

            input.contains("tudo bem") ->
                "Por aqui está tudo bem! E contigo?"

            input.contains("teu nome") || input.contains("quem és") ->
                "Sou a Gata, tua assistente pessoal!"

            input.contains("obrigado") || input.contains("obrigada") ->
                "Não tens de quê, meu bem!"

            input.contains("tchau") || input.contains("adeus") ->
                "Até logo! Vou ficar por aqui."

            else ->
                "Entendi! Conta-me mais um bocadinho sobre isso."
        }

        conversation.text = "Tu: $message\n\nGata: $answer"
        speak(answer)
        status.text = "Conversa iniciada ❤️"
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
            voiceReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            prepareRecognizer()
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
