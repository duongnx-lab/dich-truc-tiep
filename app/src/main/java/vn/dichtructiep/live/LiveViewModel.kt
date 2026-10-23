package vn.dichtructiep.live

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.dichtructiep.App
import vn.dichtructiep.data.AppSettings
import vn.dichtructiep.data.HistoryEntry
import vn.dichtructiep.data.HistoryRepository
import vn.dichtructiep.data.Language
import vn.dichtructiep.data.Languages
import vn.dichtructiep.data.SessionRecord
import vn.dichtructiep.data.SttEngine
import vn.dichtructiep.speech.AndroidSpeechEngine
import vn.dichtructiep.speech.CloudSpeechEngine
import vn.dichtructiep.speech.ListenConfig
import vn.dichtructiep.speech.SpeechEngine
import vn.dichtructiep.speech.SpeechEvent
import java.util.UUID

enum class LiveMode { LISTEN, CONVERSATION }

enum class Side { A, B }

enum class UState { TRANSLATING, DONE, SAME_LANGUAGE, ERROR }

data class Utterance(
    val id: Long,
    val side: Side?,
    val source: String,
    val sourceLang: String?,
    val targetLang: String,
    val translation: String = "",
    val state: UState = UState.TRANSLATING,
    val error: String? = null,
    val time: Long = System.currentTimeMillis(),
)

data class LiveUi(
    val running: Boolean = false,
    /** Hội thoại bấm-để-nói: phía đang được nghe. null = tự động. */
    val activeSide: Side? = null,
    val status: String = "",
    val level: Float = 0f,
    val partial: String = "",
    val partialLang: String? = null,
    val partialSide: Side? = null,
    val partialTranslation: String = "",
    val items: List<Utterance> = emptyList(),
    val error: String? = null,
    val speaking: Boolean = false,
    val lastDetected: String? = null,
)

private data class Route(val source: String?, val target: String, val side: Side?)

/**
 * Bộ não của 2 chế độ dịch trực tiếp:
 *  - LISTEN: nghe người nói (ngôn ngữ bất kỳ) → phụ đề dịch sang ngôn ngữ đích.
 *  - CONVERSATION: 2 người, 2 ngôn ngữ; tự nhận biết ai đang nói và dịch sang ngôn ngữ còn lại.
 *
 * Luồng xử lý: micro → SpeechEngine (Partial/Final) → xác định ngôn ngữ → hàng đợi dịch
 * tuần tự (giữ đúng thứ tự câu) → hiển thị + đọc to (TTS) → lưu lịch sử.
 */
class LiveViewModel(private val app: App, val mode: LiveMode) : ViewModel() {

    val settings: StateFlow<AppSettings> = app.settings
    val downloading: StateFlow<Set<String>> = app.models.downloading

    private val _ui = MutableStateFlow(LiveUi())
    val ui: StateFlow<LiveUi> = _ui.asStateFlow()

    private var engine: SpeechEngine? = null
    private var engineKind: SttEngine? = null
    private var eventsJob: Job? = null
    private var currentConfig: ListenConfig? = null

    private val finals = Channel<Pair<SpeechEvent.Final, Side?>>(Channel.UNLIMITED)
    private val translations = Channel<Long>(Channel.UNLIMITED)
    private var nextId = 1L

    private var partialJob: Job? = null
    private var lastPartialAt = 0L

