package sanex.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Thin wrapper around Android TextToSpeech set to Japanese.
 * Swap the internals later for a fully offline engine (Piper / sherpa-onnx)
 * without touching any callers.
 */
class TtsEngine(
    context: Context,
    private val onSpeakingChanged: (Boolean) -> Unit = {}
) {
    private var ready   = false
    private var pending: String? = null

    private lateinit var tts:TextToSpeech;

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.JAPANESE)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.e("Assistant", "TTS: Japanese voice not available (result=$result). " +
                        "Install it via Settings → General management → TTS output.")
            } else {
                ready = true
                Log.d("Assistant", "TTS ready (Japanese)")
                pending?.let { speak(it) }
                pending = null
            }
        } else {
            Log.e("Assistant", "TTS init failed (status=$status)")
        }
    }


        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?)  = onSpeakingChanged(true)
            override fun onDone(utteranceId: String?)   = onSpeakingChanged(false)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = onSpeakingChanged(false)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?)  = onSpeakingChanged(false)
        })
    }

    /** Speak [text] in Japanese. Queues the text if the engine isn't ready yet. */
    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            pending = text
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mivi-${System.nanoTime()}")
    }

    fun stop() = tts.stop()

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}