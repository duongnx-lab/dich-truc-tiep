package vn.dichtructiep.ui.home

import android.speech.SpeechRecognizer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.dichtructiep.app
import vn.dichtructiep.data.Languages
import vn.dichtructiep.data.SttEngine
import vn.dichtructiep.data.TranslateEngine
import vn.dichtructiep.ui.Routes
import vn.dichtructiep.ui.components.InfoBanner

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val s by app.settings.collectAsState()
    val downloaded by app.models.downloaded.collectAsState()
    val recognizerOk = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dịch Trực Tiếp", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Phiên dịch giọng nói theo thời gian thực",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onNavigate(Routes.SETTINGS) }) {
                Icon(Icons.Filled.Settings, contentDescription = "Cài đặt")
            }
        }

        // Cảnh báo cấu hình
        if (s.sttEngine == SttEngine.ANDROID && !recognizerOk) {
            InfoBanner(
                "Máy chưa có dịch vụ nhận dạng giọng nói của Google. Cài ứng dụng Google, hoặc chạm để chuyển sang Whisper (API).",
                isError = true,
                onClick = { onNavigate(Routes.SETTINGS) },
            )
        }
        if (s.sttEngine == SttEngine.CLOUD && !s.hasCloud) {
            InfoBanner(
                "Bạn đã chọn Whisper nhưng chưa nhập API key. Chạm để mở Cài đặt.",
                isError = true,
                onClick = { onNavigate(Routes.SETTINGS) },
            )
        }
        val needModels = listOf(s.listenTarget, s.convLangA, s.convLangB)
            .distinct()
            .filter { app.models.isSupported(it) && it !in downloaded }
        if (s.translateEngine == TranslateEngine.MLKIT && needModels.isNotEmpty()) {
            InfoBanner(
                "Mẹo: tải trước gói offline " + needModels.joinToString { Languages.name(it) } +
                    " để dịch tức thì, không cần mạng. Chạm để tải.",
                onClick = { onNavigate(Routes.MODELS) },
            )
        }

        ModeCard(
            icon = Icons.Filled.Hearing,
            title = "Nghe & Dịch",
            description = "Đặt điện thoại trong phòng: tự nhận diện ngôn ngữ người nói và hiện phụ đề dịch liên tục.",
            tag = "Tự nhận diện  →  ${Languages.label(s.listenTarget)}",
            brush = Brush.linearGradient(listOf(scheme.primary, Color(0xFF5C6BC0))),
            onClick = { onNavigate(Routes.LISTEN) },
        )
        ModeCard(
            icon = Icons.Filled.Forum,
            title = "Hội thoại 2 người",
            description = "Hai người hai thứ tiếng nói chuyện trực tiếp. Ứng dụng tự biết ai đang nói, dịch và đọc to.",
            tag = "${Languages.label(s.convLangA)}  ⇄  ${Languages.label(s.convLangB)}",
            brush = Brush.linearGradient(listOf(Color(0xFF00796B), Color(0xFF26A69A))),
            onClick = { onNavigate(Routes.CONVERSATION) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallCard(Icons.Filled.Translate, "Dịch văn bản", "Gõ, dán hoặc đọc", Modifier.weight(1f)) {
                onNavigate(Routes.TEXT)
            }
            SmallCard(Icons.Filled.History, "Lịch sử", "Các phiên đã dịch", Modifier.weight(1f)) {
                onNavigate(Routes.HISTORY)
            }
        }

        Surface(
            onClick = { onNavigate(Routes.SETTINGS) },
            shape = RoundedCornerShape(16.dp),
            color = scheme.surfaceVariant.copy(alpha = 0.6f),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Cấu hình hiện tại", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                Spacer(Modifier.height(4.dp))
                Text("🎙  Nhận dạng: ${s.sttEngine.label}", style = MaterialTheme.typography.bodyMedium)
                Text("🌐  Dịch: ${s.translateEngine.label}", style = MaterialTheme.typography.bodyMedium)
                if (s.sttEngine == SttEngine.ANDROID) {
                    Text(
                        "👂  Ngôn ngữ dự kiến (Nghe & Dịch): " +
                            s.listenExpected.joinToString { Languages.name(it) }.ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    description: String,
    tag: String,
    brush: Brush,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(brush)
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.height(10.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) {
                    Text(
                        tag,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
