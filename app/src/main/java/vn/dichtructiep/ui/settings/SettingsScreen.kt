package vn.dichtructiep.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.dichtructiep.app
import vn.dichtructiep.data.ApiProvider
import vn.dichtructiep.data.AppSettings
import vn.dichtructiep.data.SttEngine
import vn.dichtructiep.data.TranslateEngine
import vn.dichtructiep.ui.components.SectionLabel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onModels: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val s by app.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val update: ((AppSettings) -> AppSettings) -> Unit = { app.updateSettings(it) }

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            // ---------------------------------------------------- Nhận dạng
            SectionLabel("Nhận dạng giọng nói")
            SttEngine.entries.forEach { e ->
                ChoiceCard(
                    title = e.label,
                    desc = e.desc,
                    selected = s.sttEngine == e,
                    onClick = { update { it.copy(sttEngine = e) } },
                )
            }
            if (s.sttEngine == SttEngine.ANDROID) {
                SwitchRow(
                    "Ưu tiên nhận dạng offline",
                    "Dùng bộ nhận dạng trên máy (Android 12+) nếu đã có gói ngôn ngữ. Nhanh, riêng tư, không cần mạng.",
                    s.preferOnDevice,
                ) { v -> update { it.copy(preferOnDevice = v) } }
                SwitchRow(
                    "Giảm tiếng \"bíp\" khi nghe liên tục",
                    "Tạm tắt âm thông báo/hệ thống trong lúc nghe; tự bật lại khi dừng. Hiệu quả tùy dòng máy.",
                    s.muteBeep,
                ) { v -> update { it.copy(muteBeep = v) } }
            } else {
                SliderRow(
                    "Độ nhạy micro",
                    "Cao hơn: bắt được giọng nói nhỏ/xa, nhưng dễ nhận nhầm tiếng ồn.",
                    s.vadSensitivity, 0f..1f,
                    label = "${(s.vadSensitivity * 100).toInt()}%",
                ) { v -> update { it.copy(vadSensitivity = v) } }
                SwitchRow(
                    "Phụ đề tạm thời khi đang nói",
                    "Hiện chữ ngay trong lúc người nói chưa dứt câu (gọi API thêm ~2 giây/lần).",
                    s.cloudPartials,
                ) { v -> update { it.copy(cloudPartials = v) } }
            }

            // ---------------------------------------------------- Dịch
            SectionLabel("Dịch")
            TranslateEngine.entries.forEach { e ->
                ChoiceCard(
                    title = e.label,
                    desc = e.desc,
                    selected = s.translateEngine == e,
                    onClick = { update { it.copy(translateEngine = e) } },
                )
            }
            SwitchRow("Tự tải gói ngôn ngữ offline khi cần", null, s.autoDownloadModels) { v ->
                update { it.copy(autoDownloadModels = v) }
            }
            SwitchRow("Chỉ tải qua Wi-Fi", null, s.wifiOnlyDownload) { v -> update { it.copy(wifiOnlyDownload = v) } }
            ClickRow("Gói ngôn ngữ offline", "Tải trước / xóa các gói dịch ML Kit", onModels)

            // ---------------------------------------------------- API
            SectionLabel("API đám mây (Whisper + AI dịch)")
            Text(
                "Dùng cho nhận dạng Whisper và dịch AI. Groq có gói miễn phí, tốc độ rất nhanh; " +
                    "OpenAI tính phí theo lượng dùng. Mọi dịch vụ tương thích OpenAI đều dùng được.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApiProvider.entries.forEach { p ->
                    FilterChip(
                        selected = s.provider == p,
                        onClick = {
                            update {
                                if (p == ApiProvider.CUSTOM) it.copy(provider = p)
                                else it.copy(provider = p, baseUrl = p.baseUrl, sttModel = p.sttModel, chatModel = p.chatModel)
                            }
                            testResult = null
                        },
                        label = { Text(p.label) },
                    )
                }
            }
            SettingTextField("Địa chỉ API (Base URL)", s.baseUrl, keyboard = KeyboardType.Uri) { v ->
                update { it.copy(baseUrl = v.trim()) }
            }
            SettingTextField("API key", s.apiKey, secret = true) { v -> update { it.copy(apiKey = v.trim()) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    SettingTextField("Mô hình nhận dạng", s.sttModel) { v -> update { it.copy(sttModel = v.trim()) } }
                }
                Column(Modifier.weight(1f)) {
                    SettingTextField("Mô hình dịch", s.chatModel) { v -> update { it.copy(chatModel = v.trim()) } }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = s.hasCloud && !testing,
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            testResult = try {
                                app.api.test(app.settings.value.cloud)
                            } catch (e: Exception) {
                                "❌ ${e.message ?: e.javaClass.simpleName}"
                            }
                            testing = false
                        }
                    },
                ) {
                    if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Kiểm tra kết nối")
                }
                if (s.provider.keyUrl.isNotBlank()) {
                    OutlinedButton(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.provider.keyUrl)))
                        } catch (_: ActivityNotFoundException) {
                        }
                    }) { Text("Lấy API key") }
                }
            }
            testResult?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "API key chỉ lưu trong bộ nhớ riêng của ứng dụng trên máy này.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            // ---------------------------------------------------- Giọng đọc
            SectionLabel("Giọng đọc bản dịch")
            SliderRow(
                "Tốc độ đọc", null, s.ttsRate, 0.5f..2f, label = String.format("%.1fx", s.ttsRate),
            ) { v -> update { it.copy(ttsRate = v) } }
            SwitchRow(
                "Đọc to trong chế độ Nghe & Dịch",
                "Nên dùng tai nghe để micro không thu lại giọng đọc.",
                s.ttsListen,
            ) { v -> update { it.copy(ttsListen = v) } }
            SwitchRow("Đọc to trong chế độ Hội thoại", null, s.ttsConversation) { v ->
                update { it.copy(ttsConversation = v) }
            }
            SwitchRow(
                "Tạm ngắt micro khi đang đọc (Hội thoại)",
                "Tránh ứng dụng tự nghe lại giọng đọc của chính nó.",
                s.pauseWhileSpeaking,
            ) { v -> update { it.copy(pauseWhileSpeaking = v) } }
            ClickRow("Giọng đọc của hệ thống", "Cài thêm giọng/ngôn ngữ cho Text-to-Speech") {
                try {
                    context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                } catch (_: Exception) {
                    Toast.makeText(context, "Không mở được cài đặt TTS", Toast.LENGTH_SHORT).show()
                }
            }

            // ---------------------------------------------------- Hiển thị
            SectionLabel("Hiển thị")
            SliderRow(
                "Cỡ chữ phụ đề", null, s.fontScale, 0.8f..2.2f, label = "${(s.fontScale * 100).toInt()}%",
            ) { v -> update { it.copy(fontScale = v) } }
            SwitchRow("Hiện câu gốc dưới bản dịch", null, s.showSource) { v -> update { it.copy(showSource = v) } }
            SwitchRow("Giữ màn hình sáng khi đang dịch", null, s.keepScreenOn) { v ->
                update { it.copy(keepScreenOn = v) }
            }

            SectionLabel("Giới thiệu")
            Text(
                "Dịch Trực Tiếp 1.0 — nhận dạng giọng nói liên tục, tự phát hiện ngôn ngữ (Whisper / Android + ML Kit " +
                    "Language ID) và dịch theo thời gian thực (ML Kit offline hoặc AI đám mây).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ChoiceCard(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (desc != null) {
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun ClickRow(title: String, desc: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (desc != null) {
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun SliderRow(
    title: String,
    desc: String?,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onChange: (Float) -> Unit,
) {
    var local by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (desc != null) {
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = range,
        )
    }
}

/** Ô nhập giữ trạng thái cục bộ khi đang gõ để con trỏ không bị nhảy. */
@Composable
private fun SettingTextField(
    label: String,
    value: String,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    onCommit: (String) -> Unit,
) {
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!focused && value != text) text = value
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onCommit(it)
        },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret && !show) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
        trailingIcon = if (secret) {
            {
                IconButton(onClick = { show = !show }) {
                    Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Hiện/ẩn")
                }
            }
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onFocusChanged { focused = it.isFocused },
    )
}
