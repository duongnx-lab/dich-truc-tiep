package vn.dichtructiep.translate

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import vn.dichtructiep.data.AppSettings
import vn.dichtructiep.data.Languages
import vn.dichtructiep.data.TranslateEngine
import vn.dichtructiep.net.OpenAiClient
import java.io.IOException

/**
 * Điểm vào duy nhất cho việc dịch. Tự chọn ML Kit hoặc AI đám mây theo cài đặt,
 * và tự chuyển sang phương án còn lại khi phương án chính không dùng được.
 */
class TranslationService(
    private val models: ModelManager,
    private val api: OpenAiClient,
) {
    private val clients = LinkedHashMap<String, Translator>()

    /** Dịch câu hoàn chỉnh. context = vài câu gốc trước đó (giúp AI dịch mạch lạc hơn). */
    suspend fun translate(
        s: AppSettings,
        text: String,
        source: String?,
        target: String,
        context: List<String> = emptyList(),
    ): String {
        if (text.isBlank()) return ""
        if (source != null && source == target) return text

        val mlkitPossible = source != null && models.isSupported(source) && models.isSupported(target)
        val preferCloud = s.translateEngine == TranslateEngine.CLOUD && s.hasCloud

        if (preferCloud || (!mlkitPossible && s.hasCloud)) {
            try {
                return translateCloud(s, text, source, target, context)
            } catch (e: IOException) {
                // Mất mạng → thử dịch offline nếu có thể
                if (mlkitPossible && models.isReady(source!!) && models.isReady(target)) {
                    return translateMlKit(s, text, source, target)
                }
                throw TranslationException("Lỗi mạng khi dịch: ${e.message ?: "không kết nối được"}")
            }
        }
        if (source == null) throw TranslationException("Không xác định được ngôn ngữ nguồn")
        if (!mlkitPossible) {
            throw TranslationException(
                "Dịch offline chưa hỗ trợ ${Languages.name(source)} → ${Languages.name(target)}. " +
                    "Hãy nhập API key để dùng dịch AI."
            )
        }
        return translateMlKit(s, text, source, target)
    }

    /** Dịch nhanh cho phụ đề tạm thời: chỉ dùng ML Kit khi gói đã sẵn sàng; null nếu không thể. */
    suspend fun translateQuick(text: String, source: String?, target: String): String? {
        if (source == null || text.isBlank()) return null
        if (source == target) return text
        if (!models.isSupported(source) || !models.isSupported(target)) return null
        if (!models.isReady(source) || !models.isReady(target)) return null
        return try {
            client(source, target).translate(text).await()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun translateMlKit(s: AppSettings, text: String, source: String, target: String): String {
        models.ensure(listOf(source, target), s.autoDownloadModels, s.wifiOnlyDownload)
        return try {
            client(source, target).translate(text).await()
        } catch (e: Exception) {
            throw TranslationException("ML Kit lỗi: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun translateCloud(
        s: AppSettings,
        text: String,
        source: String?,
        target: String,
        context: List<String>,
    ): String {
        val targetName = Languages.byCode(target)?.let { "${it.nativeName} (${it.whisperName})" } ?: target
        val sourceName = source?.let { Languages.byCode(it)?.whisperName } ?: "auto-detected language"
        val system = """
            You are a professional simultaneous interpreter.
            Translate the user's text from $sourceName into $targetName.
            The text comes from live speech recognition: silently fix obvious recognition errors,
            keep names, numbers and tone, and make it natural and concise for subtitles.
            Output ONLY the translation, with no quotes, notes or explanations.
        """.trimIndent()
        val user = if (context.isNotEmpty()) {
            "Previous sentences (context only, do NOT translate):\n" +
                context.joinToString("\n") { "- $it" } +
                "\n\nText to translate:\n$text"
        } else text
        return try {
            api.chat(s.cloud, system, user)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw TranslationException("Dịch AI lỗi: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    @Synchronized
    private fun client(source: String, target: String): Translator {
        val key = "$source>$target"
        clients[key]?.let { return it }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.fromLanguageTag(source)!!)
            .setTargetLanguage(TranslateLanguage.fromLanguageTag(target)!!)
            .build()
        val c = Translation.getClient(options)
        clients[key] = c
        // Giữ tối đa 6 cặp ngôn ngữ trong bộ nhớ
        if (clients.size > 6) {
            val oldest = clients.keys.first()
            clients.remove(oldest)?.close()
        }
        return c
    }
}
