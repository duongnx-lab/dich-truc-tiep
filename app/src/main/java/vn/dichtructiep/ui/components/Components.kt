package vn.dichtructiep.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import vn.dichtructiep.data.Language
import vn.dichtructiep.data.Languages

// ---------------------------------------------------------------- Tiện ích

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Giữ màn hình sáng khi [enabled] = true. */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = context.findActivity()?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/** Trả về một hàm: gọi để chạy [onGranted] sau khi đảm bảo đã có quyền micro. */
@Composable
fun rememberMicPermission(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pending?.invoke()
        else Toast.makeText(context, "Cần quyền micro để nghe và dịch", Toast.LENGTH_LONG).show()
        pending = null
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) onGranted()
        else {
            pending = onGranted
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

fun Context.shareText(text: String, title: String = "Chia sẻ bản dịch") {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(i, title))
}

fun Context.copyText(text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("Dịch Trực Tiếp", text))
    Toast.makeText(this, "Đã sao chép", Toast.LENGTH_SHORT).show()
}

// ---------------------------------------------------------------- Chọn ngôn ngữ

/** Nút hiển thị ngôn ngữ hiện tại, bấm để đổi. */
@Composable
fun LanguageChip(
    code: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = color,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (prefix != null) {
                Text(prefix, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                Languages.label(code),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun LanguagePickerDialog(
    title: String,
    selected: String?,
    offlineReady: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onPick: (Language) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val list = remember(query) { filterLanguages(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                SearchField(query) { query = it }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it.code }) { lang ->
                        LanguageRow(
                            lang = lang,
                            selected = lang.code == selected,
                            offline = lang.code in offlineReady,
                            onClick = { onPick(lang); onDismiss() },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
    )
}

@Composable
fun MultiLanguagePickerDialog(
    title: String,
    subtitle: String,
    initial: List<String>,
    max: Int,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    val list = remember(query) { filterLanguages(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                SearchField(query) { query = it }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(list, key = { it.code }) { lang ->
                        val isOn = lang.code in chosen
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (isOn) chosen.remove(lang.code)
                                    else if (chosen.size < max) chosen.add(lang.code)
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = isOn, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text("${lang.flag}  ${lang.nameVi}", modifier = Modifier.weight(1f))
                            Text(
                                lang.nativeName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen.toList()); onDismiss() }) { Text("Xong (${chosen.size}/$max)") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

private fun filterLanguages(query: String): List<Language> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return Languages.all
    return Languages.all.filter {
        it.nameVi.lowercase().contains(q) || it.nativeName.lowercase().contains(q) ||
            it.whisperName.contains(q) || it.code == q
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        placeholder = { Text("Tìm ngôn ngữ…") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LanguageRow(lang: Language, selected: Boolean, offline: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(lang.flag, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(lang.nameVi, style = MaterialTheme.typography.bodyLarge)
            Text(
                lang.nativeName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (offline) {
            Icon(
                Icons.Filled.CloudDone, contentDescription = "Đã có gói offline",
                tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp)
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------------------------------------------------------------- Micro & sóng âm

/** Nút micro lớn, có vòng sáng nhịp theo âm lượng khi đang nghe. */
@Composable
fun MicButton(
    running: Boolean,
    level: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String = if (running) "Dừng" else "Bắt đầu nghe",
) {
    val lvl by animateFloatAsState(if (running) level else 0f, tween(120), label = "lvl")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val breathe by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "breathe",
    )
    Box(modifier.size(size * 1.6f), contentAlignment = Alignment.Center) {
        if (running) {
            Box(
                Modifier
                    .size(size)
                    .scale(breathe + lvl * 0.45f)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.22f))
            )
        }
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (running) MaterialTheme.colorScheme.error else color,
            contentColor = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier.size(size),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (running) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(size * 0.45f),
                )
            }
        }
    }
}

/** Thanh sóng âm nhỏ. */
@Composable
fun LevelBars(level: Float, active: Boolean, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondary) {
    val lvl by animateFloatAsState(if (active) level else 0f, tween(100), label = "bars")
    val weights = remember { listOf(0.45f, 0.8f, 1f, 0.7f, 0.5f) }
    Canvas(modifier.size(width = 34.dp, height = 20.dp)) {
        val barW = size.width / (weights.size * 2 - 1)
        weights.forEachIndexed { i, w ->
            val h = (size.height * (0.15f + 0.85f * lvl * w)).coerceAtLeast(3f)
            drawRoundRect(
                color = if (active) color else color.copy(alpha = 0.35f),
                topLeft = Offset(i * barW * 2, (size.height - h) / 2),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2, barW / 2),
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, isError: Boolean = false) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
