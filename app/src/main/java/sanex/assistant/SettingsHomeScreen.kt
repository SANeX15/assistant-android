package sanex.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What opens when you tap the launcher icon. */
@Composable
fun SettingsHomeScreen(
    state: MutableAssistantState,
    onOpenModels: () -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenAppNames: () -> Unit,
    onToggleAppCommands: (Boolean) -> Unit,
    onToggleKeepModel: (Boolean) -> Unit,
    onSelectMic: (MicChoice) -> Unit,
    onRefreshMicInfo: () -> Unit,
    onToggleLockScreen: (Boolean) -> Unit
) {
    val model = ModelManager.availableModels.find { it.id == state.activeModelId }
    val modelLine = (model?.displayName ?: "未選択 / None selected") +
            if (state.modelLoading) "  ·  読み込み中… / loading…"
            else if (state.modelLoaded) "  ·  読み込み済み / loaded"
            else ""
    var showMicDialog by remember { mutableStateOf(false) }

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
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text("みびちゃん", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette.accent)
            Text("mivi-chan", fontSize = 14.sp, color = Palette.sub, letterSpacing = 4.sp)

            Spacer(modifier = Modifier.height(28.dp))
            Text("設定 / Settings", fontSize = 13.sp, color = Palette.sub)
            Spacer(modifier = Modifier.height(12.dp))

            SettingRow(
                title = "音声認識モデル / Speech model",
                subtitle = modelLine,
                onClick = onOpenModels
            )
            SettingRow(
                title = "マイク / Microphone",
                subtitle = state.micChoice.label,
                onClick = {
                    onRefreshMicInfo()
                    showMicDialog = true
                }
            )
            SettingRow(
                title = "ルーティン / Routines",
                subtitle = "${state.routines.size} 件 / routines",
                onClick = onOpenRoutines
            )
            SettingToggle(
                title = "アプリを声で開く / Open apps by voice",
                subtitle = "「ユーチューブを開いて」のように言うとアプリが開きます",
                checked = state.appCommandsEnabled,
                onCheckedChange = onToggleAppCommands
            )
            SettingRow(
                title = "アプリ名 / App names",
                subtitle = "How you say each app's name  ·  ${state.appAliases.size} customised",
                onClick = onOpenAppNames
            )
            SettingToggle(
                title = "ロック画面で使う / Use on lock screen",
                subtitle = "On: works over the lock screen, and asks you to unlock before opening an app. " +
                        "Off: asks you to unlock before it listens.",
                checked = state.allowOnLockScreen,
                onCheckedChange = onToggleLockScreen
            )
            SettingToggle(
                title = "モデルを常駐 / Keep model in memory",
                subtitle = "On: the assistant opens instantly. Off: saves RAM, loads on demand.",
                checked = state.keepModelLoaded,
                onCheckedChange = onToggleKeepModel
            )
        }
    }

    if (showMicDialog) {
        AlertDialog(
            onDismissRequest = { showMicDialog = false },
            confirmButton = { TextButton(onClick = { showMicDialog = false }) { Text("閉じる / Close") } },
            title = { Text("マイク / Microphone") },
            text = {
                Column {
                    MicChoice.entries.forEach { choice ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectMic(choice) }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(selected = state.micChoice == choice, onClick = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(choice.label, fontSize = 14.sp, color = Palette.text)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("いま接続中 / Connected now", fontSize = 12.sp, color = Palette.sub)
                    Text(state.micInfo, fontSize = 12.sp, color = Palette.accent, lineHeight = 18.sp)
                }
            },
            containerColor = Palette.card,
            titleContentColor = Palette.text,
            textContentColor = Palette.text
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.card)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.text)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, fontSize = 12.sp, color = Palette.sub, lineHeight = 18.sp)
        }
        Text("›", fontSize = 24.sp, color = Palette.sub)
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.card)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.text)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, fontSize = 12.sp, color = Palette.sub, lineHeight = 18.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.accent)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, name = "Settings home")
@Composable
fun SettingsHomePreview() {
    SettingsHomeScreen(
        state = MutableAssistantState().apply {
            activeModelId = "vosk-model-ja-0.22"
            modelLoaded = true
        },
        onOpenModels = {}, onOpenRoutines = {}, onOpenAppNames = {},
        onToggleAppCommands = {}, onToggleKeepModel = {},
        onSelectMic = {}, onRefreshMicInfo = {}, onToggleLockScreen = {}
    )
}