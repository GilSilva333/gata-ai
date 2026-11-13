
package com.gataai.assistant.voice

interface VoiceEngine {

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    )

    fun stopListening()

    fun speak(
        text: String,
        onFinished: (() -> Unit)? = null
    )

    fun stopSpeaking()

    fun isSpeaking(): Boolean

    fun release()
}
