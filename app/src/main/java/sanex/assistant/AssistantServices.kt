package sanex.assistant

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * true  = open the overlay as a system "assistant" activity (the usual way).
 * false = open it as a normal translucent activity.
 */
private const val USE_ASSISTANT_TASK = true

/**
 * Declaring this service (plus the XML metadata and manifest entries) is what makes
 * mivi-chan appear under Settings → Apps → Default apps → Digital assistant app.
 */
class VoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        Log.d("Assistant", "VoiceInteractionService ready")
        AssistantEngine.init(application)
        AssistantEngine.onAssistantReady()      // warm up the speech model so the first invocation is fast
    }
}

/** The system asks this service for a new session each time the assistant is invoked. */
class SessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = Session(this)
}

/** Lets the overlay tell the current session that the assistant is finished. */
object AssistantSession {
    @Volatile var current: Session? = null

    fun hide() {
        try {
            current?.hide()
        } catch (e: Exception) {
            Log.w("Assistant", "Could not hide the session: ${e.message}")
        }
    }
}

/**
 * One assistant invocation (corner swipe, power-button hold, headset button...).
 * We don't draw anything here; we open AssistantActivity, which is the overlay.
 */
class Session(context: Context) : VoiceInteractionSession(context) {

    override fun onCreate() {
        super.onCreate()
        // The session has a full-screen window of its own. It was taking window focus away from the
        // overlay (and from the app the overlay opened), so touches never arrived until the
        // recents screen reset things. We draw nothing here, so turn that window off.
        setUiEnabled(false)
        AssistantSession.current = this
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d("Assistant", "Assistant session shown (flags=$showFlags, overlayOpen=${AssistantEngine.isOverlayOpen})")

        // Triggered again while the overlay is already open: just listen again.
        if (AssistantEngine.isOverlayOpen) {
            AssistantEngine.onRetrigger()
            return
        }

        val intent = Intent(context, AssistantActivity::class.java)
            .putExtra(AssistantActivity.EXTRA_FROM_SESSION, true)
        if (USE_ASSISTANT_TASK) {
            startAssistantActivity(intent)
        } else {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onHide() {
        super.onHide()
        Log.d("Assistant", "Assistant session hidden")
    }

    override fun onTaskFinished(intent: Intent?, taskId: Int) {
        super.onTaskFinished(intent, taskId)
        hide()
    }

    override fun onDestroy() {
        if (AssistantSession.current === this) AssistantSession.current = null
        super.onDestroy()
    }
}

/**
 * Android requires a RecognitionService to be named in the assistant metadata.
 * Our real speech recognition is Vosk inside the app, so this one just reports an error.
 */
class RecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) {}

    override fun onStopListening(listener: Callback?) {}
}
