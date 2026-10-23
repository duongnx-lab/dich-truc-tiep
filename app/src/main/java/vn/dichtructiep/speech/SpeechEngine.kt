package vn.dichtructiep.speech

import kotlinx.coroutines.flow.Flow
import vn.dichtructiep.data.Language

/** Sự kiện phát ra từ bộ nhận dạng giọng nói. */
sealed interface SpeechEvent {
    /** Văn bản tạm thời (đang nói). */
    data class Partial(val text: String, val langHint: String?) : SpeechEvent

    /** Câu hoàn chỉnh. hintTrusted = gợi ý ngôn ngữ có đáng tin không. */
    data class Final(val text: String, val langHint: String?, val hintTrusted: Boolean) : SpeechEvent

    /** Mức âm lượng đầu vào 0..1 (vẽ sóng âm). */
    data class Level(val value: Float) : SpeechEvent

    /** Trạng thái hiển thị cho người dùng ("Đang nghe…", "Đang xử lý…"). */
    data class Status(val text: String) : SpeechEvent

    /** Lỗi; fatal = phiên nghe đã dừng hẳn. */
    data class Error(val message: String, val fatal: Boolean) : SpeechEvent
}

data class ListenConfig(
    /** Các ngôn ngữ có thể xuất hiện; rỗng = bất kỳ (chỉ Whisper hỗ trợ đầy đủ). */
    val candidates: List<Language>,
    /** Ép một ngôn ngữ cụ thể (chế độ bấm-để-nói trong hội thoại). */
    val forced: Language? = null,
    /** Dừng sau câu đầu tiên. */
    val singleUtterance: Boolean = false,
    val preferOnDevice: Boolean = false,
    val muteBeep: Boolean = false,
    val vadSensitivity: Float = 0.5f,
    val cloudPartials: Boolean = true,
    /** Khoảng lặng kết thúc câu (ms) – Whisper. */
    val endSilenceMs: Int = 700,
    /** Độ dài tối đa một đoạn (ms) – Whisper. */
    val maxSegmentMs: Int = 14_000,
)

interface SpeechEngine {
    val events: Flow<SpeechEvent>
    fun start(config: ListenConfig)
    fun stop()
    /** Tạm ngắt micro (ví dụ khi đang đọc bản dịch để tránh tự nghe lại). */
    fun pause()
    fun resume()
    fun release()
}
