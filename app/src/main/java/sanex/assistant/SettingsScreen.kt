package sanex.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
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
import kotlinx.coroutines.launch

@Composable @Preview
fun SettingsScreen(
    activeModelId: String?,
    onBack: () -> Unit,
    onModelReady: (String) -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val scroll  = rememberScrollState()

    val bgColor      = Color(0xFF0D0D0D)
    val accentColor  = Color(0xFF7C4DFF)
    val cardColor    = Color(0xFF1A1A2E)
    val textColor    = Color(0xFFE0E0E0)
    val subTextColor = Color(0xFF9E9E9E)
    val redColor     = Color(0xFFE53935)
    val greenColor   = Color(0xFF43A047)

    // -1 = idle, 0..100 = downloading, -2 = extracting
    val progressMap = remember {
        mutableStateMapOf<String, Int>().also { map ->
            ModelManager.availableModels.forEach { map[it.id] = -1 }
        }
    }
    val errorMap = remember { mutableStateMapOf<String, String>() }

    var installedModels by remember {
        mutableStateOf(
            ModelManager.availableModels
                .filter { ModelManager.isModelReady(context, it.id) }
                .map { it.id }
                .toSet()
        )
    }

    fun refreshInstalled() {
        installedModels = ModelManager.availableModels
            .filter { ModelManager.isModelReady(context, it.id) }
            .map { it.id }
            .toSet()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = textColor
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "設定 / Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = "モデル管理 / Model management",
                        fontSize = 12.sp,
                        color = subTextColor
                    )
                }
            }

            Text(
                text = "音声認識モデル / Speech models",
                fontSize = 13.sp,
                color = subTextColor,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            ModelManager.availableModels.forEach { model ->
                val isInstalled   = model.id in installedModels
                val isActive      = model.id == activeModelId
                val progress      = progressMap[model.id] ?: -1
                val isDownloading = progress in 0..100
                val isExtracting  = progress == -2
                val hasError      = errorMap.containsKey(model.id)
                val borderColor   = when {
                    isActive    -> accentColor
                    isInstalled -> greenColor.copy(alpha = 0.5f)
                    else        -> cardColor
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(cardColor)
                        .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = model.displayName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                            if (isActive) {
                                Text(
                                    text = "使用中 / Active",
                                    fontSize = 11.sp,
                                    color = accentColor,
                                    modifier = Modifier
                                        .background(
                                            accentColor.copy(alpha = 0.15f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            } else if (isInstalled) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Installed",
                                    tint = greenColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = model.description,
                            fontSize = 12.sp,
                            color = subTextColor,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        when {
                            isDownloading -> {
                                Text(
                                    text = "ダウンロード中… $progress% / Downloading…",
                                    fontSize = 12.sp,
                                    color = accentColor
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { progress / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = accentColor,
                                    trackColor = accentColor.copy(alpha = 0.2f)
                                )
                            }
                            isExtracting -> {
                                Text(
                                    text = "展開中… / Extracting…",
                                    fontSize = 12.sp,
                                    color = accentColor
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = accentColor,
                                    trackColor = accentColor.copy(alpha = 0.2f)
                                )
                            }
                            hasError -> {
                                Text(
                                    text = "エラー: ${errorMap[model.id]}",
                                    fontSize = 12.sp,
                                    color = redColor
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        errorMap.remove(model.id)
                                        progressMap[model.id] = -1
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = redColor),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("再試行 / Retry", fontSize = 12.sp)
                                }
                            }
                            else -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (!isInstalled) {
                                        Button(
                                            onClick = {
                                                errorMap.remove(model.id)
                                                scope.launch {
                                                    ModelManager.downloadModel(
                                                        context = context,
                                                        model = model,
                                                        onProgress = { p ->
                                                            progressMap[model.id] = p
                                                        },
                                                        onComplete = {
                                                            progressMap[model.id] = -1
                                                            refreshInstalled()
                                                            onModelReady(model.id)
                                                        },
                                                        onError = { err ->
                                                            progressMap[model.id] = -1
                                                            errorMap[model.id] = err
                                                        }
                                                    )
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = accentColor
                                            ),
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Text(
                                                "ダウンロード / Download (${model.sizeMb} MB)",
                                                fontSize = 12.sp
                                            )
                                        }
                                    } else {
                                        if (!isActive) {
                                            Button(
                                                onClick = { onModelReady(model.id) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = greenColor
                                                ),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Text("使用する / Use", fontSize = 12.sp)
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                ModelManager.deleteModel(context, model.id)
                                                refreshInstalled()
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = redColor
                                            ),
                                            border = BorderStroke(1.dp, redColor.copy(alpha = 0.5f)),
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("削除 / Delete", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val usedMb = ModelManager.modelsDir(context).walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() } / (1024 * 1024)

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ストレージ使用量 / Storage used: ${usedMb} MB",
                fontSize = 12.sp,
                color = subTextColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        }
    }
}