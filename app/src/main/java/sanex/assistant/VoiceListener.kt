package sanex.assistant

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import kotlin.math.sqrt

/** Short feedback beeps: listening started, understood, not understood. */
object Earcon {
    private val handler = Handler(Looper.getMainLooper())

    fun start() = play(ToneGenerator.TONE_PROP_BEEP, 120)
    fun ok()    = play(ToneGenerator.TONE_PROP_ACK, 150)
    fun fail()  = play(ToneGenerator.TONE_PROP_NACK, 200)

    private fun play(tone: Int, ms: Int) {
        try {
            val generator = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            generator.startTone(tone, ms)
            handler.postDelayed({ generator.release() }, ms + 200L)
        } catch (e: Exception) {
            Log.w("Assistant", "Could not play tone: ${e.message}")
        }
    }
}

/**
 * Records from the microphone and feeds Vosk. Replaces Vosk's SpeechService so we get
 * a mic level for the UI, per-word confidence, and control over timeouts.
 *
 * All callbacks arrive on the main thread. After stop() no more callbacks are delivered.
 */
class VoiceListener(
    private val model: Model,
    private val callbacks: Callbacks,
    private val noSpeechTimeoutMs: Long = 6_000,
    private val maxDurationMs: Long = 12_000
) {
    interface Callbacks {
        fun onLevel(level: Float)                       // 0..1, about 10 times a second
        fun onPartial(text: String)
        fun onResult(text: String, confidence: Float?)  // end of a sentence
        fun onTimeout()                                 // nothing was said
        fun onError(message: String)
    }

    @Volatile private var running = false
    private val main = Handler(Looper.getMainLooper())

    fun start() {
        running = true
        Thread({ loop() }, "mivi-listener").start()
    }

    /** Non-blocking. The listener thread releases the mic and recognizer itself. */
    fun stop() {
        running = false
    }

    private fun loop() {
        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            postError("This device can't record at 16 kHz")
            return
        }

        var record: AudioRecord? = null
        var recognizer: Recognizer? = null
        try {
            record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, sampleRate * 2)
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                postError("Could not open the microphone")
                return
            }

            recognizer = Recognizer(model, sampleRate.toFloat())
            recognizer.setWords(true)            // adds per-word confidence to results

            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                postError("The microphone is busy")
                return
            }

            val buffer = ShortArray(sampleRate / 10)      // 100 ms of audio
            val startedAt = SystemClock.elapsedRealtime()
            var heardSpeech = false

            while (running) {
                val n = record.read(buffer, 0, buffer.size)
                if (n < 0) {
                    postError("Microphone read error ($n)")
                    return
                }
                if (n == 0) continue

                postLevel(rms(buffer, n))

                if (recognizer.acceptWaveForm(buffer, n)) {
                    // Vosk detected the end of a sentence.
                    val json = recognizer.result
                    val text = field(json, "text")
                    if (text.isNotBlank()) {
                        postResult(text, confidence(json))
                        return
                    }
                } else {
                    val partial = field(recognizer.partialResult, "partial")
                    if (partial.isNotBlank()) {
                        heardSpeech = true
                        postPartial(partial)
                    }
                }

                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if ((!heardSpeech && elapsed > noSpeechTimeoutMs) || elapsed > maxDurationMs) {
                    val json = recognizer.finalResult
                    val text = field(json, "text")
                    if (text.isNotBlank()) postResult(text, confidence(json)) else postTimeout()
                    return
                }
            }
        } catch (e: Exception) {
            postError(e.message ?: "Listening failed")
        } finally {
            try { record?.stop() } catch (e: Exception) { /* already stopped */ }
            record?.release()
            recognizer?.close()
        }
    }

    private fun rms(buffer: ShortArray, n: Int): Float {
        var sum = 0.0
        for (i in 0 until n) {
            val v = buffer[i].toDouble()
            sum += v * v
        }
        val r = sqrt(sum / n) / 32768.0
        return (r * 10.0).coerceIn(0.0, 1.0).toFloat()    // raw speech is quiet, so scale it up for display
    }

    private fun field(json: String?, key: String): String {
        if (json == null) return ""
        return try { JSONObject(json).optString(key, "") } catch (e: Exception) { "" }
    }

    /** Average of Vosk's per-word confidence, 0..1, or null if there are no words. */
    private fun confidence(json: String?): Float? {
        if (json == null) return null
        return try {
            val words = JSONObject(json).optJSONArray("result") ?: return null
            if (words.length() == 0) return null
            var sum = 0.0
            for (i in 0 until words.length()) sum += words.getJSONObject(i).optDouble("conf", 0.0)
            (sum / words.length()).toFloat().coerceIn(0f, 1f)
        } catch (e: Exception) {
            null
        }
    }

    private fun postLevel(level: Float) { main.post { if (running) callbacks.onLevel(level) } }
    private fun postPartial(text: String) { main.post { if (running) callbacks.onPartial(text) } }
    private fun postResult(text: String, conf: Float?) { main.post { if (running) callbacks.onResult(text, conf) } }
    private fun postTimeout() { main.post { if (running) callbacks.onTimeout() } }
    private fun postError(message: String) { main.post { if (running) callbacks.onError(message) } }
}
