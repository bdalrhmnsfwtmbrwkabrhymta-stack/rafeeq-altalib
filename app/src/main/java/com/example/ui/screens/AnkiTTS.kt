package com.example.ui.screens

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object AnkiTTS : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("ar") // Default to Arabic, or can be changed
            isReady = true
        }
    }

    fun speak(text: String) {
        if (isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun stop() {
        tts?.stop()
    }
}
