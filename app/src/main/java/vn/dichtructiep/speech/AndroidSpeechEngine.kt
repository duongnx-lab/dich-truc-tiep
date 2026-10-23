package vn.dichtructiep.speech

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import vn.dichtructiep.data.Languages

/**
 * Nhận dạng liên tục bằng SpeechRecognizer của Android (thường là Google).
 *
 * SpeechRecognizer chỉ nghe từng câu, nên engine tự khởi động lại sau mỗi câu/lỗi
 * để tạo cảm giác "nghe liên tục". Để tự nhận diện ngôn ngữ, engine khai báo ngôn ngữ
 * chính + các ngôn ngữ bổ sung (EXTRA_ADDITIONAL_LANGUAGES) và, trên Android 14+,
 * bật Language Detection / Language Switch của hệ thống.
 */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {

    private val main = Handler(Looper.getMainLooper())
    private val channel = Channel<SpeechEvent>(Channel.UNLIMITED)
    override val events: Flow<SpeechEvent> = channel.receiveAsFlow()

    private var recognizer: SpeechRecognizer? = null
    private var usingOnDevice = false
    private var wantOnDevice = false
    private var config: ListenConfig? = null
    private var active = false
    private var paused = false
    private var lastPartial = ""
    private var detectedLang: String? = null
    private var errorStreak = 0
    private var lastLevelAt = 0L
    private val beep = BeepMuter(context)

    private val restartRunnable = Runnable { listenNow() }

    override fun start(config: ListenConfig) = onMain {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            emit(
                SpeechEvent.Error(
                    "Máy chưa có dịch vụ nhận dạng giọng nói. Hãy cài/cập nhật ứng dụng Google " +
                        "hoặc chuyển sang Whisper trong Cài đặt.", true
                )
            )
            return@onMain
        }
        this.config = config
        active = true
        paused = false
        errorStreak = 0
        wantOnDevice = config.preferOnDevice
        if (config.muteBeep) beep.mute()
        createRecognizer()
        listenNow()
    }

    override fun stop() = onMain {
        active = false
        paused = false
        main.removeCallbacks(restartRunnable)
        runCatching { recognizer?.cancel() }
        destroyRecognizer()
        beep.restore()
        emit(SpeechEvent.Level(0f))
    }

    override fun pause() = onMain {
        if (!active || paused) return@onMain
        paused = true
        main.removeCallbacks(restartRunnable)
        runCatching { recognizer?.cancel() }
        emit(SpeechEvent.Level(0f))
        emit(SpeechEvent.Status("Tạm ngắt micro khi đang đọc…"))
    }

    override fun resume() = onMain {
        if (!active || !paused) return@onMain
        paused = false
        scheduleRestart(250)
    }

    override fun release() {
        stop()
    }

    // ------------------------------------------------------------------

    private fun createRecognizer() {
        destroyRecognizer()
        val r = if (wantOnDevice && Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            usingOnDevice = true
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            usingOnDevice = false
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        r.setRecognitionListener(listener)
        recognizer = r
    }

    private fun destroyRecognizer() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun listenNow() {
        main.removeCallbacks(restartRunnable)
        if (!active || paused) return
        val cfg = config ?: return
        if (recognizer == null) createRecognizer()
        lastPartial = ""
        detectedLang = null
        try {
            recognizer?.startListening(buildIntent(cfg))
            emit(SpeechEvent.Status("Đang nghe…"))
        } catch (e: Exception) {
            errorStreak++
            scheduleRestart(600, recreate = true)
        }
    }

    private fun scheduleRestart(delayMs: Long, recreate: Boolean = false) {
        main.removeCallbacks(restartRunnable)
        if (!active) return
        if (recreate) createRecognizer()
        main.postDelayed(restartRunnable, delayMs)
    }

    private fun buildIntent(cfg: ListenConfig): Intent {
        val primary = cfg.forced ?: cfg.candidates.firstOrNull() ?: Languages.EN
        val others = if (cfg.forced != null) emptyList()
        else cfg.candidates.filter { it.code != primary.code }.take(3)

        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, primary.speechTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primary.speechTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (cfg.preferOnDevice) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

            if (others.isNotEmpty()) {
                val all = ArrayList<String>().apply {
                    add(primary.speechTag)
                    others.forEach { add(it.speechTag) }
                }
                // Dịch vụ Google hỗ trợ nhận dạng đa ngôn ngữ qua extra (không công khai) này
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", others.map { it.speechTag }.toTypedArray())
                if (Build.VERSION.SDK_INT >= 34) {
                    putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
                    putStringArrayListExtra("android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES", all)
                    putExtra("android.speech.extra.ENABLE_LANGUAGE_SWITCH", "balanced")
                    putStringArrayListExtra("android.speech.extra.LANGUAGE_SWITCH_ALLOWED_LANGUAGES", all)
                }
            }
        }
    }

    private fun finalizeText(text: String) {
        val cfg = config
        val forced = cfg?.forced
        emit(
            SpeechEvent.Final(
                text = text,
                langHint = forced?.code ?: detectedLang,
                hintTrusted = forced != null,
            )
        )
    }

    private fun afterUtterance() {
        if (config?.singleUtterance == true) {
            active = false
            main.removeCallbacks(restartRunnable)
            destroyRecognizer()
            beep.restore()
            emit(SpeechEvent.Level(0f))
            emit(SpeechEvent.Status(""))
        } else {
            scheduleRestart(80)
        }
    }

    private fun fatal(message: String) {
        active = false
        main.removeCallbacks(restartRunnable)
        destroyRecognizer()
        beep.restore()
        emit(SpeechEvent.Level(0f))
        emit(SpeechEvent.Error(message, true))
    }

    private fun handleError(code: Int) {
        if (!active || paused) return
        when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                if (lastPartial.isNotBlank()) {
                    finalizeText(lastPartial)
                    afterUtterance()
                } else if (config?.singleUtterance == true) {
                    fatal("Không nghe thấy gì. Hãy bấm micro và nói lại.")
                } else {
                    scheduleRestart(100)
                }
                return
            }

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                errorStreak++
                scheduleRestart(400, recreate = true)
            }

            SpeechRecognizer.ERROR_CLIENT -> {
                errorStreak++
                scheduleRestart(300, recreate = true)
            }

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                fatal("Ứng dụng chưa được cấp quyền micro.")
                return
            }

            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER, 10 /* TOO_MANY_REQUESTS */, 11 /* SERVER_DISCONNECTED */ -> {
                errorStreak++
                emit(SpeechEvent.Error("Lỗi mạng khi nhận dạng – đang thử lại…", false))
                val backoff = (500L shl errorStreak.coerceAtMost(4)).coerceAtMost(8000L)
                scheduleRestart(backoff, recreate = code == 11)
            }

            12 /* LANGUAGE_NOT_SUPPORTED */, 13 /* LANGUAGE_UNAVAILABLE */ -> {
                if (usingOnDevice) {
                    wantOnDevice = false
                    emit(
                        SpeechEvent.Error(
                            "Chưa có gói nhận dạng offline cho ngôn ngữ này – chuyển sang nhận dạng trực tuyến.",
                            false
                        )
                    )
                    scheduleRestart(200, recreate = true)
                } else {
                    val name = (config?.forced ?: config?.candidates?.firstOrNull())?.nameVi ?: "ngôn ngữ này"
                    fatal("Bộ nhận dạng của máy không hỗ trợ $name. Hãy dùng Whisper (Cài đặt › Nhận dạng).")
                }
                return
            }

            else -> {
                errorStreak++
                scheduleRestart(500, recreate = errorStreak % 3 == 0)
            }
        }
        if (errorStreak > 15) {
            fatal("Bộ nhận dạng giọng nói gặp lỗi liên tục (mã $code). Hãy thử lại hoặc chuyển sang Whisper.")
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            val now = SystemClock.uptimeMillis()
            if (now - lastLevelAt < 60) return
            lastLevelAt = now
            emit(SpeechEvent.Level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)))
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            emit(SpeechEvent.Level(0f))
        }

        override fun onError(error: Int) {
            handleError(error)
        }

        override fun onResults(results: Bundle?) {
            if (!active) return
            errorStreak = 0
            val text = best(results).ifBlank { lastPartial }
            if (text.isNotBlank()) finalizeText(text)
            afterUtterance()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!active) return
            val text = best(partialResults)
            if (text.isNotBlank()) {
                errorStreak = 0
                lastPartial = text
                emit(SpeechEvent.Partial(text, config?.forced?.code ?: detectedLang))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}

        // Android 14+: hệ thống báo ngôn ngữ phát hiện được
        override fun onLanguageDetection(results: Bundle) {
            val lang = results.getString("detected_language")
            if (!lang.isNullOrBlank()) detectedLang = Languages.normalize(lang)
        }
    }

    private fun best(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()

    private fun emit(e: SpeechEvent) {
        channel.trySend(e)
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }
}

