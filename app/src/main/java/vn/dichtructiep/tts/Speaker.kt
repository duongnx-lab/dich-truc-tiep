package vn.dichtructiep.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.dichtructiep.data.Language
import java.util.Locale
import java.util.UUID

/** Đọc bản dịch thành tiếng (Text-to-Speech của hệ thống). */
class Speaker(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private val pending = ArrayList<() -> Unit>()
    private val callbacks = HashMap<String, () -> Unit>()
    private val activeIds = HashSet<String>()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            main.post { finish(utteranceId) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            main.post { finish(utteranceId) }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            main.post { finish(utteranceId) }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            main.post { finish(utteranceId) }
        }
    }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            main.post {
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    tts?.setOnUtteranceProgressListener(progress)
                    pending.forEach { it() }
                } else {
                    failed = true
                }
                pending.clear()
            }
        }
    }

    val isAvailable: Boolean get() = !failed

    fun isLanguageSupported(lang: Language): Boolean {
        val t = tts ?: return false
        if (!ready) return true
        val r = t.isLanguageAvailable(lang.locale)
        return r >= TextToSpeech.LANG_AVAILABLE
    }

    fun speak(text: String, lang: Language, rate: Float = 1f, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke(); return
        }
        val action = action@{
            val t = tts
            if (t == null) {
                onDone?.invoke(); return@action
            }
            var res = t.setLanguage(lang.locale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                res = t.setLanguage(Locale(lang.code))
            }
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                onDone?.invoke(); return@action
            }
            t.setSpeechRate(rate.coerceIn(0.5f, 2f))
            val id = UUID.randomUUID().toString()
            onDone?.let { callbacks[id] = it }
            activeIds.add(id)
            _speaking.value = true
            val r = t.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), id)
            if (r != TextToSpeech.SUCCESS) finish(id)
        }
        main.post {
            when {
                ready -> action()
                failed -> onDone?.invoke()
                else -> pending.add(action)
            }
        }
    }

    fun stop() {
        main.post {
            runCatching { tts?.stop() }
            val cbs = callbacks.values.toList()
            callbacks.clear()
            activeIds.clear()
            _speaking.value = false
            cbs.forEach { it() }
        }
    }

    fun shutdown() {
        runCatching { tts?.shutdown() }
        tts = null
    }

    private fun finish(id: String?) {
        if (id == null) return
        activeIds.remove(id)
        callbacks.remove(id)?.invoke()
        if (activeIds.isEmpty()) _speaking.value = false
    }

}
