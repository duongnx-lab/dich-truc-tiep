package vn.dichtructiep.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Bộ máy nhận dạng giọng nói. */
enum class SttEngine(val label: String, val desc: String) {
    ANDROID(
        "Android (Google)",
        "Miễn phí, nhanh, có phụ đề tức thời. Cần khai báo trước các ngôn ngữ dự kiến (tối đa 4)."
    ),
    CLOUD(
        "Whisper (API đám mây)",
        "Tự nhận diện gần 100 ngôn ngữ, chính xác cao. Cần API key (Groq miễn phí / OpenAI)."
    )
}

/** Bộ máy dịch. */
enum class TranslateEngine(val label: String, val desc: String) {
    MLKIT("ML Kit (offline)", "Dịch ngay trên máy, không cần mạng sau khi tải gói ngôn ngữ (~30 MB/ngôn ngữ)."),
    CLOUD("AI (API đám mây)", "Dịch bằng mô hình ngôn ngữ lớn, hiểu ngữ cảnh, tự sửa lỗi nhận dạng. Cần API key.")
}

enum class ApiProvider(
    val label: String,
    val baseUrl: String,
    val sttModel: String,
    val chatModel: String,
    val keyUrl: String,
) {
    GROQ(
        "Groq (có gói miễn phí)",
        "https://api.groq.com/openai/v1",
        "whisper-large-v3-turbo",
        "llama-3.3-70b-versatile",
        "https://console.groq.com/keys"
    ),
    OPENAI(
        "OpenAI",
        "https://api.openai.com/v1",
        "whisper-1",
        "gpt-4o-mini",
        "https://platform.openai.com/api-keys"
    ),
    CUSTOM("Tùy chỉnh (tương thích OpenAI)", "", "", "", "")
}

data class AppSettings(
    // Nhận dạng
    val sttEngine: SttEngine = SttEngine.ANDROID,
    val preferOnDevice: Boolean = false,
    val muteBeep: Boolean = false,
    val vadSensitivity: Float = 0.5f,
    val cloudPartials: Boolean = true,
    // Dịch
    val translateEngine: TranslateEngine = TranslateEngine.MLKIT,
    val autoDownloadModels: Boolean = true,
    val wifiOnlyDownload: Boolean = false,
    // API
    val provider: ApiProvider = ApiProvider.GROQ,
    val baseUrl: String = ApiProvider.GROQ.baseUrl,
    val apiKey: String = "",
    val sttModel: String = ApiProvider.GROQ.sttModel,
    val chatModel: String = ApiProvider.GROQ.chatModel,
    // Chế độ Nghe & dịch
    val listenTarget: String = "vi",
    val listenExpected: List<String> = listOf("en"),
    val ttsListen: Boolean = false,
    // Chế độ hội thoại
    val convLangA: String = "vi",
    val convLangB: String = "en",
    val faceToFace: Boolean = true,
    val ttsConversation: Boolean = true,
    val pauseWhileSpeaking: Boolean = true,
    // Giọng đọc & hiển thị
    val ttsRate: Float = 1.0f,
    val fontScale: Float = 1.0f,
    val showSource: Boolean = true,
    val keepScreenOn: Boolean = true,
    // Dịch văn bản
    val textTarget: String = "vi",
) {
    val hasCloud: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank()

    val cloud: CloudConfig get() = CloudConfig(baseUrl.trim().trimEnd('/'), apiKey.trim(), sttModel.trim(), chatModel.trim())
}