    private var sessionId = UUID.randomUUID().toString()
    private var sessionStart = System.currentTimeMillis()
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            for ((event, side) in finals) handleFinal(event, side)
        }
        viewModelScope.launch {
            for (id in translations) processTranslation(id)
        }
        viewModelScope.launch {
            app.speaker.speaking.collect { speaking ->
                _ui.update { it.copy(speaking = speaking) }
                val s = settings.value
                if (mode == LiveMode.CONVERSATION && s.pauseWhileSpeaking && _ui.value.running) {
                    if (speaking) engine?.pause() else engine?.resume()
                }
            }
        }
    }

    // ------------------------------------------------------------------ Điều khiển

    fun toggle() {
        if (_ui.value.running) stop() else start(null)
    }

    /** Hội thoại: bấm micro của một phía để nói đúng 1 câu bằng ngôn ngữ của phía đó. */
    fun talk(side: Side) {
        val cur = _ui.value
        if (cur.running && cur.activeSide == side) {
            stop(); return
        }
        if (cur.running) engine?.stop()
        start(side)
    }

    fun start(side: Side?) {
        viewModelScope.launch {
            val s = app.settingsRepo.flow.first()
            if (!hasMicPermission()) {
                _ui.update { it.copy(error = "Cần cấp quyền micro để nghe và dịch.") }
                return@launch
            }
            if (s.sttEngine == SttEngine.CLOUD && !s.hasCloud) {
                _ui.update { it.copy(error = "Chưa nhập API key cho Whisper. Vào Cài đặt › API đám mây, hoặc chuyển sang nhận dạng Android.") }
                return@launch
            }
            if (mode == LiveMode.CONVERSATION) app.speaker.stop()
            val eng = ensureEngine(s)
            val cfg = buildConfig(s, side)
            currentConfig = cfg
            _ui.update {
                it.copy(
                    running = true, activeSide = side, error = null, status = "Đang khởi động…",
                    partial = "", partialTranslation = "", partialSide = side,
                )
            }
            eng.start(cfg)
        }
    }

    fun stop() {
        engine?.stop()
        partialJob?.cancel()
        _ui.update {
            it.copy(
                running = false, activeSide = null, level = 0f, status = "",
                partial = "", partialTranslation = "",
            )
        }
        scheduleSave(0)
    }

    fun dismissError() = _ui.update { it.copy(error = null) }

    fun stopSpeaking() = app.speaker.stop()

    fun speak(u: Utterance) {
        val text = if (u.state == UState.DONE || u.state == UState.SAME_LANGUAGE) u.translation else return
        val lang = Languages.byCode(u.targetLang) ?: return
        app.speaker.speak(text, lang, settings.value.ttsRate)
    }

    fun retry(u: Utterance) {
        updateItem(u.id) { it.copy(state = UState.TRANSLATING, error = null) }
        translations.trySend(u.id)
    }

    fun delete(u: Utterance) {
        _ui.update { st -> st.copy(items = st.items.filter { it.id != u.id }) }
        scheduleSave()
    }

    /** Bắt đầu phiên mới (phiên cũ đã được lưu vào lịch sử). */
    fun newSession() {
        viewModelScope.launch {
            saveJob?.cancel()
            saveNow()
            _ui.update { it.copy(items = emptyList(), partial = "", partialTranslation = "", lastDetected = null) }
            sessionId = UUID.randomUUID().toString()
            sessionStart = System.currentTimeMillis()
        }
    }

    // ------------------------------------------------------------------ Cài đặt nhanh

    fun updateSettings(restart: Boolean = true, transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            app.settingsRepo.update(transform)
            val cur = _ui.value
            if (restart && cur.running) {
                engine?.stop()
                start(cur.activeSide)
            }
        }
    }

    fun setListenTarget(code: String) = updateSettings { it.copy(listenTarget = code) }

    fun setListenExpected(codes: List<String>) = updateSettings { it.copy(listenExpected = codes) }

    fun setConvLang(side: Side, code: String) = updateSettings {
        if (side == Side.A) it.copy(convLangA = code) else it.copy(convLangB = code)
    }

    fun swapConvLangs() = updateSettings { it.copy(convLangA = it.convLangB, convLangB = it.convLangA) }

    // ------------------------------------------------------------------ Xuất văn bản

    fun transcript(): String = buildString {
        val s = settings.value
        for (u in _ui.value.items) {
            val who = when (u.side) {
                Side.A -> "[${Languages.name(s.convLangA)}] "
                Side.B -> "[${Languages.name(s.convLangB)}] "
                null -> ""
            }
            appendLine("${HistoryRepository.formatTime(u.time)} $who${u.source}")
            if (u.state != UState.SAME_LANGUAGE) appendLine("→ ${u.translation}")
            appendLine()
        }
    }.trim()

    // ------------------------------------------------------------------ Nội bộ

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun ensureEngine(s: AppSettings): SpeechEngine {
        val existing = engine
        if (existing != null && engineKind == s.sttEngine) return existing
        existing?.release()
        eventsJob?.cancel()
        val e: SpeechEngine = when (s.sttEngine) {
            SttEngine.ANDROID -> AndroidSpeechEngine(app)
            SttEngine.CLOUD -> CloudSpeechEngine(app, app.api, app.detector) { app.settings.value.cloud }
        }
        engine = e
        engineKind = s.sttEngine
        eventsJob = viewModelScope.launch { e.events.collect { onEvent(it) } }
        return e
    }

    private fun langA(s: AppSettings): Language = Languages.byCodeOr(s.convLangA, Languages.VI)
    private fun langB(s: AppSettings): Language = Languages.byCodeOr(s.convLangB, Languages.EN)

    private fun buildConfig(s: AppSettings, side: Side?): ListenConfig {
        val candidates: List<Language> = when (mode) {
            LiveMode.LISTEN ->
                if (s.sttEngine == SttEngine.CLOUD) emptyList()
                else (s.listenExpected.mapNotNull { Languages.byCode(it) } +
                    Languages.byCodeOr(s.listenTarget, Languages.VI))
                    .distinctBy { it.code }
                    .take(4)

            LiveMode.CONVERSATION -> listOf(langA(s), langB(s)).distinctBy { it.code }
        }
        val forced = when (side) {
            Side.A -> langA(s)
            Side.B -> langB(s)
            null -> null
        }
        return ListenConfig(
            candidates = candidates,
            forced = forced,
            singleUtterance = side != null,
            preferOnDevice = s.preferOnDevice,
            muteBeep = s.muteBeep,
            vadSensitivity = s.vadSensitivity,
            cloudPartials = s.cloudPartials,
            endSilenceMs = if (mode == LiveMode.LISTEN) 650 else 800,
            maxSegmentMs = if (mode == LiveMode.LISTEN) 12_000 else 20_000,
        )
    }

    private fun onEvent(e: SpeechEvent) {
        when (e) {
            is SpeechEvent.Level -> _ui.update { it.copy(level = e.value) }

            is SpeechEvent.Status -> {
                if (e.text.isEmpty() && currentConfig?.singleUtterance == true) {
                    _ui.update { it.copy(running = false, activeSide = null, level = 0f, status = "") }
                } else {
                    _ui.update { it.copy(status = e.text) }
                }
            }

            is SpeechEvent.Partial -> if (_ui.value.running) onPartial(e)

            is SpeechEvent.Final -> {
                finals.trySend(e to (_ui.value.activeSide ?: currentConfig?.forced?.let { sideOf(it.code) }))
            }

            is SpeechEvent.Error -> _ui.update {
                if (e.fatal) it.copy(error = e.message, running = false, activeSide = null, level = 0f, status = "")
                else it.copy(error = e.message)
            }
        }
    }

    private fun sideOf(code: String): Side? {
        if (mode != LiveMode.CONVERSATION) return null
        val s = settings.value
        return when (code) {
            s.convLangA -> Side.A
            s.convLangB -> Side.B
            else -> null
        }
    }

    private fun onPartial(e: SpeechEvent.Partial) {
        val side = _ui.value.activeSide
        _ui.update { it.copy(partial = e.text, partialSide = side ?: it.partialSide) }
        val now = SystemClock.uptimeMillis()
        if (now - lastPartialAt < 350) return
        lastPartialAt = now
        partialJob?.cancel()
        partialJob = viewModelScope.launch {
            val s = settings.value
            val r = resolveRoute(e.text, e.langHint, false, s, side)
            val tr = app.translator.translateQuick(e.text, r.source, r.target)
            if (_ui.value.running && _ui.value.partial.isNotEmpty()) {
                _ui.update { it.copy(partialTranslation = tr ?: "", partialLang = r.source, partialSide = r.side) }
            }
        }
    }

    private suspend fun resolveRoute(
        text: String,
        hint: String?,
        hintTrusted: Boolean,
        s: AppSettings,
        forcedSide: Side?,
    ): Route = when (mode) {
        LiveMode.LISTEN -> {
            val cands = if (s.sttEngine == SttEngine.ANDROID) currentConfig?.candidates?.map { it.code }.orEmpty()
            else emptyList()
            val src = app.detector.resolve(text, hint, hintTrusted, cands)
            Route(src, s.listenTarget, null)
        }

        LiveMode.CONVERSATION -> {
            val a = s.convLangA
            val b = s.convLangB
            val side = forcedSide ?: run {
                val src = app.detector.resolve(text, hint, hintTrusted, listOf(a, b))
                if (src == b) Side.B else Side.A
            }
            if (side == Side.A) Route(a, b, Side.A) else Route(b, a, Side.B)
        }
    }

    private suspend fun handleFinal(e: SpeechEvent.Final, side: Side?) {
        val text = e.text.trim()
        if (text.isEmpty()) return
        partialJob?.cancel()
        val s = settings.value
        val r = resolveRoute(text, e.langHint, e.hintTrusted, s, side)
        val id = nextId++
        val same = r.source != null && r.source == r.target
        val u = Utterance(
            id = id,
            side = r.side,
            source = text,
            sourceLang = r.source,
            targetLang = r.target,
            translation = if (same) text else "",
            state = if (same) UState.SAME_LANGUAGE else UState.TRANSLATING,
        )
        _ui.update {
            it.copy(
                items = it.items + u,
                partial = "",
                partialTranslation = "",
                partialSide = null,
                error = null,
                lastDetected = r.source ?: it.lastDetected,
            )
        }
        if (same) scheduleSave() else translations.trySend(id)
    }

    private suspend fun processTranslation(id: Long) {
        val u = _ui.value.items.firstOrNull { it.id == id } ?: return
        val s = settings.value
        val context = _ui.value.items
            .filter { it.id < id && it.state == UState.DONE && it.sourceLang == u.sourceLang }
            .takeLast(3)
            .map { it.source }
        try {
            val tr = app.translator.translate(s, u.source, u.sourceLang, u.targetLang, context)
            updateItem(id) { it.copy(translation = tr, state = UState.DONE, error = null) }
            val ttsOn = if (mode == LiveMode.LISTEN) s.ttsListen else s.ttsConversation
            if (ttsOn) Languages.byCode(u.targetLang)?.let { app.speaker.speak(tr, it, s.ttsRate) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateItem(id) { it.copy(state = UState.ERROR, error = e.message ?: "Lỗi dịch") }
        }
        scheduleSave()
    }

    private fun updateItem(id: Long, transform: (Utterance) -> Utterance) {
        _ui.update { st -> st.copy(items = st.items.map { if (it.id == id) transform(it) else it }) }
    }

    private fun scheduleSave(delayMs: Long = 1500) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(delayMs)
            saveNow()
        }
    }

    private suspend fun saveNow() {
        val items = _ui.value.items.filter { it.state == UState.DONE || it.state == UState.SAME_LANGUAGE }
        if (items.isEmpty()) return
        val s = settings.value
        val title = when (mode) {
            LiveMode.LISTEN -> {
                val src = items.mapNotNull { it.sourceLang }.distinct().joinToString(", ") { Languages.name(it) }
                "${src.ifBlank { "?" }} → ${Languages.name(s.listenTarget)}"
            }

            LiveMode.CONVERSATION -> "${Languages.name(s.convLangA)} ⇄ ${Languages.name(s.convLangB)}"
        }
        val record = SessionRecord(
            id = sessionId,
            mode = if (mode == LiveMode.LISTEN) "listen" else "conversation",
            startedAt = sessionStart,
            title = title,
            entries = items.map {
                HistoryEntry(
                    time = it.time,
                    side = when (it.side) { Side.A -> 1; Side.B -> 2; null -> 0 },
                    source = it.source,
                    sourceLang = it.sourceLang,
                    translation = it.translation,
                    targetLang = it.targetLang,
                )
            },
        )
        runCatching { app.history.save(record) }
    }

    override fun onCleared() {
        engine?.release()
        engine = null
        if (mode == LiveMode.CONVERSATION) app.speaker.stop()
        app.appScope.launch { saveNow() }
        super.onCleared()
    }

    companion object {
        fun factory(mode: LiveMode): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                LiveViewModel(app, mode)
            }
        }
    }
}
