package sanex.assistant

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Screen { HOME, MODELS, ROUTINES, APPS, EDITOR, SETUP }

class NavState(start: Screen) {
    var screen by mutableStateOf(start)
    var editing by mutableStateOf<Routine?>(null)
}

/**
 * The launcher icon opens this: settings, models and routines.
 * Listening itself happens in the assistant overlay (AssistantActivity),
 * apart from recording trigger phrases in the routine editor.
 */
class MainActivity : ComponentActivity() {

    private val requestMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            AssistantEngine.onMicPermissionResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AssistantEngine.init(application)

        if (!AssistantEngine.hasMicPermission()) requestMic.launch(Manifest.permission.RECORD_AUDIO)

        val nav = NavState(if (AssistantEngine.hasModelInstalled()) Screen.HOME else Screen.SETUP)
        setContent { AppRoot(nav) }
    }
}

@Composable
fun AppRoot(nav: NavState) {
    val state = AssistantEngine.state

    BackHandler(enabled = nav.screen == Screen.MODELS || nav.screen == Screen.ROUTINES) {
        nav.screen = Screen.HOME
    }
    BackHandler(enabled = nav.screen == Screen.APPS) {
        AssistantEngine.cancelEnroll()
        nav.screen = Screen.HOME
    }
    BackHandler(enabled = nav.screen == Screen.EDITOR) {
        AssistantEngine.cancelEnroll()
        nav.screen = Screen.ROUTINES
    }

    when (nav.screen) {
        Screen.SETUP -> SetupScreen(
            onModelReady = { id ->
                AssistantEngine.loadModel(id)
                nav.screen = Screen.HOME
            }
        )
        Screen.HOME -> SettingsHomeScreen(
            state = state,
            onOpenModels = { nav.screen = Screen.MODELS },
            onOpenRoutines = { nav.screen = Screen.ROUTINES },
            onOpenAppNames = { nav.screen = Screen.APPS },
            onToggleAppCommands = AssistantEngine::setAppCommandsEnabled,
            onToggleKeepModel = AssistantEngine::setKeepModelLoaded
        )
        Screen.MODELS -> SettingsScreen(
            activeModelId = state.activeModelId,
            onBack = { nav.screen = Screen.HOME },
            onModelReady = { id ->
                AssistantEngine.loadModel(id)
                nav.screen = Screen.HOME
            }
        )
        Screen.ROUTINES -> RoutinesScreen(
            routines = state.routines,
            onBack = { nav.screen = Screen.HOME },
            onAdd = {
                nav.editing = Routine(name = "", triggers = emptyList(), actions = emptyList())
                nav.screen = Screen.EDITOR
            },
            onEdit = { nav.editing = it; nav.screen = Screen.EDITOR },
            onToggle = AssistantEngine::toggleRoutine,
            onRun = AssistantEngine::runRoutine,
            onDelete = AssistantEngine::deleteRoutine
        )
        Screen.APPS -> AppCommandsScreen(
            state = state,
            onBack = { nav.screen = Screen.HOME },
            aliasesFor = AssistantEngine::aliasesFor,
            onSetAliases = AssistantEngine::setAppAliases,
            onEnrollName = { cb -> AssistantEngine.startListening(cb) },
            onCancelEnroll = AssistantEngine::cancelEnroll
        )
        Screen.EDITOR -> {
            val r = nav.editing
            if (r != null) {
                RoutineEditorScreen(
                    initial = r,
                    state = state,
                    onBack = { AssistantEngine.cancelEnroll(); nav.screen = Screen.ROUTINES },
                    onSave = { AssistantEngine.saveRoutine(it); nav.screen = Screen.ROUTINES },
                    onTest = AssistantEngine::runRoutine,
                    onEnrollPhrase = { cb -> AssistantEngine.startListening(cb) },
                    onCancelEnroll = AssistantEngine::cancelEnroll
                )
            }
        }
    }
}

// ── First-launch screen: pick a model to download ────────────────────────────

@Composable
fun SetupScreen(onModelReady: (String) -> Unit) {
    val bgColor     = Color(0xFF0D0D0D)
    val accentColor = Color(0xFF7C4DFF)
    val subColor    = Color(0xFF9E9E9E)
    val textColor   = Color(0xFFE0E0E0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .systemBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Text("みびちゃん", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = accentColor)
            Text("mivi-chan",  fontSize = 14.sp, color = subColor, letterSpacing = 4.sp)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "はじめに音声モデルをダウンロードしてください。\nPlease download a speech model to get started.",
                fontSize = 14.sp,
                color = textColor,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            SettingsScreen(
                activeModelId = null,
                onBack        = {},
                onModelReady  = onModelReady
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, name = "Setup Screen")
@Composable
fun SetupScreenPreview() {
    SetupScreen(onModelReady = {})
}
