package vn.dichtructiep.ui.text

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.dichtructiep.App
import vn.dichtructiep.app
import vn.dichtructiep.data.Languages
import vn.dichtructiep.ui.components.InfoBanner
import vn.dichtructiep.ui.components.LanguageChip
import vn.dichtructiep.ui.components.LanguagePickerDialog
import vn.dichtructiep.ui.components.copyText
import vn.dichtructiep.ui.components.shareText

class TextViewModel(private val app: App) : ViewModel() {
    var input by mutableStateOf("")
    var source by mutableStateOf<String?>(null) // null = tự nhận diện
    var detected by mutableStateOf<String?>(null)
    var result by mutableStateOf("")
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var job: Job? = null

    fun translate(target: String, debounceMs: Long = 0) {
        job?.cancel()
        val text = input
        if (text.isBlank()) {
            result = ""; detected = null; error = null
            return
        }
        job = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            busy = true
            error = null
            try {
                val src = source ?: app.detector.resolve(text, null, false, emptyList())
                detected = src
                result = app.translator.translate(app.settings.value, text, src, target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Lỗi dịch"
            } finally {
                busy = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TextViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextTranslateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val vm: TextViewModel = viewModel(factory = TextViewModel.Factory)
    val s by app.settings.collectAsState()
    val shared by app.sharedText.collectAsState()
    val ready by app.models.downloaded.collectAsState()
    var pickSource by remember { mutableStateOf(false) }
    var pickTarget by remember { mutableStateOf(false) }

    LaunchedEffect(shared) {
        shared?.let {
            vm.input = it
            app.sharedText.value = null
            vm.translate(s.textTarget)
        }
    }
    LaunchedEffect(vm.input, vm.source, s.textTarget) {
        vm.translate(s.textTarget, debounceMs = 800)
    }

    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) vm.input = if (vm.input.isBlank()) text else vm.input.trimEnd() + " " + text
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dịch văn bản", fontWeight = FontWeight.SemiBold) },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = vm.source == null, onClick = { vm.source = null }, label = { Text("Tự nhận diện") })
                LanguageChip(
                    code = vm.source ?: vm.detected,
                    onClick = { pickSource = true },
                    modifier = Modifier.weight(1f, fill = false),
                )
                IconButton(onClick = {
                    val from = vm.source ?: vm.detected
                    if (from != null && from != s.textTarget) {
                        val oldTarget = s.textTarget
                        app.updateSettings { it.copy(textTarget = from) }
                        vm.source = oldTarget
                        if (vm.result.isNotBlank()) vm.input = vm.result
                    }
                }) { Icon(Icons.Filled.SwapHoriz, "Đảo chiều") }
                LanguageChip(code = s.textTarget, onClick = { pickTarget = true }, modifier = Modifier.weight(1f, fill = false))
            }

            OutlinedTextField(
                value = vm.input,
                onValueChange = { vm.input = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                placeholder = { Text("Nhập, dán hoặc đọc văn bản cần dịch…") },
                trailingIcon = {
                    if (vm.input.isNotEmpty()) {
                        IconButton(onClick = { vm.input = "" }) { Icon(Icons.Filled.Clear, "Xóa") }
                    }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val tag = Languages.byCode(vm.source ?: vm.detected)?.speechTag
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        if (tag != null) putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Hãy nói…")
                    }
                    try {
                        dictate.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "Máy không có trình nhận dạng giọng nói", Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Filled.Mic, "Đọc để nhập") }
                IconButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!clip.isNullOrBlank()) vm.input = clip
                }) { Icon(Icons.Filled.ContentPaste, "Dán") }
                Spacer(Modifier.weight(1f))
                if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("${vm.input.length} ký tự", style = MaterialTheme.typography.labelSmall)
            }

            vm.error?.let { InfoBanner(it, isError = true) }

            if (vm.result.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "${Languages.label(vm.detected)}  →  ${Languages.label(s.textTarget)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer {
                            Text(vm.result, style = MaterialTheme.typography.titleLarge)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = {
                                Languages.byCode(s.textTarget)?.let { app.speaker.speak(vm.result, it, s.ttsRate) }
                            }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, "Đọc") }
                            IconButton(onClick = { context.copyText(vm.result) }) { Icon(Icons.Filled.ContentCopy, "Sao chép") }
                            IconButton(onClick = { context.shareText(vm.result) }) { Icon(Icons.Filled.Share, "Chia sẻ") }
                        }
                    }
                }
            }
        }
    }

    if (pickSource) {
        LanguagePickerDialog(
            title = "Ngôn ngữ nguồn",
            selected = vm.source,
            offlineReady = ready,
            onDismiss = { pickSource = false },
            onPick = { vm.source = it.code },
        )
    }
    if (pickTarget) {
        LanguagePickerDialog(
            title = "Dịch sang",
            selected = s.textTarget,
            offlineReady = ready,
            onDismiss = { pickTarget = false },
            onPick = { lang -> app.updateSettings { it.copy(textTarget = lang.code) } },
        )
    }
}