data class CloudConfig(
    val baseUrl: String,
    val apiKey: String,
    val sttModel: String,
    val chatModel: String,
)

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object K {
        val stt = stringPreferencesKey("stt")
        val preferOnDevice = booleanPreferencesKey("prefer_on_device")
        val muteBeep = booleanPreferencesKey("mute_beep")
        val vad = floatPreferencesKey("vad")
        val cloudPartials = booleanPreferencesKey("cloud_partials")
        val tr = stringPreferencesKey("translate")
        val autoDl = booleanPreferencesKey("auto_dl")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val provider = stringPreferencesKey("provider")
        val baseUrl = stringPreferencesKey("base_url")
        val apiKey = stringPreferencesKey("api_key")
        val sttModel = stringPreferencesKey("stt_model")
        val chatModel = stringPreferencesKey("chat_model")
        val listenTarget = stringPreferencesKey("listen_target")
        val listenExpected = stringPreferencesKey("listen_expected")
        val ttsListen = booleanPreferencesKey("tts_listen")
        val convA = stringPreferencesKey("conv_a")
        val convB = stringPreferencesKey("conv_b")
        val faceToFace = booleanPreferencesKey("face_to_face")
        val ttsConv = booleanPreferencesKey("tts_conv")
        val pauseSpeaking = booleanPreferencesKey("pause_speaking")
        val ttsRate = floatPreferencesKey("tts_rate")
        val fontScale = floatPreferencesKey("font_scale")
        val showSource = booleanPreferencesKey("show_source")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val textTarget = stringPreferencesKey("text_target")
    }

    val flow: Flow<AppSettings> = context.settingsStore.data
        .catch { emit(emptyPreferences()) }
        .map { read(it) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val updated = transform(read(prefs))
            write(prefs, updated)
        }
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            sttEngine = enumOr(p[K.stt], d.sttEngine),
            preferOnDevice = p[K.preferOnDevice] ?: d.preferOnDevice,
            muteBeep = p[K.muteBeep] ?: d.muteBeep,
            vadSensitivity = p[K.vad] ?: d.vadSensitivity,
            cloudPartials = p[K.cloudPartials] ?: d.cloudPartials,
            translateEngine = enumOr(p[K.tr], d.translateEngine),
            autoDownloadModels = p[K.autoDl] ?: d.autoDownloadModels,
            wifiOnlyDownload = p[K.wifiOnly] ?: d.wifiOnlyDownload,
            provider = enumOr(p[K.provider], d.provider),
            baseUrl = p[K.baseUrl] ?: d.baseUrl,
            apiKey = p[K.apiKey] ?: d.apiKey,
            sttModel = p[K.sttModel] ?: d.sttModel,
            chatModel = p[K.chatModel] ?: d.chatModel,
            listenTarget = p[K.listenTarget] ?: d.listenTarget,
            listenExpected = p[K.listenExpected]?.split(',')?.filter { it.isNotBlank() } ?: d.listenExpected,
            ttsListen = p[K.ttsListen] ?: d.ttsListen,
            convLangA = p[K.convA] ?: d.convLangA,
            convLangB = p[K.convB] ?: d.convLangB,
            faceToFace = p[K.faceToFace] ?: d.faceToFace,
            ttsConversation = p[K.ttsConv] ?: d.ttsConversation,
            pauseWhileSpeaking = p[K.pauseSpeaking] ?: d.pauseWhileSpeaking,
            ttsRate = p[K.ttsRate] ?: d.ttsRate,
            fontScale = p[K.fontScale] ?: d.fontScale,
            showSource = p[K.showSource] ?: d.showSource,
            keepScreenOn = p[K.keepScreenOn] ?: d.keepScreenOn,
            textTarget = p[K.textTarget] ?: d.textTarget,
        )
    }

    private fun write(p: MutablePreferences, s: AppSettings) {
        p[K.stt] = s.sttEngine.name
        p[K.preferOnDevice] = s.preferOnDevice
        p[K.muteBeep] = s.muteBeep
        p[K.vad] = s.vadSensitivity
        p[K.cloudPartials] = s.cloudPartials
        p[K.tr] = s.translateEngine.name
        p[K.autoDl] = s.autoDownloadModels
        p[K.wifiOnly] = s.wifiOnlyDownload
        p[K.provider] = s.provider.name
        p[K.baseUrl] = s.baseUrl
        p[K.apiKey] = s.apiKey
        p[K.sttModel] = s.sttModel
        p[K.chatModel] = s.chatModel
        p[K.listenTarget] = s.listenTarget
        p[K.listenExpected] = s.listenExpected.joinToString(",")
        p[K.ttsListen] = s.ttsListen
        p[K.convA] = s.convLangA
        p[K.convB] = s.convLangB
        p[K.faceToFace] = s.faceToFace
        p[K.ttsConv] = s.ttsConversation
        p[K.pauseSpeaking] = s.pauseWhileSpeaking
        p[K.ttsRate] = s.ttsRate
        p[K.fontScale] = s.fontScale
        p[K.showSource] = s.showSource
        p[K.keepScreenOn] = s.keepScreenOn
        p[K.textTarget] = s.textTarget
    }

    private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
        value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default
}
