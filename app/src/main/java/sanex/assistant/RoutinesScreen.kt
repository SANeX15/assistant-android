package sanex.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object Palette {
    val bg     = Color(0xFF0D0D0D)
    val card   = Color(0xFF1A1A2E)
    val accent = Color(0xFF7C4DFF)
    val text   = Color(0xFFE0E0E0)
    val sub    = Color(0xFF9E9E9E)
    val red    = Color(0xFFE53935)
}

private fun describe(a: RoutineAction): String = when (a) {
    is RoutineAction.OpenApp -> "アプリを開く / Open: ${a.label}"
    is RoutineAction.Speak   -> "話す / Say: ${a.text}"
    is RoutineAction.Wait    -> "待つ / Wait ${a.seconds}s"
}

private fun shortLabel(a: RoutineAction): String = when (a) {
    is RoutineAction.OpenApp -> a.label
    is RoutineAction.Speak   -> "話す"
    is RoutineAction.Wait    -> "${a.seconds}s"
}

// ── Routine list ─────────────────────────────────────────────────────────────

@Composable
fun RoutinesScreen(
    routines: List<Routine>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Routine) -> Unit,
    onToggle: (Routine) -> Unit,
    onRun: (Routine) -> Unit,
    onDelete: (Routine) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.bg)
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Palette.text)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("ルーティン / Routines", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.text)
                    Text("声で自動化 / Automate by voice", fontSize = 12.sp, color = Palette.sub)
                }
            }

            if (routines.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "まだありません。＋ をタップして作成\nNo routines yet. Tap + to create one.",
                        fontSize = 14.sp,
                        color = Palette.sub,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(routines, key = { it.id }) { r ->
                        RoutineCard(r, onEdit, onToggle, onRun, onDelete)
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAdd,
            containerColor = Palette.accent,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add routine")
        }
    }
}

