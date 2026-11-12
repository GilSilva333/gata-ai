
package com.gataai.assistant

import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var voiceReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "GATA AI 🐱"
            textSize = 32f
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Oi! Eu sou a tua assistente."
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 32)
        }

        val button = Button(this).apply {
            text = "Conhecer a minha voz"
            setOnClickListener {
                if (voiceReady) {
                    tts?.speak(
                        "Oi, meu bem! Eu sou a Gata. Estou chegando para conversar contigo!",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "gata_intro"
                    )
                } else {
                    text = "A preparar a voz..."
                }
            }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(button)

        setContentView(layout)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("pt", "BR"))
            voiceReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
