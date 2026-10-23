package vn.dichtructiep.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import vn.dichtructiep.data.Languages

class TranslationException(message: String) : Exception(message)

/** Quản lý các gói ngôn ngữ offline của ML Kit (tải / xóa / trạng thái). */
class ModelManager {

    private val rmm = RemoteModelManager.getInstance()
    private val mutex = Mutex()

    private val _downloaded = MutableStateFlow<Set<String>>(emptySet())
    val downloaded: StateFlow<Set<String>> = _downloaded.asStateFlow()

    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    private var refreshedOnce = false

    fun isSupported(code: String?): Boolean =
        code != null && TranslateLanguage.fromLanguageTag(code) != null

    fun isReady(code: String): Boolean = code in _downloaded.value

    suspend fun refresh() {
        try {
            val models = rmm.getDownloadedModels(TranslateRemoteModel::class.java).await()
            _downloaded.value = models.mapNotNull { Languages.normalize(it.language) }.toSet()
            refreshedOnce = true
        } catch (_: Exception) {
        }
    }

    suspend fun download(code: String, wifiOnly: Boolean) {
        val tag = TranslateLanguage.fromLanguageTag(code)
            ?: throw TranslationException("ML Kit chưa hỗ trợ ${Languages.name(code)}")
        _downloading.update { it + code }
        try {
            val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
            rmm.download(TranslateRemoteModel.Builder(tag).build(), conditions).await()
        } catch (e: Exception) {
            throw TranslationException(
                "Không tải được gói ${Languages.name(code)}" +
                    (if (wifiOnly) " (đang đặt chỉ tải qua Wi-Fi)" else "") + ": ${e.message ?: e.javaClass.simpleName}"
            )
        } finally {
            _downloading.update { it - code }
            refresh()
        }
    }

    suspend fun delete(code: String) {
        val tag = TranslateLanguage.fromLanguageTag(code) ?: return
        runCatching { rmm.deleteDownloadedModel(TranslateRemoteModel.Builder(tag).build()).await() }
        refresh()
    }

    /** Đảm bảo các gói cần thiết đã có; tự tải nếu được phép. */
    suspend fun ensure(codes: Collection<String>, allowDownload: Boolean, wifiOnly: Boolean) {
        if (!refreshedOnce) refresh()
        val missing = codes.distinct().filter { it !in _downloaded.value }
        if (missing.isEmpty()) return
        if (!allowDownload) {
            throw TranslationException(
                "Chưa tải gói offline: " + missing.joinToString { Languages.name(it) } +
                    ". Vào Cài đặt › Gói ngôn ngữ offline để tải."
            )
        }
        mutex.withLock {
            for (code in missing) {
                if (code !in _downloaded.value) download(code, wifiOnly)
            }
        }
    }
}
