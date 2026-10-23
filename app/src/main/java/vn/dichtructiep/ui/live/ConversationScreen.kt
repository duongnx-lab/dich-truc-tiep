package vn.dichtructiep.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.dichtructiep.app
import vn.dichtructiep.data.AppSettings
import vn.dichtructiep.data.Languages
import vn.dichtructiep.live.LiveMode
import vn.dichtructiep.live.LiveUi
import vn.dichtructiep.live.LiveViewModel
import vn.dichtructiep.live.Side
import vn.dichtructiep.live.UState
import vn.dichtructiep.live.Utterance
import vn.dichtructiep.ui.components.InfoBanner
import vn.dichtructiep.ui.components.KeepScreenOn
import vn.dichtructiep.ui.components.LanguageChip
import vn.dichtructiep.ui.components.LanguagePickerDialog
import vn.dichtructiep.ui.components.LevelBars
import vn.dichtructiep.ui.components.MicButton
import vn.dichtructiep.ui.components.copyText
import vn.dichtructiep.ui.components.rememberMicPermission
import vn.dichtructiep.ui.components.shareText
import vn.dichtructiep.ui.theme.SideAColor
import vn.dichtructiep.ui.theme.SideBColor

@Composable
fun ConversationScreen(onBack: () -> Unit, onSettings: () -> Unit) {
    val vm: LiveViewModel = viewModel(factory = LiveViewModel.factory(LiveMode.CONVERSATION))
    val ui by vm.ui.collectAsState()
    val s by vm.settings.collectAsState()
    val context = LocalContext.current
    val ready by context.app.models.downloaded.collectAsState()

    var pickSide by remember { mutableStateOf<Side?>(null) }
    var menu by remember { mutableStateOf(false) }

    val startAuto = rememberMicPermission { vm.start(null) }
    val talkA = rememberMicPermission { vm.talk(Side.A) }
    val talkB = rememberMicPermission { vm.talk(Side.B) }

    KeepScreenOn(s.keepScreenOn && (ui.running || ui.speaking))

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Nửa trên: người B (xoay ngược để người đối diện đọc được)
        ConversationPanel(
            side = Side.B,
            ui = ui,
            s = s,
            accent = SideBColor,
            onPickLang = { pickSide = Side.B },
            onTalk = talkB,
            onSpeak = { vm.speak(it) },
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { rotationZ = if (s.faceToFace) 180f else 0f },
        )

        // Thanh điều khiển giữa
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ui.error?.let { err ->
                    InfoBanner(
                        "$err  (chạm để ẩn)",
                        isError = true,
                        onClick = { vm.dismissError() },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
                    IconButton(onClick = { vm.swapConvLangs() }) { Icon(Icons.Filled.SwapVert, "Đổi ngôn ngữ") }
                    IconButton(onClick = { vm.updateSettings(restart = false) { it.copy(faceToFace = !it.faceToFace) } }) {
                        Icon(
                            Icons.Filled.ScreenRotation, "Đối diện",
                            tint = if (s.faceToFace) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MicButton(
                        running = ui.running && ui.activeSide == null,
                        level = ui.level,
                        onClick = { if (ui.running && ui.activeSide == null) vm.stop() else startAuto() },
                        size = 60.dp,
                        contentDescription = "Tự động nghe cả hai",
                    )
                    IconButton(onClick = {
                        vm.updateSettings(restart = false) { it.copy(ttsConversation = !it.ttsConversation) }
                        if (s.ttsConversation) vm.stopSpeaking()
                    }) {
                        Icon(
                            if (s.ttsConversation) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            "Đọc to bản dịch",
                            tint = if (s.ttsConversation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Thêm") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Phiên mới") },
                                leadingIcon = { Icon(Icons.Filled.Add, null) },
                                onClick = { menu = false; vm.newSession() },
                            )
                            DropdownMenuItem(
                                text = { Text("Sao chép hội thoại") },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                                onClick = { menu = false; context.copyText(vm.transcript()) },
                            )
                            DropdownMenuItem(
                                text = { Text("Chia sẻ") },
                                leadingIcon = { Icon(Icons.Filled.Share, null) },
                                onClick = { menu = false; context.shareText(vm.transcript()) },
                            )
                            DropdownMenuItem(
                                text = { Text("Cài đặt") },
                                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                onClick = { menu = false; onSettings() },
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    LevelBars(ui.level, ui.running)
                    Text(
                        statusText(ui, s),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        // Nửa dưới: người A (người cầm máy)
        ConversationPanel(
            side = Side.A,
            ui = ui,
            s = s,
            accent = SideAColor,
            onPickLang = { pickSide = Side.A },
            onTalk = talkA,
            onSpeak = { vm.speak(it) },
            modifier = Modifier.weight(1f),
        )
    }

    pickSide?.let { side ->
        LanguagePickerDialog(
            title = if (side == Side.A) "Ngôn ngữ của bạn (A)" else "Ngôn ngữ người đối diện (B)",
            selected = if (side == Side.A) s.convLangA else s.convLangB,
            offlineReady = ready,
            onDismiss = { pickSide = null },
            onPick = { vm.setConvLang(side, it.code) },
        )
    }
}

private fun statusText(ui: LiveUi, s: AppSettings): String = when {
    ui.speaking -> "Đang đọc bản dịch…"
    ui.running && ui.activeSide == Side.A -> "Đang nghe ${Languages.name(s.convLangA)}…"
    ui.running && ui.activeSide == Side.B -> "Đang nghe ${Languages.name(s.convLangB)}…"
    ui.running -> ui.status.ifBlank { "Tự động nhận biết người nói" }
    else -> "Bấm micro giữa để nghe tự động, hoặc micro mỗi bên để nói"
}

@Composable
private fun ConversationPanel(
    side: Side,
    ui: LiveUi,
    s: AppSettings,
    accent: Color,
    onPickLang: () -> Unit,
    onTalk: () -> Unit,
    onSpeak: (Utterance) -> Unit,
    modifier: Modifier = Modifier,
) {
    val myLang = if (side == Side.A) s.convLangA else s.convLangB
    val listeningMe = ui.running && ui.activeSide == side
    val listState = rememberLazyListState()
    val partialMine = ui.partial.isNotBlank() && ui.partialSide == side
    val partialOther = ui.partialTranslation.isNotBlank() && ui.partialSide != null && ui.partialSide != side
    val extra = if (partialMine || partialOther) 1 else 0
    val total = ui.items.size + extra

    LaunchedEffect(ui.items.size, extra, ui.partialTranslation, ui.items.lastOrNull()?.state) {
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LanguageChip(
                code = myLang,
                onClick = onPickLang,
                color = accent.copy(alpha = 0.16f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = onTalk,
                shape = RoundedCornerShape(50),
                color = if (listeningMe) MaterialTheme.colorScheme.error else accent,
                contentColor = Color.White,
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (listeningMe) Icons.Filled.Stop else Icons.Filled.Mic, null, Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(talkLabel(myLang, listeningMe), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (total == 0) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    hintText(myLang),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ui.items, key = { it.id }) { u ->
                    Bubble(u = u, mine = u.side == side, accent = accent, fontScale = s.fontScale, onSpeak = onSpeak)
                }
                if (extra == 1) {
                    item(key = "partial") {
                        val text = if (partialMine) ui.partial else ui.partialTranslation
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (partialMine) Arrangement.End else Arrangement.Start,
                        ) {
                            Text(
                                "$text …",
                                style = MaterialTheme.typography.bodyLarge,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.widthIn(max = 320.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(u: Utterance, mine: Boolean, accent: Color, fontScale: Float, onSpeak: (Utterance) -> Unit) {
    val big = MaterialTheme.typography.titleLarge.let {
        it.copy(fontSize = it.fontSize * fontScale, lineHeight = it.lineHeight * fontScale)
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            onClick = { if (!mine) onSpeak(u) },
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 18.dp,
            ),
            color = if (mine) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (mine) {
                    // Câu mình nói (ngôn ngữ của mình)
                    Text(u.source, style = MaterialTheme.typography.bodyLarge)
                } else {
                    // Câu người kia nói, đã dịch sang ngôn ngữ của mình
                    when (u.state) {
                        UState.TRANSLATING -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("…", style = big)
                        }

                        UState.ERROR -> Text(
                            u.error ?: "Lỗi dịch",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        else -> Text(u.translation, style = big, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${Languages.flag(u.sourceLang)} ${u.source}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun talkLabel(lang: String, listening: Boolean): String {
    if (listening) return when (lang) {
        "vi" -> "Dừng"; "zh" -> "停止"; "ja" -> "停止"; "ko" -> "중지"; "fr" -> "Arrêter"
        "de" -> "Stopp"; "es" -> "Detener"; "ru" -> "Стоп"; "th" -> "หยุด"; else -> "Stop"
    }
    return when (lang) {
        "vi" -> "Bấm để nói"; "zh" -> "点击说话"; "ja" -> "タップして話す"; "ko" -> "눌러서 말하기"
        "fr" -> "Parler"; "de" -> "Sprechen"; "es" -> "Hablar"; "ru" -> "Говорить"; "th" -> "แตะเพื่อพูด"
        "id", "ms" -> "Bicara"; "pt" -> "Falar"; "it" -> "Parla"; else -> "Tap to speak"
    }
}

private fun hintText(lang: String): String = when (lang) {
    "vi" -> "Hãy nói tiếng Việt.\nBản dịch của người đối diện sẽ hiện ở đây."
    "en" -> "Speak English.\nTranslations of the other person will appear here."
    "zh" -> "请说中文。\n对方的翻译将显示在这里。"
    "ja" -> "日本語で話してください。\n相手の翻訳がここに表示されます。"
    "ko" -> "한국어로 말씀하세요.\n상대방의 번역이 여기에 표시됩니다."
    "fr" -> "Parlez français.\nLa traduction de votre interlocuteur s'affichera ici."
    "de" -> "Sprechen Sie Deutsch.\nDie Übersetzung Ihres Gegenübers erscheint hier."
    "es" -> "Hable en español.\nLa traducción de la otra persona aparecerá aquí."
    "ru" -> "Говорите по-русски.\nПеревод собеседника появится здесь."
    "th" -> "พูดภาษาไทย\nคำแปลของอีกฝ่ายจะแสดงที่นี่"
    else -> "Speak ${Languages.byCode(lang)?.nativeName ?: lang}.\nTranslations will appear here."
}
