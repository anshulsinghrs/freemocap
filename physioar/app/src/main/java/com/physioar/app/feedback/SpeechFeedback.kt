package com.physioar.app.feedback

import android.content.Context
import android.speech.tts.TextToSpeech
import com.physioar.engine.exercise.Feedback
import com.physioar.engine.exercise.FeedbackCode
import com.physioar.engine.exercise.Severity
import java.util.Locale

/**
 * Spoken cues via on-device Android TextToSpeech. The engine already throttles repeats;
 * this additionally avoids talking over itself and skips the "Hold…" tick.
 */
class SpeechFeedback(context: Context) {
    @Volatile private var ready = false
    private var lastSpokenAtMs = 0L
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.getDefault()
                ready = true
            }
        }
    }

    fun speak(feedback: List<Feedback>) {
        if (!ready || feedback.isEmpty()) return
        val f = feedback.filterNot { it.code == FeedbackCode.HOLD_POSITION && it.message.endsWith("…") }
            .maxByOrNull { it.severity.ordinal } ?: return
        val now = System.currentTimeMillis()
        val urgent = f.severity >= Severity.WARNING
        if (!urgent && tts.isSpeaking) return
        if (!urgent && now - lastSpokenAtMs < 1500) return
        lastSpokenAtMs = now
        tts.speak(f.message, if (urgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, f.code.name)
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
