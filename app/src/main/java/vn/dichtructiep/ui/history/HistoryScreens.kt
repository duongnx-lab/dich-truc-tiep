package vn.dichtructiep.ui.history

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.dichtructiep.app
import vn.dichtructiep.data.HistoryRepository
import vn.dichtructiep.data.Languages
import vn.dichtructiep.data.SessionRecord
import vn.dichtructiep.ui.components.shareText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val repo = context.app.history
    val sessions by repo.sessions.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lịch sử", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
                },
                actions = {
                    if (sessions.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, "Xóa tất cả") }
                    }
                },
            )
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.History, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text("Chưa có phiên dịch nào", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sessions, key = { it.id }) { r ->
                    SessionRow(r, onClick = { onOpen(r.id) }, onDelete = { scope.launch { repo.delete(r.id) } })
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Xóa toàn bộ lịch sử?") },
            text = { Text("Không thể hoàn tác.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; scope.launch { repo.clear() } }) { Text("Xóa") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Hủy") } },
        )
    }
}

@Composable
private fun SessionRow(r: SessionRecord, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (r.mode) {
                    "listen" -> Icons.Filled.Hearing
                    "conversation" -> Icons.Filled.Forum
                    else -> Icons.Filled.Translate
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${HistoryRepository.modeLabel(r.mode)} · ${r.title}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${HistoryRepository.formatDate(r.startedAt)} · ${r.entries.size} câu",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                r.entries.lastOrNull()?.let {
                    Text(
                        it.translation,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Xóa") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app.history
    val sessions by repo.sessions.collectAsState()
    val record = sessions.firstOrNull { it.id == id }
    val scope = rememberCoroutineScope()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null && record != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(HistoryRepository.toPlainText(record).toByteArray(Charsets.UTF_8))
                }
            }.onSuccess { Toast.makeText(context, "Đã lưu tệp", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Không lưu được: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(record?.title ?: "Phiên dịch", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        record?.let {
                            Text(
                                HistoryRepository.formatDate(it.startedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
                },
                actions = {
                    if (record != null) {
                        IconButton(onClick = { context.shareText(HistoryRepository.toPlainText(record)) }) {
                            Icon(Icons.Filled.Share, "Chia sẻ")
                        }
                        IconButton(onClick = { exporter.launch("dich-${record.startedAt}.txt") }) {
                            Icon(Icons.Filled.SaveAlt, "Lưu tệp .txt")
                        }
                        IconButton(onClick = { scope.launch { repo.delete(record.id); onBack() } }) {
                            Icon(Icons.Filled.Delete, "Xóa")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (record == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding), contentAlignment = Alignment.Center
            ) { Text("Không tìm thấy phiên") }
            return@Scaffold
        }
        SelectionContainer(Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(record.entries) { e ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                buildString {
                                    append(HistoryRepository.formatTime(e.time))
                                    when (e.side) {
                                        1 -> append(" · Người A")
                                        2 -> append(" · Người B")
                                    }
                                    append(" · ${Languages.label(e.sourceLang)} → ${Languages.label(e.targetLang)}")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(e.translation, style = MaterialTheme.typography.titleMedium)
                            if (e.translation != e.source) {
                                Text(
                                    e.source,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