@Composable
private fun RoutineCard(
    r: Routine,
    onEdit: (Routine) -> Unit,
    onToggle: (Routine) -> Unit,
    onRun: (Routine) -> Unit,
    onDelete: (Routine) -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.card)
            .border(1.dp, if (r.enabled) Palette.accent.copy(alpha = 0.5f) else Palette.card, shape)
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = r.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.text,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = r.enabled,
                    onCheckedChange = { onToggle(r) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Palette.accent)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            r.triggers.take(3).forEach { t ->
                Text("「$t」  ${toRomaji(t)}", fontSize = 12.sp, color = Palette.sub)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = r.actions.joinToString("  →  ") { shortLabel(it) },
                fontSize = 12.sp,
                color = Palette.accent.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onRun(r) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("実行 / Run", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { onEdit(r) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("編集 / Edit", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { onDelete(r) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.red),
                    border = BorderStroke(1.dp, Palette.red.copy(alpha = 0.5f)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ── Routine editor ───────────────────────────────────────────────────────────

private enum class EditorDialog { NONE, APP, SPEAK, WAIT }

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Palette.text,
    unfocusedTextColor = Palette.text,
    focusedBorderColor = Palette.accent,
    unfocusedBorderColor = Palette.sub,
    cursorColor = Palette.accent,
    focusedLabelColor = Palette.accent,
    unfocusedLabelColor = Palette.sub
)

@Composable
private fun SectionTitle(jp: String, en: String, hint: String? = null) {
    Spacer(modifier = Modifier.height(20.dp))
    Text("$jp / $en", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.text)
    if (hint != null) Text(hint, fontSize = 12.sp, color = Palette.sub, lineHeight = 18.sp)
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun RoutineEditorScreen(
    initial: Routine,
    state: MutableAssistantState,
    onBack: () -> Unit,
    onSave: (Routine) -> Unit,
    onTest: (Routine) -> Unit,
    onEnrollPhrase: ((String) -> Unit) -> Unit,
    onCancelEnroll: () -> Unit
) {
    val context = LocalContext.current
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    val triggers = remember(initial.id) { mutableStateListOf<String>().apply { addAll(initial.triggers) } }
    val steps = remember(initial.id) { mutableStateListOf<RoutineAction>().apply { addAll(initial.actions) } }
    var dialog by remember { mutableStateOf(EditorDialog.NONE) }

    fun draft() = initial.copy(name = name.trim(), triggers = triggers.toList(), actions = steps.toList())
    val canSave = name.isNotBlank() && triggers.isNotEmpty() && steps.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.bg)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Top bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Palette.text)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (initial.name.isBlank()) "新しいルーティン / New routine" else "編集 / Edit routine",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.text
                )
            }

            // Name
            SectionTitle("名前", "Name")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            // Trigger phrases
            SectionTitle(
                "言うフレーズ", "Trigger phrases",
                "Record each phrase once. It is saved exactly as the recognizer heard it, so saying it again matches."
            )
            triggers.forEachIndexed { i, t ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.card)
                        .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t, fontSize = 16.sp, color = Palette.text)
                        Text(toRomaji(t), fontSize = 12.sp, color = Palette.accent.copy(alpha = 0.85f))
                    }
                    IconButton(onClick = { triggers.removeAt(i) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove phrase", tint = Palette.sub)
                    }
                }
            }
            if (state.isEnrolling) {
                Text(
                    text = "聞いています… ${state.partialText}",
                    fontSize = 14.sp,
                    color = Palette.accent,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                OutlinedButton(
                    onClick = onCancelEnroll,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text)
                ) { Text("キャンセル / Cancel") }
            } else {
                Button(
                    onClick = { onEnrollPhrase { text -> if (text !in triggers) triggers.add(text) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.accent)
                ) { Text("● 録音 / Record a phrase") }
            }

            // Actions
            SectionTitle("動作", "Actions", "They run top to bottom.")
            steps.forEachIndexed { i, a ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.card)
                        .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)
                ) {
                    Text("${i + 1}.", fontSize = 14.sp, color = Palette.sub)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(describe(a), fontSize = 14.sp, color = Palette.text, modifier = Modifier.weight(1f))
                    IconButton(onClick = { steps.removeAt(i) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove action", tint = Palette.sub)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { dialog = EditorDialog.APP },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("+ App", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = { dialog = EditorDialog.SPEAK },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("+ Speak", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = { dialog = EditorDialog.WAIT },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("+ Wait", fontSize = 13.sp) }
            }

            // Test + Save
            Spacer(modifier = Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onTest(draft()) },
                    enabled = steps.isNotEmpty(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("テスト / Test") }
                Button(
                    onClick = { onSave(draft()) },
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.accent),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("保存 / Save") }
            }
            if (state.isRunning) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(state.status, fontSize = 12.sp, color = Palette.accent)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    if (dialog == EditorDialog.APP) {
        val apps = remember { AssistantEngine.state.apps.ifEmpty { loadLaunchableApps(context) } }
        AlertDialog(
            onDismissRequest = { dialog = EditorDialog.NONE },
            confirmButton = { TextButton(onClick = { dialog = EditorDialog.NONE }) { Text("閉じる / Close") } },
            title = { Text("アプリを選択 / Pick an app") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        Text(
                            text = app.label,
                            color = Palette.text,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    steps.add(RoutineAction.OpenApp(app.packageName, app.label))
                                    dialog = EditorDialog.NONE
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            containerColor = Palette.card,
            titleContentColor = Palette.text,
            textContentColor = Palette.text
        )
    }

    if (dialog == EditorDialog.SPEAK) {
        var speakText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialog = EditorDialog.NONE },
            confirmButton = {
                TextButton(
                    enabled = speakText.isNotBlank(),
                    onClick = {
                        steps.add(RoutineAction.Speak(speakText.trim()))
                        dialog = EditorDialog.NONE
                    }
                ) { Text("追加 / Add") }
            },
            dismissButton = { TextButton(onClick = { dialog = EditorDialog.NONE }) { Text("キャンセル / Cancel") } },
            title = { Text("話す内容 / What to say") },
            text = {
                OutlinedTextField(
                    value = speakText,
                    onValueChange = { speakText = it },
                    label = { Text("日本語 / Japanese text") },
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            containerColor = Palette.card,
            titleContentColor = Palette.text,
            textContentColor = Palette.text
        )
    }

    if (dialog == EditorDialog.WAIT) {
        AlertDialog(
            onDismissRequest = { dialog = EditorDialog.NONE },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialog = EditorDialog.NONE }) { Text("キャンセル / Cancel") } },
            title = { Text("待つ時間 / Wait for") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 3, 5, 10).forEach { s ->
                        OutlinedButton(
                            onClick = {
                                steps.add(RoutineAction.Wait(s))
                                dialog = EditorDialog.NONE
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("${s}s", fontSize = 13.sp) }
                    }
                }
            },
            containerColor = Palette.card,
            titleContentColor = Palette.text,
            textContentColor = Palette.text
        )
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private fun sampleRoutines() = listOf(
    Routine(
        name = "Camera",
        triggers = listOf("カメラを開いて"),
        actions = listOf(RoutineAction.OpenApp("com.android.camera", "Camera"))
    ),
    Routine(
        name = "Good morning",
        triggers = listOf("おはよう", "おはようございます"),
        actions = listOf(RoutineAction.Speak("おはようございます"), RoutineAction.Wait(2)),
        enabled = false
    )
)

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, name = "Routines list")
@Composable
fun RoutinesScreenPreview() {
    RoutinesScreen(
        routines = sampleRoutines(),
        onBack = {}, onAdd = {}, onEdit = {}, onToggle = {}, onRun = {}, onDelete = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, name = "Routine editor")
@Composable
fun RoutineEditorPreview() {
    RoutineEditorScreen(
        initial = sampleRoutines()[1],
        state = MutableAssistantState(),
        onBack = {}, onSave = {}, onTest = {}, onEnrollPhrase = {}, onCancelEnroll = {}
    )
}
