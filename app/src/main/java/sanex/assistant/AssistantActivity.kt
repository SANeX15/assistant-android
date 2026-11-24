package sanex.assistant

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Shows a small "taps: N" line in the overlay so we can see whether touches reach it. Set to false once taps work. */
private const val SHOW_TOUCH_DEBUG = true

/**
 * The assistant overlay: a translucent window with a bottom sheet of at most half the screen.
 * Opens, plays a beep, listens, shows what it heard, runs the matching routine or app command,
 * then closes itself. When it isn't sure it asks out loud and waits for はい / いいえ.
 */
class AssistantActivity : ComponentActivity() {

    private var launchedVia = "intent"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        launchedVia =
            if (intent.getBooleanExtra(EXTRA_FROM_SESSION, false)) "assistant session"
            else "intent ${intent.action ?: ""}".trim()

        AssistantEngine.init(application)
        AssistantEngine.onOverlayOpened()

        setContent {
            AssistantOverlay(
                state      = AssistantEngine.state,
                onDismiss  = { closeOverlay() },
                onSettings = { openSettings() },
                onMic      = { AssistantEngine.onMicButton() },
                onAnswer   = { AssistantEngine.answerConfirm(it) },
                debugInfo  = if (SHOW_TOUCH_DEBUG) "via $launchedVia" else null
            )
        }

        if (savedInstanceState == null) AssistantEngine.startListening()
    }

    /** The assistant was triggered again (headset button, gesture...) while this screen is already open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d("Assistant", "overlay onNewIntent: listening again")
        AssistantEngine.onRetrigger()
    }

    /** Sees every touch before the UI does, so we can tell "touch never arrived" from "button did nothing". */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            AssistantEngine.state.debugTaps++
            Log.d("Assistant", "overlay touch DOWN at ${ev.x.toInt()},${ev.y.toInt()}")
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d("Assistant", "overlay window focus: $hasFocus")
    }

    // An assistant window should go away as soon as it loses focus
    // (another app was opened, the screen turned off, the user went home).
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) closeOverlay()
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) AssistantEngine.onOverlayClosed()
        super.onDestroy()
    }

    /** Close the screen and also tell the system the assistant session is over, so it stops holding input focus. */
    private fun closeOverlay() {
        AssistantEngine.onOverlayClosed()
        AssistantSession.hide()
        finish()
    }

    private fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        closeOverlay()
    }

    companion object {
        const val EXTRA_FROM_SESSION = "from_session"
    }
}

