package sanex.assistant

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.vosk.Model

/** Creates the engine as soon as the process starts. Register it as android:name in the manifest. */
class AssistantApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AssistantEngine.init(this)
    }
}

/** A question mivi-chan has asked out loud and is waiting for はい / いいえ on. */
class PendingConfirm(val question: String, val onYes: () -> Unit)

/** Everything the screens can observe. Compose redraws when any of these change. */
class MutableAssistantState {
    var activeModelId      by mutableStateOf<String?>(null)
    var modelLoaded        by mutableStateOf(false)
    var modelLoading       by mutableStateOf(false)
    var status             by mutableStateOf("起動中… (Kido-chuu — Starting…)")

    var isListening        by mutableStateOf(false)
    var isSpeaking         by mutableStateOf(false)
    var isRunning          by mutableStateOf(false)
    var isEnrolling        by mutableStateOf(false)
    var needsMicPermission by mutableStateOf(false)

    var micLevel           by mutableStateOf(0f)
    var partialText        by mutableStateOf("")
    var partialRomaji      by mutableStateOf("")
    var resultText         by mutableStateOf("")
    var resultRomaji       by mutableStateOf("")
    var heardConfidence    by mutableStateOf<Float?>(null)
    var matchStatus        by mutableStateOf("")

    /** Set while mivi-chan is waiting for a spoken yes or no. */
    var confirm            by mutableStateOf<PendingConfirm?>(null)

    /** Non-zero = the overlay should close shortly. Set back to 0 when a new interaction starts. */
    var closeAt            by mutableStateOf(0L)

    var appCommandsEnabled by mutableStateOf(false)
    var keepModelLoaded    by mutableStateOf(true)

    /** Counts finger-downs that reach the overlay window (temporary debugging aid). */
    var debugTaps          by mutableStateOf(0)

    /** Installed apps with a launcher icon, loaded in the background. */
    var apps               by mutableStateOf<List<AppInfo>>(emptyList())

    val routines = mutableStateListOf<Routine>()
    val appAliases = mutableStateMapOf<String, List<String>>()
}

/**
 * The one shared brain: speech model, microphone, TTS, routines and matching.
 * Lives as long as the app process, so the settings app and the overlay use the same loaded model.
 * Call everything from the main thread.
 */
object AssistantEngine {
    private const val TAG = "Assistant"
    private const val KEY_ACTIVE_MODEL = "active_model"
    private const val KEY_APP_COMMANDS = "app_commands"
    private const val KEY_KEEP_MODEL = "keep_model"
    private const val READY = "準備完了 (Junbi kanryou — Ready)"
    private const val EARCON_DELAY_MS = 250L       // let the start beep finish before the mic opens
    private const val UNLOAD_DELAY_MS = 30_000L    // when "keep model in memory" is off
    private const val CLOSE_DELAY_MS = 2_000L      // overlay stays this long after finishing

    lateinit var app: Application
        private set

    val state = MutableAssistantState()

    /** True while the assistant overlay is on screen. */
    @Volatile var isOverlayOpen = false
        private set

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: SharedPreferences
    private lateinit var runner: RoutineRunner
    private var tts: TtsEngine? = null

    private var model: Model? = null
    private var loadingModelId: String? = null
    private var pendingListen = false

    private var listener: VoiceListener? = null
    private var beginRunnable: Runnable? = null
    private var enrollCallback: ((String) -> Unit)? = null
    private var runJob: Job? = null
    private var refreshingApps = false

    // Spoken yes/no confirmation
    private var listenAfterSpeech = false
    private var questionSpeechStarted = false
    private var confirmRetries = 0

    private val unloadRunnable = Runnable {
        if (!state.keepModelLoaded && !state.isListening) unloadModel()
    }
    private val speechStartCheck = Runnable {
        if (listenAfterSpeech && !questionSpeechStarted) beginAnswerListening()   // TTS never started: listen anyway
    }
    private val speechHardLimit = Runnable {
        if (listenAfterSpeech) beginAnswerListening()
    }

    private var initialized = false

