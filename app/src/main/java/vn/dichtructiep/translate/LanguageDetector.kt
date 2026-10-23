package vn.dichtructiep.translate

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.tasks.await
import vn.dichtructiep.data.Languages

/**
 * Xác định ngôn ngữ của một câu bằng 3 nguồn bằng chứng:
 *  1. Gợi ý từ bộ nhận dạng giọng nói (Whisper / Android 14+).
 *  2. Bộ chữ viết đặc trưng (dấu tiếng Việt, Hangul, Kana, chữ Thái...).
 *  3. ML Kit Language Identification (offline).
 */
class LanguageDetector {

    private val client = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.05f).build()
    )

    /** Danh sách (mã ngôn ngữ, độ tin cậy) theo ML Kit, đã chuẩn hóa mã. */
    suspend fun identify(text: String): List<Pair<String, Float>> = try {
        client.identifyPossibleLanguages(text).await()
            .mapNotNull { l -> Languages.normalize(l.languageTag)?.let { it to l.confidence } }
            .groupBy({ it.first }, { it.second })
            .map { (code, confs) -> code to confs.max() }
            .sortedByDescending { it.second }
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * @param hint        ngôn ngữ do bộ nhận dạng báo (có thể null)
     * @param hintTrusted gợi ý có đáng tin không (Whisper: có)
     * @param candidates  tập ngôn ngữ cho phép; rỗng = bất kỳ
     */
    suspend fun resolve(
        text: String,
        hint: String?,
        hintTrusted: Boolean,
        candidates: List<String>,
    ): String? {
        val h = Languages.normalize(hint)
        val script = scriptGuess(text)
        val ml = identify(text)

        if (candidates.isEmpty()) {
            if (h != null && hintTrusted) return h
            if (script != null) return script
            val top = ml.firstOrNull()
            if (top != null && top.second >= 0.5f) return top.first
            return h ?: top?.first
        }

        // Tập đóng (ví dụ hội thoại Việt ↔ Anh)
        if (script != null && script in candidates) return script
        if (h != null && h in candidates && (hintTrusted || script == null)) return h
        val inSet = ml.filter { it.first in candidates }
        if (inSet.isNotEmpty() && inSet.first().second >= 0.2f) return inSet.first().first
        // Văn bản chữ Latin không dấu tiếng Việt → nhiều khả năng là ngôn ngữ Latin còn lại
        if (script == null && isMostlyLatin(text)) {
            val latinNonVi = candidates.firstOrNull { it != "vi" && it in LATIN_LANGS }
            if (latinNonVi != null) return latinNonVi
        }
        return inSet.firstOrNull()?.first ?: h?.takeIf { it in candidates } ?: candidates.first()
    }

    companion object {
        private val LATIN_LANGS = setOf(
            "en", "fr", "de", "es", "pt", "it", "nl", "tr", "pl", "sv", "cs", "ro", "hu",
            "da", "fi", "no", "id", "ms", "tl", "vi"
        )
        private const val VI_SPECIAL = "ăâđêôơưĂÂĐÊÔƠƯ"

        /** Đoán ngôn ngữ dựa trên hệ chữ viết rất đặc trưng; null nếu không chắc. */
        fun scriptGuess(text: String): String? {
            var vi = 0; var hangul = 0; var kana = 0; var han = 0; var thai = 0; var cyr = 0
            var arabic = 0; var deva = 0; var khmer = 0; var lao = 0; var myan = 0; var hebrew = 0
            var greek = 0; var bengali = 0; var letters = 0
            for (ch in text) {
                if (!ch.isLetter()) continue
                letters++
                val c = ch.code
                when {
                    ch in VI_SPECIAL || c in 0x1EA0..0x1EF9 -> vi++
                    c in 0xAC00..0xD7AF || c in 0x1100..0x11FF || c in 0x3130..0x318F -> hangul++
                    c in 0x3040..0x30FF -> kana++
                    c in 0x4E00..0x9FFF || c in 0x3400..0x4DBF -> han++
                    c in 0x0E00..0x0E7F -> thai++
                    c in 0x0400..0x04FF -> cyr++
                    c in 0x0600..0x06FF || c in 0x0750..0x077F -> arabic++
                    c in 0x0900..0x097F -> deva++
                    c in 0x1780..0x17FF -> khmer++
                    c in 0x0E80..0x0EFF -> lao++
                    c in 0x1000..0x109F -> myan++
                    c in 0x0590..0x05FF -> hebrew++
                    c in 0x0370..0x03FF -> greek++
                    c in 0x0980..0x09FF -> bengali++
                }
            }
            if (letters == 0) return null
            return when {
                kana > 0 -> "ja"
                hangul > 0 -> "ko"
                han * 3 >= letters -> "zh"
                thai > 0 -> "th"
                khmer > 0 -> "km"
                lao > 0 -> "lo"
                myan > 0 -> "my"
                deva > 0 -> "hi"
                bengali > 0 -> "bn"
                hebrew > 0 -> "he"
                greek * 2 >= letters -> "el"
                // Ả Rập / Ba Tư / Urdu và Nga / Ukraina dùng chung bộ chữ → để ML Kit phân biệt
                vi >= 1 && vi * 12 >= letters -> "vi"
                else -> null
            }
        }

        fun isMostlyLatin(text: String): Boolean {
            var latin = 0; var letters = 0
            for (ch in text) {
                if (!ch.isLetter()) continue
                letters++
                if (ch.code < 0x0250 || ch.code in 0x1E00..0x1EFF) latin++
            }
            return letters > 0 && latin * 10 >= letters * 9
        }
    }
}