@Composable
fun AssistantOverlay(
    state: MutableAssistantState,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onMic: () -> Unit,
    onAnswer: (Boolean) -> Unit = {},
    debugInfo: String? = null
) {
    val bg        = Color(0xFF0D0D0D)
    val card      = Color(0xFF1A1A2E)
    val accent    = Color(0xFF7C4DFF)
    val red       = Color(0xFFE53935)
    val green     = Color(0xFF43A047)
    val amber     = Color(0xFFFFB300)
    val textColor = Color(0xFFE0E0E0)
    val subColor  = Color(0xFF9E9E9E)

    val maxSheetHeight   = LocalConfiguration.current.screenHeightDp.dp * 0.5f
    val scrimInteraction = remember { MutableInteractionSource() }

    // When an interaction is finished the engine sets closeAt; close shortly after.
    // A new interaction (for example triggering the assistant again) sets it back to 0, which cancels this.
    val closeAt = state.closeAt
    LaunchedEffect(closeAt) {
        if (closeAt != 0L) {
            delay(6000)
            onDismiss()
        }
    }

    val level by animateFloatAsState(targetValue = state.micLevel, label = "micLevel")
    val busy = state.isListening || state.isSpeaking || state.isRunning

    Box(modifier = Modifier.fillMaxSize()) {

        // Layer 1: the dimmed area. Tapping it closes the assistant.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.30f))
                .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismiss)
        )

        // Layer 2: the sheet, on top and separate from the layer below, so its buttons get taps first.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(bg)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Header: name on the left, settings in the corner
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("みびちゃん", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent)
                    Text("mivi-chan", fontSize = 11.sp, color = subColor, letterSpacing = 3.sp)
                    if (debugInfo != null) {
                        Text("taps: ${state.debugTaps}  ·  $debugInfo", fontSize = 10.sp, color = amber)
                    }
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "設定 / Settings", tint = subColor)
                }
            }

            // Mic level: shows whether the phone is hearing you
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(card)
            ) {
                if (state.isListening) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(level.coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .background(accent)
                    )
                }
            }

            // Result in the middle
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(card)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.partialText.isNotBlank() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.partialText, fontSize = 22.sp, color = textColor.copy(alpha = 0.45f), textAlign = TextAlign.Center)
                        if (state.partialRomaji.isNotBlank()) {
                            Text(state.partialRomaji, fontSize = 14.sp, color = accent.copy(alpha = 0.4f), textAlign = TextAlign.Center)
                        }
                    }
                    state.resultText.isNotBlank() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.resultText, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = textColor, textAlign = TextAlign.Center)
                        if (state.resultRomaji.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(state.resultRomaji, fontSize = 15.sp, color = accent.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                        }
                    }
                    state.needsMicPermission -> Text(
                        "マイクの許可が必要です。アプリを開いて許可してください。\nOpen the app once to allow the microphone.",
                        fontSize = 13.sp, color = subColor, textAlign = TextAlign.Center, lineHeight = 20.sp
                    )
                    state.modelLoading -> Text(
                        "モデルを読み込み中…\nLoading the speech model…",
                        fontSize = 14.sp, color = subColor, textAlign = TextAlign.Center, lineHeight = 22.sp
                    )
                    else -> Text(
                        "話しかけてください\nSay something…",
                        fontSize = 14.sp, color = subColor, textAlign = TextAlign.Center, lineHeight = 22.sp
                    )
                }
            }

            // How clearly it heard you
            val confidence = state.heardConfidence
            if (!state.isListening && state.resultText.isNotBlank() && confidence != null) {
                val pct = (confidence * 100).toInt()
                val c = when {
                    pct >= 85 -> green
                    pct >= 60 -> amber
                    else      -> red
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "認識の確かさ / Heard clearly: $pct%",
                    fontSize = 12.sp,
                    color = c,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            // What it is doing
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.status,
                fontSize = 12.sp,
                color = subColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.matchStatus.isNotBlank()) {
                Text(
                    text = state.matchStatus,
                    fontSize = 13.sp,
                    color = accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }

            // A question it asked out loud. Answer by voice (はい / いいえ) or with the buttons.
            state.confirm?.let { c ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = c.question,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = toRomaji(c.question),
                    fontSize = 12.sp,
                    color = accent.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Button(
                        onClick = { onAnswer(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = green),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) { Text("はい / Yes", color = Color.White) }
                    OutlinedButton(
                        onClick = { onAnswer(false) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) { Text("いいえ / No") }
                }
            }

            // Speak / stop
            Spacer(modifier = Modifier.height(12.dp))
            val buttonColor = when {
                state.isListening                   -> red
                state.isSpeaking || state.isRunning -> green
                else                                -> accent
            }
            Button(
                onClick = onMic,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (busy) "停止 / Stop" else "話す / Speak", color = Color.White)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF444444, name = "Overlay — listening")
@Composable
fun AssistantOverlayListeningPreview() {
    AssistantOverlay(
        state = MutableAssistantState().apply {
            isListening = true
            micLevel = 0.6f
            status = "聞いています… (Kiite imasu — Listening…)"
            partialText = "カメラを"
            partialRomaji = "kamerao"
        },
        onDismiss = {}, onSettings = {}, onMic = {}, debugInfo = "via preview"
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF444444, name = "Overlay — asking")
@Composable
fun AssistantOverlayAskingPreview() {
    AssistantOverlay(
        state = MutableAssistantState().apply {
            status = "話しています… (Hanashite imasu — Speaking…)"
            isSpeaking = true
            resultText = "カメラをひらいて"
            resultRomaji = "kamerawohiraite"
            heardConfidence = 0.58f
            confirm = PendingConfirm("カメラを開いて、ですか？") {}
        },
        onDismiss = {}, onSettings = {}, onMic = {}
    )
}
