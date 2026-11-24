package sanex.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How an alias reads: typed romaji shows its katakana, kana/kanji shows its romaji. */
private fun aliasPreview(alias: String): String =
    if (RomajiConverter.isAscii(alias)) RomajiConverter.toKatakana(alias) else toRomaji(alias)

/**
 * Lets you choose how each app is called by voice: "<name> を開いて".
 * Add a name by typing romaji, or by saying it once.
 */
@Composable
fun AppCommandsScreen(
    state: MutableAssistantState,
    onBack: () -> Unit,
    aliasesFor: (AppInfo) -> List<String>,
    onSetAliases: (String, List<String>) -> Unit,
    onEnrollName: ((String) -> Unit) -> Unit,
    onCancelEnroll: () -> Unit
) {
    val context = LocalContext.current
    val loaded = state.apps
    val apps = if (loaded.isNotEmpty()) loaded else remember { loadLaunchableApps(context) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<AppInfo?>(null) }

    val shown = apps
        .filter { it.label.contains(query, ignoreCase = true) }
        .sortedByDescending { aliasesFor(it).isNotEmpty() }      // apps that already have a name first

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
                IconButton(onClick = { onCancelEnroll(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Palette.text)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("アプリ名 / App names", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.text)
                    Text("「ユーチューブを開いて」のように言います", fontSize = 12.sp, color = Palette.sub)
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Search apps") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Palette.text,
                    unfocusedTextColor = Palette.text,
                    focusedBorderColor = Palette.accent,
                    unfocusedBorderColor = Palette.sub,
                    cursorColor = Palette.accent,
                    focusedLabelColor = Palette.accent,
                    unfocusedLabelColor = Palette.sub
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(shown, key = { it.packageName }) { app ->
                    val names = aliasesFor(app)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.card)
                            .clickable { editing = app }
                            .padding(14.dp)
                    ) {
                        Text(app.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.text)
                        Text(
                            text = if (names.isEmpty()) "名前なし / no spoken name yet"
                            else names.joinToString("   ·   ") { "$it → ${aliasPreview(it)}" },
                            fontSize = 12.sp,
                            color = if (names.isEmpty()) Palette.sub else Palette.accent.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }

    editing?.let { app ->
        val list = remember(app.packageName) { mutableStateListOf<String>().apply { addAll(aliasesFor(app)) } }
        var typed by remember(app.packageName) { mutableStateOf("") }
        fun commit() = onSetAliases(app.packageName, list.toList())
        fun close() { onCancelEnroll(); editing = null }

        AlertDialog(
            onDismissRequest = { close() },
            confirmButton = { TextButton(onClick = { close() }) { Text("閉じる / Done") } },
            title = { Text(app.label) },
            text = {
                Column {
                    Text(
                        "How do you say this app's name? Type it in romaji, or say only the name.",
                        fontSize = 12.sp,
                        color = Palette.sub,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    list.forEachIndexed { i, a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(a, fontSize = 15.sp, color = Palette.text)
                                Text(aliasPreview(a), fontSize = 12.sp, color = Palette.accent.copy(alpha = 0.85f))
                            }
                            IconButton(onClick = { list.removeAt(i); commit() }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Palette.sub)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        label = { Text("romaji, e.g. yu-chu-bu") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Palette.text,
                            unfocusedTextColor = Palette.text,
                            focusedBorderColor = Palette.accent,
                            unfocusedBorderColor = Palette.sub,
                            cursorColor = Palette.accent,
                            focusedLabelColor = Palette.accent,
                            unfocusedLabelColor = Palette.sub
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (typed.isNotBlank()) {
                        Text("→ ${aliasPreview(typed.trim())}", fontSize = 12.sp, color = Palette.accent)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val a = typed.trim()
                                if (a.isNotEmpty() && a !in list) { list.add(a); commit() }
                                typed = ""
                            },
                            enabled = typed.isNotBlank(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text)
                        ) { Text("追加 / Add") }

                        if (state.isEnrolling) {
                            OutlinedButton(
                                onClick = onCancelEnroll,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text)
                            ) { Text("キャンセル") }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    onEnrollName { heard ->
                                        if (heard !in list) { list.add(heard); commit() }
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text)
                            ) { Text("● 録音 / Record") }
                        }
                    }
                    if (state.isEnrolling) {
                        Text(
                            "聞いています… ${state.partialText}",
                            fontSize = 13.sp,
                            color = Palette.accent,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            containerColor = Palette.card,
            titleContentColor = Palette.text,
            textContentColor = Palette.text
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, name = "App names")
@Composable
fun AppCommandsScreenPreview() {
    AppCommandsScreen(
        state = MutableAssistantState(),
        onBack = {},
        aliasesFor = { DefaultAppNames.forLabel(it.label) },
        onSetAliases = { _, _ -> },
        onEnrollName = {},
        onCancelEnroll = {}
    )
}