/**
 * Tùy chọn: tắt tiếng hệ thống/thông báo trong lúc nghe để giảm tiếng "bíp" mỗi lần
 * bộ nhận dạng khởi động lại. Luôn khôi phục lại khi dừng (kể cả nếu app bị đóng đột ngột,
 * lần mở sau sẽ tự khôi phục).
 */
class BeepMuter(context: Context) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = context.getSharedPreferences("beep_muter", Context.MODE_PRIVATE)
    private var mutedStreams = emptyList<Int>()

    fun mute() {
        if (mutedStreams.isNotEmpty()) return
        val done = ArrayList<Int>()
        for (s in STREAMS) {
            try {
                am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0)
                done.add(s)
            } catch (_: Exception) {
            }
        }
        mutedStreams = done
        if (done.isNotEmpty()) prefs.edit().putBoolean(KEY, true).apply()
    }

    fun restore() {
        if (mutedStreams.isEmpty()) return
        unmute(am, mutedStreams)
        mutedStreams = emptyList()
        prefs.edit().putBoolean(KEY, false).apply()
    }

    companion object {
        private const val KEY = "muted"
        private val STREAMS = listOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)

        private fun unmute(am: AudioManager, streams: List<Int>) {
            for (s in streams) {
                try {
                    am.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0)
                } catch (_: Exception) {
                }
            }
        }

        /** Gọi khi khởi động app: nếu lần trước app bị đóng khi đang tắt tiếng thì bật lại. */
        fun recoverIfNeeded(context: Context) {
            val prefs = context.getSharedPreferences("beep_muter", Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY, false)) {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                unmute(am, STREAMS)
                prefs.edit().putBoolean(KEY, false).apply()
            }
        }
    }
}
