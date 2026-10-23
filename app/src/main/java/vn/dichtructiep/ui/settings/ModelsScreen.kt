package vn.dichtructiep.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.dichtructiep.app
import vn.dichtructiep.data.Languages

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val models = app.models
    val downloaded by models.downloaded.collectAsState()
    val downloading by models.downloading.collectAsState()
    val s by app.settings.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { models.refresh() }

    val supported = Languages.all.filter { models.isSupported(it.code) }
    val inUse = listOf(s.listenTarget, s.convLangA, s.convLangB, s.textTarget)
        .plus(s.listenExpected)
        .distinct()
        .filter { models.isSupported(it) }
    val missingInUse = inUse.filter { it !in downloaded }

    fun download(code: String) {
        scope.launch {
            try {
                models.download(code, s.wifiOnlyDownload)
            } catch (e: Exception) {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gói ngôn ngữ offline", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column {
                    Text(
                        "Mỗi gói khoảng 30 MB, cho phép dịch hoàn toàn offline bằng Google ML Kit. " +
                            "Khmer, Lào, Myanmar chưa có gói offline – cần dịch AI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (missingInUse.isNotEmpty()) {
                        Spacer(Modifier.size(10.dp))
                        Button(onClick = { missingInUse.forEach { download(it) } }) {
                            Text("Tải các ngôn ngữ đang dùng (" + missingInUse.joinToString { Languages.name(it) } + ")")
                        }
                    }
                }
            }
            items(supported, key = { it.code }) { lang ->
                val isDown = lang.code in downloaded
                val isLoading = lang.code in downloading
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(lang.flag, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(lang.nameVi, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when {
                                    isLoading -> "Đang tải…"
                                    isDown -> "Đã sẵn sàng"
                                    else -> "Chưa tải"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDown) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        when {
                            isLoading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            isDown -> {
                                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                                if (lang.code != "en") {
                                    IconButton(onClick = { scope.launch { models.delete(lang.code) } }) {
                                        Icon(Icons.Filled.Delete, "Xóa gói")
                                    }
                                }
                            }

                            else -> IconButton(onClick = { download(lang.code) }) {
                                Icon(Icons.Filled.CloudDownload, "Tải gói")
                            }
                        }
                    }
                }
            }
        }
    }
}
