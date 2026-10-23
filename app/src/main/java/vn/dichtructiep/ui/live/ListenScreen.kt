package vn.dichtructiep.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.dichtructiep.app
import vn.dichtructiep.data.Languages
import vn.dichtructiep.data.SttEngine
import vn.dichtructiep.live.LiveMode
import vn.dichtructiep.live.LiveViewModel
import vn.dichtructiep.live.UState
import vn.dichtructiep.live.Utterance
import vn.dichtructiep.ui.components.InfoBanner
import vn.dichtructiep.ui.components.KeepScreenOn
import vn.dichtructiep.ui.components.LanguageChip
import vn.dichtructiep.ui.components.LanguagePickerDialog
import vn.dichtructiep.ui.components.LevelBars
import vn.dichtructiep.ui.components.MicButton
import vn.dichtructiep.ui.components.MultiLanguagePickerDialog
import vn.dichtructiep.ui.components.copyText
import vn.dichtructiep.ui.components.rememberMicPermission
import vn.dichtructiep.ui.components.shareText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(onBack: () -> Unit, onSettings: () -> Unit) {
    val vm: LiveViewModel = viewModel(factory = LiveViewModel.factory(LiveMode.LISTEN))
    val ui by vm.ui.collectAsState()
    val s by vm.settings.collectAsState()
    val downloading by vm.downloading.collectAsState()
    val context = LocalContext.current

    var pickTarget by remember { mutableStateOf(false) }
    var pickExpected by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    val startWithPermission = rememberMicPermission { vm.start(null) }
    val onMic = { if (ui.running) vm.stop() else startWithPermission() }

    KeepScreenOn(ui.running && s.keepScreenOn)

    val listState = rememberLazyListState()
    val hasPartial = ui.partial.isNotBlank()
    val total = ui.items.size + if (hasPartial) 1 else 0
    LaunchedEffect(ui.items.size, hasPartial, ui.partialTranslation) {
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Nghe & Dịch", fontWeight = FontWeight.SemiBold)
                        val sub = when {
                            downloading.isNotEmpty() ->
                                "Đang tải gói " + downloading.joinToString { Languages.name(it) } + "…"
                            ui.running -> ui.status.ifBlank { "Đang nghe…" }
                            else -> "Đã dừng"
                        }
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
                },
                actions = {
                    IconButton(onClick = { fullscreen = true }) { Icon(Icons.Filled.Fullscreen, "Phụ đề toàn màn hình") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Thêm") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Phiên mới") },
                            leadingIcon = { Icon(Icons.Filled.Add, null) },
                            onClick = { menu = false; vm.newSession() },
                        )
                        DropdownMenuItem(
                            text = { Text("Sao chép toàn bộ") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                            onClick = { menu = false; context.copyText(vm.transcript()) },
                        )
                        DropdownMenuItem(
                            text = { Text("Chia sẻ") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = { menu = false; context.shareText(vm.transcript()) },
                        )
                        DropdownMenuItem(
                            text = { Text("Dừng đọc") },
                            leadingIcon = { Icon(Icons.Filled.Stop, null) },
                            onClick = { menu = false; vm.stopSpeaking() },
                        )
                        DropdownMenuItem(
                            text = { Text("Cài đặt") },
                            leadingIcon = { Icon(Icons.Filled.Settings, null) },
                            onClick = { menu = false; onSettings() },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        LevelBars(ui.level, ui.running)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            ui.lastDetected?.let { "Phát hiện: ${Languages.label(it)}" } ?: "Chưa phát hiện",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MicButton(running = ui.running, level = ui.level, onClick = onMic)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        FilterChip(
                            selected = s.ttsListen,
                            onClick = { vm.updateSettings(restart = false) { it.copy(ttsListen = !it.ttsListen) } },
                            label = { Text("Đọc to") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Thanh ngôn ngữ
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (s.sttEngine == SttEngine.ANDROID) {
                    Surface(
                        onClick = { pickExpected = true },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            "Người nói: " + s.listenExpected.joinToString(" · ") { Languages.flag(it) + " " + Languages.name(it) }
                                .ifBlank { "chọn ngôn ngữ" },
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            "🌐 Tự nhận diện mọi ngôn ngữ",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
                Text("→", style = MaterialTheme.typography.titleMedium)
                LanguageChip(code = s.listenTarget, onClick = { pickTarget = true })
                FilterChip(
                    selected = s.showSource,
                    onClick = { vm.updateSettings(restart = false) { it.copy(showSource = !it.showSource) } },
                    label = { Text("Bản gốc") },
                )
                TextButton(onClick = {
                    vm.updateSettings(restart = false) { it.copy(fontScale = (it.fontScale - 0.15f).coerceAtLeast(0.8f)) }
                }) { Text("A−") }
                TextButton(onClick = {
                    vm.updateSettings(restart = false) { it.copy(fontScale = (it.fontScale + 0.15f).coerceAtMost(2.2f)) }
                }) { Text("A+", fontWeight = FontWeight.Bold) }
            }

            ui.error?.let { err ->
                InfoBanner(
                    "$err  (chạm để ẩn)",
                    isError = true,
                    onClick = { vm.dismissError() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            if (total == 0) {
                EmptyListenHint(
                    target = s.listenTarget,
                    android = s.sttEngine == SttEngine.ANDROID,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(ui.items, key = { it.id }) { u ->
                        UtteranceCard(
                            u = u,
                            showSource = s.showSource,
                            fontScale = s.fontScale,
                            onSpeak = { vm.speak(u) },
                            onCopy = { context.copyText(u.translation.ifBlank { u.source }) },
                            onRetry = { vm.retry(u) },
                        )
                    }
                    if (hasPartial) {
                        item(key = "partial") {
                            PartialCard(ui.partial, ui.partialTranslation, ui.partialLang, s.fontScale)
                        }
                    }
                }
            }
        }
    }

    if (pickTarget) {
        val ready by context.app.models.downloaded.collectAsState()
        LanguagePickerDialog(
            title = "Dịch sang",
            selected = s.listenTarget,
            offlineReady = ready,
            onDismiss = { pickTarget = false },
            onPick = { vm.setListenTarget(it.code) },
        )
    }
    if (pickExpected) {
        MultiLanguagePickerDialog(
            title = "Người nói dùng ngôn ngữ nào?",
            subtitle = "Bộ nhận dạng Android cần biết trước (tối đa 3). Ứng dụng sẽ tự phân biệt giữa các ngôn ngữ này. " +
                "Muốn tự nhận diện mọi ngôn ngữ, hãy chọn Whisper trong Cài đặt.",
            initial = s.listenExpected,
            max = 3,
            onDismiss = { pickExpected = false },
            onConfirm = { if (it.isNotEmpty()) vm.setListenExpected(it) },
        )
    }

    AnimatedVisibility(visible = fullscreen) {
        FullscreenCaptions(
            items = ui.items,
            partialTranslation = ui.partialTranslation.ifBlank { ui.partial },
            running = ui.running,
            fontScale = s.fontScale,
            onMic = onMic,
            onClose = { fullscreen = false },
        )
    }
    }
}

@Composable
private fun EmptyListenHint(target: String, android: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Hearing, contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Bấm micro để bắt đầu",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Đặt điện thoại hướng về phía người nói. Ứng dụng sẽ tự nhận diện ngôn ngữ và hiện phụ đề " +
                "dịch sang ${Languages.name(target)} theo thời gian thực." +
                if (android) "\n\nĐang dùng nhận dạng Android: chạm \"Người nói\" để khai báo ngôn ngữ dự kiến." else "",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun UtteranceCard(
    u: Utterance,
    showSource: Boolean,
    fontScale: Float,
    onSpeak: () -> Unit,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
) {
    val big = MaterialTheme.typography.titleLarge.let {
        it.copy(fontSize = it.fontSize * fontScale, lineHeight = it.lineHeight * fontScale)
    }
    val small = MaterialTheme.typography.bodyMedium.let {
        it.copy(fontSize = it.fontSize * fontScale.coerceAtMost(1.5f), lineHeight = it.lineHeight * fontScale.coerceAtMost(1.5f))
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${Languages.flag(u.sourceLang)} ${Languages.name(u.sourceLang)}  →  ${Languages.flag(u.targetLang)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    vn.dichtructiep.data.HistoryRepository.formatTime(u.time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            when (u.state) {
                UState.TRANSLATING -> {
                    Text(u.source, style = big, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Đang dịch…", style = MaterialTheme.typography.labelMedium)
                    }
                }

                UState.DONE -> {
                    Text(u.translation, style = big, fontWeight = FontWeight.Medium)
                    if (showSource) {
                        Spacer(Modifier.height(4.dp))
                        Text(u.source, style = small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                UState.SAME_LANGUAGE -> {
                    Text(u.source, style = big)
                    Text(
                        "(đã là ${Languages.name(u.targetLang)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                UState.ERROR -> {
                    Text(u.source, style = small)
                    Text(u.error ?: "Lỗi dịch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Thử lại") }
                }
            }
            if (u.state == UState.DONE || u.state == UState.SAME_LANGUAGE) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onSpeak, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, "Đọc", Modifier.size(18.dp))
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.ContentCopy, "Sao chép", Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PartialCard(partial: String, partialTranslation: String, lang: String?, fontScale: Float) {
    val big = MaterialTheme.typography.titleLarge.let {
        it.copy(fontSize = it.fontSize * fontScale, lineHeight = it.lineHeight * fontScale)
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "● Đang nói" + (lang?.let { " · ${Languages.label(it)}" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            if (partialTranslation.isNotBlank()) {
                Text(partialTranslation, style = big, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
            }
            Text(
                partial,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Chế độ phụ đề toàn màn hình: chữ lớn trên nền tối, phù hợp đặt trên bàn hoặc chiếu cho nhiều người xem. */
@Composable
private fun FullscreenCaptions(
    items: List<Utterance>,
    partialTranslation: String,
    running: Boolean,
    fontScale: Float,
    onMic: () -> Unit,
    onClose: () -> Unit,
) {
    val lines = items.filter { it.state == UState.DONE || it.state == UState.SAME_LANGUAGE }.takeLast(3)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onClose)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            lines.forEachIndexed { i, u ->
                val alpha = if (i == lines.lastIndex && partialTranslation.isBlank()) 1f else 0.55f
                Text(
                    u.translation,
                    color = Color.White.copy(alpha = alpha),
                    fontSize = (30 * fontScale).sp,
                    lineHeight = (38 * fontScale).sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (partialTranslation.isNotBlank()) {
                Text(
                    partialTranslation,
                    color = Color(0xFFFFF59D),
                    fontSize = (30 * fontScale).sp,
                    lineHeight = (38 * fontScale).sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (lines.isEmpty() && partialTranslation.isBlank()) {
                Text(
                    if (running) "Đang nghe…" else "Bấm micro để bắt đầu",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 24.sp,
                )
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Filled.Close, "Thoát toàn màn hình", tint = Color.White)
        }
        MicButton(
            running = running,
            level = 0f,
            onClick = onMic,
            size = 56.dp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        )
    }
}
