package vn.dichtructiep.data

import java.util.Locale

/**
 * Một ngôn ngữ được ứng dụng hỗ trợ.
 *
 * @param code       mã ISO-639-1 (dùng cho ML Kit, Whisper, so sánh nội bộ)
 * @param speechTag  thẻ BCP-47 cho bộ nhận dạng giọng nói Android và TTS
 * @param whisperName tên tiếng Anh mà Whisper trả về trong trường "language"
 */
data class Language(
    val code: String,
    val speechTag: String,
    val nameVi: String,
    val nativeName: String,
    val flag: String,
    val whisperName: String,
) {
    val label: String get() = "$flag $nameVi"
    val locale: Locale get() = Locale.forLanguageTag(speechTag)
}

object Languages {

    val all: List<Language> = listOf(
        Language("vi", "vi-VN", "Tiếng Việt", "Tiếng Việt", "🇻🇳", "vietnamese"),
        Language("en", "en-US", "Tiếng Anh", "English", "🇺🇸", "english"),
        Language("zh", "zh-CN", "Tiếng Trung", "中文", "🇨🇳", "chinese"),
        Language("ja", "ja-JP", "Tiếng Nhật", "日本語", "🇯🇵", "japanese"),
        Language("ko", "ko-KR", "Tiếng Hàn", "한국어", "🇰🇷", "korean"),
        Language("fr", "fr-FR", "Tiếng Pháp", "Français", "🇫🇷", "french"),
        Language("de", "de-DE", "Tiếng Đức", "Deutsch", "🇩🇪", "german"),
        Language("es", "es-ES", "Tiếng Tây Ban Nha", "Español", "🇪🇸", "spanish"),
        Language("ru", "ru-RU", "Tiếng Nga", "Русский", "🇷🇺", "russian"),
        Language("th", "th-TH", "Tiếng Thái", "ไทย", "🇹🇭", "thai"),
        Language("id", "id-ID", "Tiếng Indonesia", "Bahasa Indonesia", "🇮🇩", "indonesian"),
        Language("ms", "ms-MY", "Tiếng Mã Lai", "Bahasa Melayu", "🇲🇾", "malay"),
        Language("tl", "fil-PH", "Tiếng Philippines", "Filipino", "🇵🇭", "tagalog"),
        Language("km", "km-KH", "Tiếng Khmer", "ភាសាខ្មែរ", "🇰🇭", "khmer"),
        Language("lo", "lo-LA", "Tiếng Lào", "ລາວ", "🇱🇦", "lao"),
        Language("my", "my-MM", "Tiếng Myanmar", "မြန်မာ", "🇲🇲", "myanmar"),
        Language("hi", "hi-IN", "Tiếng Hindi", "हिन्दी", "🇮🇳", "hindi"),
        Language("ar", "ar-SA", "Tiếng Ả Rập", "العربية", "🇸🇦", "arabic"),
        Language("pt", "pt-BR", "Tiếng Bồ Đào Nha", "Português", "🇧🇷", "portuguese"),
        Language("it", "it-IT", "Tiếng Ý", "Italiano", "🇮🇹", "italian"),
        Language("nl", "nl-NL", "Tiếng Hà Lan", "Nederlands", "🇳🇱", "dutch"),
        Language("tr", "tr-TR", "Tiếng Thổ Nhĩ Kỳ", "Türkçe", "🇹🇷", "turkish"),
        Language("pl", "pl-PL", "Tiếng Ba Lan", "Polski", "🇵🇱", "polish"),
        Language("uk", "uk-UA", "Tiếng Ukraina", "Українська", "🇺🇦", "ukrainian"),
        Language("sv", "sv-SE", "Tiếng Thụy Điển", "Svenska", "🇸🇪", "swedish"),
        Language("cs", "cs-CZ", "Tiếng Séc", "Čeština", "🇨🇿", "czech"),
        Language("el", "el-GR", "Tiếng Hy Lạp", "Ελληνικά", "🇬🇷", "greek"),
        Language("he", "he-IL", "Tiếng Do Thái", "עברית", "🇮🇱", "hebrew"),
        Language("bn", "bn-BD", "Tiếng Bengal", "বাংলা", "🇧🇩", "bengali"),
        Language("ur", "ur-PK", "Tiếng Urdu", "اردو", "🇵🇰", "urdu"),
        Language("fa", "fa-IR", "Tiếng Ba Tư", "فارسی", "🇮🇷", "persian"),
        Language("ro", "ro-RO", "Tiếng Romania", "Română", "🇷🇴", "romanian"),
        Language("hu", "hu-HU", "Tiếng Hungary", "Magyar", "🇭🇺", "hungarian"),
        Language("da", "da-DK", "Tiếng Đan Mạch", "Dansk", "🇩🇰", "danish"),
        Language("fi", "fi-FI", "Tiếng Phần Lan", "Suomi", "🇫🇮", "finnish"),
        Language("no", "nb-NO", "Tiếng Na Uy", "Norsk", "🇳🇴", "norwegian"),
    )

    private val byCodeMap: Map<String, Language> = all.associateBy { it.code }
    private val byWhisperMap: Map<String, Language> = all.associateBy { it.whisperName }

    val VI: Language = byCodeMap.getValue("vi")
    val EN: Language = byCodeMap.getValue("en")

    /** Chuẩn hóa mọi dạng mã ("en-US", "zh-Hant", "iw", "fil", "nb"...) về mã nội bộ. */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val base = raw.trim().lowercase(Locale.ROOT).replace('_', '-').substringBefore('-')
        return when (base) {
            "iw" -> "he"
            "in" -> "id"
            "fil" -> "tl"
            "nb", "nn" -> "no"
            "und", "" -> null
            else -> base
        }
    }

    fun byCode(raw: String?): Language? = normalize(raw)?.let { byCodeMap[it] }

    fun byCodeOr(raw: String?, fallback: Language): Language = byCode(raw) ?: fallback

    /** Whisper trả về tên tiếng Anh ("english"); một số dịch vụ trả mã ("en"). */
    fun fromWhisper(value: String?): Language? {
        if (value.isNullOrBlank()) return null
        val v = value.trim().lowercase(Locale.ROOT)
        return byWhisperMap[v] ?: when (v) {
            "mandarin", "cantonese" -> byCodeMap["zh"]
            "burmese" -> byCodeMap["my"]
            "filipino" -> byCodeMap["tl"]
            "nynorsk" -> byCodeMap["no"]
            else -> byCode(v)
        }
    }

    fun name(code: String?): String = byCode(code)?.nameVi ?: (code ?: "?")
    fun label(code: String?): String = byCode(code)?.label ?: (code ?: "?")
    fun flag(code: String?): String = byCode(code)?.flag ?: "🌐"
}
