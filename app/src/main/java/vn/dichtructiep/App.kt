package vn.dichtructiep

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.dichtructiep.data.AppSettings
import vn.dichtructiep.data.HistoryRepository
import vn.dichtructiep.data.SettingsRepository
import vn.dichtructiep.net.OpenAiClient
import vn.dichtructiep.speech.BeepMuter
import vn.dichtructiep.translate.LanguageDetector
import vn.dichtructiep.translate.ModelManager
import vn.dichtructiep.translate.TranslationService
import vn.dichtructiep.tts.Speaker

/** Nơi giữ các thành phần dùng chung toàn ứng dụng (DI thủ công). */
class App : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var settingsRepo: SettingsRepository
        private set
    lateinit var history: HistoryRepository
        private set
    lateinit var settings: StateFlow<AppSettings>
        private set

    val api: OpenAiClient by lazy { OpenAiClient() }
    val models: ModelManager by lazy { ModelManager() }
    val detector: LanguageDetector by lazy { LanguageDetector() }
    val translator: TranslationService by lazy { TranslationService(models, api) }
    val speaker: Speaker by lazy { Speaker(this) }

    /** Văn bản được chia sẻ/bôi đen từ ứng dụng khác, chờ mở màn hình Dịch văn bản. */
    val sharedText = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        BeepMuter.recoverIfNeeded(this)
        settingsRepo = SettingsRepository(this)
        history = HistoryRepository(this)
        settings = settingsRepo.flow.stateIn(appScope, SharingStarted.Eagerly, AppSettings())
        appScope.launch { history.load() }
        appScope.launch { models.refresh() }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        appScope.launch { settingsRepo.update(transform) }
    }
}

val Context.app: App get() = applicationContext as App