    fun init(application: Application) {
        if (initialized) return
        initialized = true
        app = application
        prefs = application.getSharedPreferences("Assistant", Context.MODE_PRIVATE)

        state.appCommandsEnabled = prefs.getBoolean(KEY_APP_COMMANDS, false)
        state.keepModelLoaded = prefs.getBoolean(KEY_KEEP_MODEL, true)
        state.routines.addAll(RoutineStore.load(application))
        state.appAliases.putAll(AppAliasStore.load(application))
        state.activeModelId = preferredModelId()
        state.status = READY

        tts = TtsEngine(application) { speaking ->
            main.post {
                state.isSpeaking = speaking
                if (speaking) {
                    state.status = "話しています… (Hanashite imasu — Speaking…)"
                    if (listenAfterSpeech) questionSpeechStarted = true
                } else if (listenAfterSpeech && questionSpeechStarted) {
                    beginAnswerListening()                    // the question is finished: now listen for はい / いいえ
                } else if (!state.isListening && !state.isRunning) {
                    state.status = READY
                }
            }
        }
        runner = RoutineRunner(application, speak = { tts?.speak(it) }, isSpeaking = { state.isSpeaking })

        refreshApps()
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun onMicPermissionResult(granted: Boolean) {
        state.needsMicPermission = !granted
    }

    // ── Installed apps ───────────────────────────────────────────────────────

    /** Reading every app's label is slow (it froze the screen for about a second), so do it off the main thread. */
    fun refreshApps() {
        if (refreshingApps) return
        refreshingApps = true
        Thread {
            val list = try { loadLaunchableApps(app) } catch (e: Exception) { emptyList() }
            main.post {
                state.apps = list
                refreshingApps = false
            }
        }.start()
    }

    // ── Model ────────────────────────────────────────────────────────────────

    /** The model the user chose last time, or any installed one. */
    fun preferredModelId(): String? =
        prefs.getString(KEY_ACTIVE_MODEL, null)?.takeIf { ModelManager.isModelReady(app, it) }
            ?: ModelManager.firstReadyModel(app)

    fun hasModelInstalled(): Boolean = preferredModelId() != null

    fun ensureModelLoaded() {
        if (model != null || loadingModelId != null) return
        val id = preferredModelId() ?: return
        loadModel(id)
    }

    fun loadModel(modelId: String) {
        if (loadingModelId == modelId) return
        stopListening()
        model?.close()
        model = null
        state.modelLoaded = false

        loadingModelId = modelId
        state.modelLoading = true
        state.activeModelId = modelId
        state.status = "モデルを読み込み中… (Loading model…)"
        prefs.edit().putString(KEY_ACTIVE_MODEL, modelId).apply()

        val path = ModelManager.modelDir(app, modelId).absolutePath
        Thread {
            try {
                val loaded = Model(path)
                main.post {
                    if (loadingModelId != modelId) {      // a newer load replaced this one
                        loaded.close()
                        return@post
                    }
                    model = loaded
                    loadingModelId = null
                    state.modelLoading = false
                    state.modelLoaded = true
                    state.status = READY
                    Log.d(TAG, "Model loaded: $modelId")
                    if (pendingListen) {
                        pendingListen = false
                        startListening(enrollCallback)
                    }
                }
            } catch (e: Exception) {
                main.post {
                    Log.e(TAG, "Model load failed: ${e.message}")
                    loadingModelId = null
                    pendingListen = false
                    state.modelLoading = false
                    state.status = "モデルの読み込みに失敗しました (Model failed to load)"
                }
            }
        }.start()
    }

    fun unloadModel() {
        stopListening()
        model?.close()
        model = null
        state.modelLoaded = false
        Log.d(TAG, "Model unloaded")
    }

    /** The assistant service started: warm up the model so the first invocation is fast. */
    fun onAssistantReady() {
        if (state.keepModelLoaded) ensureModelLoaded()
    }

    fun onOverlayOpened() {
        isOverlayOpen = true
        state.closeAt = 0L
        main.removeCallbacks(unloadRunnable)
        ensureModelLoaded()
        refreshApps()
    }

    fun onOverlayClosed() {
        isOverlayOpen = false
        pendingListen = false
        cancelConfirm()
        enrollCallback = null
        state.isEnrolling = false
        stopListening()
        if (!state.keepModelLoaded) main.postDelayed(unloadRunnable, UNLOAD_DELAY_MS)
    }

    /** The assistant was triggered again while the overlay is already open: start over and listen. */
    fun onRetrigger() {
        Log.d(TAG, "Re-triggered: listening again")
        cancelConfirm()
        enrollCallback = null
        state.isEnrolling = false
        stopSpeaking()
        runJob?.cancel()
        state.isRunning = false
        stopListening()
        startListening()
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    fun setAppCommandsEnabled(enabled: Boolean) {
        state.appCommandsEnabled = enabled
        prefs.edit().putBoolean(KEY_APP_COMMANDS, enabled).apply()
    }

    fun setKeepModelLoaded(keep: Boolean) {
        state.keepModelLoaded = keep
        prefs.edit().putBoolean(KEY_KEEP_MODEL, keep).apply()
        if (keep) ensureModelLoaded()
    }

    // ── Listening ────────────────────────────────────────────────────────────

    /** Tap on the mic button: stop whatever is happening, or start listening. */
    fun onMicButton() {
        when {
            state.isRunning   -> cancelRun()
            state.isSpeaking  -> stopSpeaking()
            state.isListening -> stopListening()
            else              -> { cancelConfirm(); startListening() }
        }
    }

    /**
     * Start listening. If [enroll] is given, the next sentence is handed to it
     * (trigger phrases, app names, and answers to questions) instead of being run as a command.
     */
    fun startListening(enroll: ((String) -> Unit)? = null) {
        if (state.isListening) return

        if (!hasMicPermission()) {
            state.needsMicPermission = true
            state.status = "マイクの許可が必要です (Mic permission needed)"
            return
        }
        state.needsMicPermission = false

        state.closeAt = 0L
        stopSpeaking()
        runJob?.cancel()
        state.isRunning = false
        enrollCallback = enroll
        state.isEnrolling = enroll != null

        val m = model
        if (m == null) {
            if (preferredModelId() == null) {
                state.status = "モデルをダウンロードしてください (Download a model first)"
                enrollCallback = null
                state.isEnrolling = false
                return
            }
            pendingListen = true          // starts by itself once the model has loaded
            ensureModelLoaded()
            return
        }

        state.partialText = ""
        state.partialRomaji = ""
        if (state.confirm == null) {      // while answering a question, keep showing what was heard before
            state.resultText = ""
            state.resultRomaji = ""
            state.heardConfidence = null
            state.matchStatus = ""
        }
        state.isListening = true
        state.status = "聞いています… (Kiite imasu — Listening…)"

        Earcon.start()
        val runnable = Runnable {
            beginRunnable = null
            beginRecording(m)
        }
        beginRunnable = runnable
        main.postDelayed(runnable, EARCON_DELAY_MS)
    }

    private fun beginRecording(m: Model) {
        val l = VoiceListener(m, object : VoiceListener.Callbacks {
            override fun onLevel(level: Float) {
                state.micLevel = level
            }

            override fun onPartial(text: String) {
                val partial = text.replace(" ", "")   // Vosk's Japanese output has spaces between words
                state.partialText = partial
                state.partialRomaji = toRomaji(partial)
            }

            override fun onResult(text: String, confidence: Float?) {
                handleUtterance(text, confidence)
            }

            override fun onTimeout() {
                val wasConfirm = state.confirm != null
                cancelEnroll()
                Earcon.fail()
                if (wasConfirm) {
                    cancelConfirm()
                    state.matchStatus = "返事がなかったのでキャンセルしました (No answer: cancelled)"
                    requestClose()
                } else {
                    state.status = "聞き取れませんでした (Didn't catch that)"
                }
            }

            override fun onError(message: String) {
                Log.e(TAG, "Listening error: $message")
                cancelEnroll()
                cancelConfirm()
                Earcon.fail()
                state.status = "エラー: $message"
            }
        })
        listener = l
        l.start()
    }

    fun stopListening() {
        beginRunnable?.let { main.removeCallbacks(it) }
        beginRunnable = null
        listener?.stop()
        listener = null
        state.isListening = false
        state.micLevel = 0f
        state.partialText = ""
        state.partialRomaji = ""
        if (!state.isSpeaking && !state.isRunning && !state.modelLoading) state.status = READY
    }

    fun cancelEnroll() {
        enrollCallback = null
        pendingListen = false
        state.isEnrolling = false
        stopListening()
    }

    // ── Sentence → command ───────────────────────────────────────────────────

    private fun handleUtterance(raw: String, confidence: Float?) {
        val text = raw.replace(" ", "")
        val isAnswer = state.confirm != null
        if (!isAnswer) {
            state.resultText = text
            state.resultRomaji = toRomaji(text)
            state.heardConfidence = confidence
        }
        Log.d(TAG, "Heard: $text (confidence=$confidence)")

        stopListening()     // mic off before anything speaks

        val cb = enrollCallback
        if (cb != null) {   // a trigger phrase, an app name, or an answer to a question
            enrollCallback = null
            state.isEnrolling = false
            if (!isAnswer) Earcon.ok()
            cb(text)
            return
        }
        handleCommand(text)
    }

    private fun handleCommand(text: String) {
        val routineGuess = CommandMatcher.match(text, state.routines).firstOrNull()
        val appGuess =
            if (state.appCommandsEnabled) {
                AppCommandMatcher.match(text, appsForMatching()) { aliasesFor(it) }
            } else null

        // 1) A routine that matches well runs at once.
        if (routineGuess != null && routineGuess.score >= CommandMatcher.RUN_THRESHOLD) {
            state.matchStatus = "▶ ${routineGuess.routine.name}  (${(routineGuess.score * 100).toInt()}%)"
            Log.d(TAG, "Matched '${routineGuess.routine.name}' via '${routineGuess.trigger}' (${routineGuess.score})")
            Earcon.ok()
            runRoutine(routineGuess.routine)
            return
        }

        // 2) An app that matches well opens at once.
        if (appGuess != null && appGuess.score >= AppCommandMatcher.RUN_THRESHOLD) {
            Log.d(TAG, "App command: '${appGuess.app.label}' via '${appGuess.alias}' (${appGuess.score})")
            Earcon.ok()
            openApp(appGuess)
            return
        }

        // 3) Not sure: ask out loud, and act on はい / いいえ.
        val askRoutine = routineGuess != null && (appGuess == null || routineGuess.score >= appGuess.score)
        when {
            askRoutine && routineGuess != null -> {
                Log.d(TAG, "Asking about routine '${routineGuess.routine.name}' (${routineGuess.score})")
                askConfirm("${routineGuess.trigger}、ですか？") { runRoutine(routineGuess.routine) }
            }
            appGuess != null -> {
                Log.d(TAG, "Asking about app '${appGuess.app.label}' (${appGuess.score})")
                askConfirm("${spokenName(appGuess)}を開きますか？") { openApp(appGuess) }
            }
            !state.appCommandsEnabled &&
                CommandMatcher.OPEN_VERB.containsMatchIn(CommandMatcher.normalize(text)) -> {
                state.matchStatus = "「アプリを声で開く」がオフです (Turn on “Open apps by voice” in settings)"
                Earcon.fail()
                requestClose()
            }
            else -> {
                state.matchStatus = "一致するものがありません (Nothing matched)"
                Earcon.fail()
                speak("すみません、わかりませんでした")
                requestClose()
            }
        }
    }

    // ── Spoken confirmation ──────────────────────────────────────────────────

    /** Ask [question] out loud, listen for はい / いいえ, and run [onYes] only on a yes. */
    fun askConfirm(question: String, onYes: () -> Unit) {
        cancelConfirm()
        state.confirm = PendingConfirm(question, onYes)
        state.matchStatus = ""
        state.status = "確認しています… (Asking…)"
        askAloudThenListen(question)
    }

    private fun askAloudThenListen(text: String) {
        main.removeCallbacks(speechStartCheck)
        main.removeCallbacks(speechHardLimit)
        listenAfterSpeech = true
        questionSpeechStarted = false
        speak(text)
        main.postDelayed(speechStartCheck, 2_500)
        main.postDelayed(speechHardLimit, 20_000)
    }

    private fun beginAnswerListening() {
        main.removeCallbacks(speechStartCheck)
        main.removeCallbacks(speechHardLimit)
        if (!listenAfterSpeech) return
        listenAfterSpeech = false
        questionSpeechStarted = false
        if (state.confirm == null) return
        startListening { answer -> onConfirmAnswer(answer) }
    }

    private fun onConfirmAnswer(text: String) {
        val pending = state.confirm ?: return
        when (YesNo.parse(text)) {
            true  -> finishConfirm(pending, true)
            false -> finishConfirm(pending, false)
            null  -> {
                confirmRetries++
                if (confirmRetries >= 2) {
                    cancelConfirm()
                    state.matchStatus = "キャンセルしました (Cancelled)"
                    speak("すみません、キャンセルします")
                    requestClose()
                } else {
                    askAloudThenListen("はい、かいいえで答えてください")
                }
            }
        }
    }

    /** Touch buttons for the same question (the voice answer is the normal way). */
    fun answerConfirm(yes: Boolean) {
        val pending = state.confirm ?: return
        stopSpeaking()
        enrollCallback = null
        state.isEnrolling = false
        stopListening()
        finishConfirm(pending, yes)
    }

    private fun finishConfirm(pending: PendingConfirm, yes: Boolean) {
        cancelConfirm()
        if (yes) {
            Earcon.ok()
            pending.onYes()
        } else {
            state.matchStatus = "キャンセルしました (Cancelled)"
            speak("わかりました")
            requestClose()
        }
    }

    private fun cancelConfirm() {
        main.removeCallbacks(speechStartCheck)
        main.removeCallbacks(speechHardLimit)
        listenAfterSpeech = false
        questionSpeechStarted = false
        confirmRetries = 0
        state.confirm = null
    }

    /** Ask the overlay to close itself in a moment (used when an interaction is finished). */
    private fun requestClose() {
        state.closeAt = SystemClock.elapsedRealtime()
    }

    // ── App names ────────────────────────────────────────────────────────────

    private fun appsForMatching(): List<AppInfo> =
        state.apps.ifEmpty { loadLaunchableApps(app) }

    /** Names this app answers to: yours if you set any, otherwise the built-in guesses. */
    fun aliasesFor(appInfo: AppInfo): List<String> =
        state.appAliases[appInfo.packageName] ?: DefaultAppNames.forLabel(appInfo.label)

    fun setAppAliases(packageName: String, aliases: List<String>) {
        state.appAliases[packageName] = aliases
        AppAliasStore.save(app, state.appAliases.toMap())
    }

    private fun spokenName(match: AppMatch): String =
        if (RomajiConverter.hasKanji(match.alias)) match.alias else RomajiConverter.toKatakana(match.alias)

    /** Open the app, then confirm out loud: 「<name>を開きました」. */
    fun openApp(match: AppMatch) {
        state.matchStatus = "▶ ${match.app.label}  (${(match.score * 100).toInt()}%)"
        runRoutine(
            Routine(
                name = match.app.label,
                triggers = emptyList(),
                actions = listOf(
                    RoutineAction.OpenApp(match.app.packageName, match.app.label),
                    RoutineAction.Speak("${spokenName(match)}を開きました")
                )
            )
        )
    }

    // ── Routines ─────────────────────────────────────────────────────────────

    private fun persistRoutines() = RoutineStore.save(app, state.routines.toList())

    fun saveRoutine(r: Routine) {
        val i = state.routines.indexOfFirst { it.id == r.id }
        if (i >= 0) state.routines[i] = r else state.routines.add(r)
        persistRoutines()
    }

    fun deleteRoutine(r: Routine) {
        state.routines.removeAll { it.id == r.id }
        persistRoutines()
    }

    fun toggleRoutine(r: Routine) {
        val i = state.routines.indexOfFirst { it.id == r.id }
        if (i >= 0) {
            state.routines[i] = r.copy(enabled = !r.enabled)
            persistRoutines()
        }
    }

    fun runRoutine(routine: Routine) {
        runJob?.cancel()
        runJob = scope.launch {
            state.isRunning = true
            state.status = "実行中… (Running: ${routine.name})"
            var ok = false
            try {
                val result = runner.run(routine) { step, total ->
                    state.status = "実行中 $step/$total… (${routine.name})"
                }
                ok = result.ok
                state.status = if (ok) "完了 (Done: ${routine.name})" else "エラー: ${result.message}"
            } finally {
                state.isRunning = false
                if (ok) requestClose()
            }
        }
    }

    fun cancelRun() {
        runJob?.cancel()
        state.isRunning = false
        state.status = "停止しました (Stopped)"
    }

    // ── Speech output ────────────────────────────────────────────────────────

    fun speak(text: String) {
        tts?.speak(text)
    }

    fun stopSpeaking() {
        tts?.stop()
    }
}
