package vn.dichtructiep.speech

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.dichtructiep.data.CloudConfig
import vn.dichtructiep.data.Languages
import vn.dichtructiep.net.ApiException
import vn.dichtructiep.net.OpenAiClient
import vn.dichtructiep.translate.LanguageDetector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Nhận dạng bằng Whisper (API tương thích OpenAI: Groq, OpenAI...).
 *
 * Micro được đọc liên tục ở 16 kHz; bộ phát hiện giọng nói (VAD) theo năng lượng
 * với ngưỡng nhiễu thích nghi sẽ cắt âm thanh thành từng câu tại các khoảng lặng.
 * Mỗi câu được gửi lên Whisper – Whisper tự nhận diện ngôn ngữ (gần 100 ngôn ngữ).
 * Trong lúc người nói chưa dứt câu, engine gửi định kỳ phần âm thanh đã có để hiện
 * phụ đề tạm thời (có thể tắt để tiết kiệm API).
 */
class CloudSpeechEngine(
    private val context: Context,
    private val api: OpenAiClient,
    private val detector: LanguageDetector,
    private val cloudConfig: () -> CloudConfig,
) : SpeechEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val channel = Channel<SpeechEvent>(Channel.UNLIMITED)
    override val events: Flow<SpeechEvent> = channel.receiveAsFlow()

    private var recordJob: Job? = null
    private var workerJob: Job? = null

    @Volatile private var running = false
    @Volatile private var paused = false
    private val generation = AtomicLong(0)
    private val partialInFlight = AtomicBoolean(false)
    private val queuedFinalId = AtomicLong(0)

    private class Segment(val id: Long, val pcm: ShortArray)

    override fun start(config: ListenConfig) {
        stop()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            emit(SpeechEvent.Error("Ứng dụng chưa được cấp quyền micro.", true))
            return
        }
        val cfg = cloudConfig()
        if (cfg.apiKey.isBlank() || cfg.baseUrl.isBlank()) {
            emit(SpeechEvent.Error("Chưa nhập API key cho Whisper. Vào Cài đặt › API đám mây.", true))
            return
        }
        running = true
        paused = false
        val gen = generation.get()
        val segments = Channel<Segment>(Channel.UNLIMITED)
        workerJob = scope.launch {
            for (s in segments) transcribeFinal(s, config)
            if (config.singleUtterance) emit(SpeechEvent.Status(""))
        }
        recordJob = scope.launch(Dispatchers.IO) { recordLoop(config, segments, gen) }
        emit(SpeechEvent.Status("Đang nghe…"))
    }

    /** Dừng thu âm; các câu đã thu vẫn được nhận dạng nốt. */
    override fun stop() {
        generation.incrementAndGet()
        running = false
        paused = false
        recordJob = null
        workerJob = null
    }

    override fun pause() {
        if (!running) return
        paused = true
        emit(SpeechEvent.Level(0f))
        emit(SpeechEvent.Status("Tạm ngắt micro khi đang đọc…"))
    }

    override fun resume() {
        if (!running) return
        paused = false
        emit(SpeechEvent.Status("Đang nghe…"))
    }

    override fun release() {
        generation.incrementAndGet()
        running = false
        scope.cancel()
    }

    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private suspend fun recordLoop(cfg: ListenConfig, out: Channel<Segment>, gen: Long) {
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf, FRAME * 2 * 16)
            )
        } catch (e: Exception) {
            running = false
            emit(SpeechEvent.Error("Không mở được micro: ${e.message}", true))
            out.close()
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            running = false
            emit(SpeechEvent.Error("Không mở được micro (có thể ứng dụng khác đang dùng).", true))
            out.close()
            return
        }

        val sens = cfg.vadSensitivity.coerceIn(0f, 1f).toDouble()
        val k = 3.2 - 1.8 * sens            // hệ số so với nền nhiễu
        val absMin = 260.0 - 200.0 * sens   // ngưỡng tuyệt đối (thang int16)
        val endSilenceFrames = max(8, cfg.endSilenceMs / FRAME_MS)
        val maxFrames = max(100, cfg.maxSegmentMs / FRAME_MS)
        val softMaxFrames = maxFrames * 2 / 3
        val idleTimeoutFrames = 8000 / FRAME_MS

        val frame = ShortArray(FRAME)
        val preRoll = ArrayDeque<ShortArray>()
        val recentLoud = ArrayDeque<Boolean>()
        val buffer = PcmBuffer()
        var inSpeech = false
        var silenceFrames = 0
        var speechFrames = 0
        var segFrames = 0
        var lastPartialAt = 0
        var noiseFloor = -1.0
        var segId = queuedFinalId.get()
        var counter = 0
        var idleFrames = 0
        var keepGoing = true

        try {
            rec.startRecording()
            while (currentCoroutineContext().isActive && keepGoing && generation.get() == gen) {
                val n = rec.read(frame, 0, FRAME)
                if (n <= 0) {
                    if (n < 0) {
                        emit(SpeechEvent.Error("Lỗi đọc micro ($n).", true))
                        keepGoing = false
                    }
                    continue
                }
                if (paused) {
                    inSpeech = false
                    buffer.clear(); preRoll.clear(); recentLoud.clear()
                    silenceFrames = 0; speechFrames = 0; segFrames = 0
                    continue
                }

                val rms = rms(frame, n)
                if (noiseFloor < 0) noiseFloor = rms
                if (rms < noiseFloor) noiseFloor = noiseFloor * 0.9 + rms * 0.1
                else if (!inSpeech) noiseFloor = noiseFloor * 0.995 + rms * 0.005
                val threshold = max(noiseFloor * k, absMin)
                val loud = rms > threshold

                if (++counter % 2 == 0) emit(SpeechEvent.Level(min(1.0, rms / 4000.0).toFloat()))

                val copy = frame.copyOf(n)
                if (!inSpeech) {
                    preRoll.addLast(copy)
                    if (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
                    recentLoud.addLast(loud)
                    if (recentLoud.size > 5) recentLoud.removeFirst()
                    if (recentLoud.count { it } >= 3) {
                        inSpeech = true
                        segId++
                        buffer.clear()
                        preRoll.forEach { buffer.add(it) }
                        preRoll.clear(); recentLoud.clear()
                        silenceFrames = 0
                        speechFrames = 3
                        segFrames = buffer.frames(FRAME)
                        lastPartialAt = segFrames
                        idleFrames = 0
                    } else if (cfg.singleUtterance && ++idleFrames > idleTimeoutFrames) {
                        emit(SpeechEvent.Error("Không nghe thấy gì. Hãy bấm micro và nói lại.", true))
                        keepGoing = false
                    }
                } else {
                    buffer.add(copy)
                    segFrames++
                    if (loud) {
                        speechFrames++; silenceFrames = 0
                    } else silenceFrames++

                    val end = silenceFrames >= endSilenceFrames ||
                        segFrames >= maxFrames ||
                        (segFrames >= softMaxFrames && silenceFrames >= 8)
                    if (end) {
                        val trailing = (silenceFrames - 7).coerceAtLeast(0) * FRAME
                        if (speechFrames >= MIN_SPEECH_FRAMES) {
                            queuedFinalId.set(segId)
                            out.trySend(Segment(segId, buffer.toShortArray(trailing)))
                        }
                        inSpeech = false
                        buffer.clear()
                        silenceFrames = 0; speechFrames = 0; segFrames = 0
                        if (cfg.singleUtterance) keepGoing = false
                    } else if (cfg.cloudPartials && segFrames - lastPartialAt >= PARTIAL_EVERY_FRAMES) {
                        lastPartialAt = segFrames
                        launchPartial(segId, buffer.toShortArray(0), cfg)
                    }
                }
            }
            // Người dùng bấm dừng khi đang nói dở → vẫn xử lý phần đã nói
            if (inSpeech && speechFrames >= MIN_SPEECH_FRAMES) {
                queuedFinalId.set(segId)
                out.trySend(Segment(segId, buffer.toShortArray(0)))
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
            emit(SpeechEvent.Level(0f))
            out.close()
        }
    }

    private suspend fun transcribeFinal(seg: Segment, cfg: ListenConfig) {
        emit(SpeechEvent.Status("Đang nhận dạng…"))
        try {
            val wav = Wav.encode(normalizeGain(seg.pcm), RATE)
            val conf = cloudConfig()
            val forced = cfg.forced?.code
            var t = api.transcribe(conf, wav, forced)
            var lang = Languages.fromWhisper(t.language)?.code ?: forced

            // Hội thoại 2 ngôn ngữ: Whisper đoán ra ngôn ngữ thứ 3 → nhận dạng lại với ngôn ngữ phù hợp nhất
            val candidates = cfg.candidates.map { it.code }
            if (forced == null && candidates.isNotEmpty() && lang != null && lang !in candidates) {
                val retry = detector.resolve(t.text, null, false, candidates)
                if (retry != null) {
                    t = api.transcribe(conf, wav, retry)
                    lang = retry
                }
            }

            val text = t.text.trim()
            if (text.isBlank() || isHallucination(text, t.noSpeechProb, seg.pcm.size)) {
                if (running) emit(SpeechEvent.Status("Đang nghe…"))
                return
            }
            emit(SpeechEvent.Final(text, lang, hintTrusted = lang != null))
            if (running) emit(SpeechEvent.Status("Đang nghe…"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            val fatal = e.code == 401 || e.code == 403 || e.code == 404
            if (fatal) {
                generation.incrementAndGet()
                running = false
            }
            emit(SpeechEvent.Error("Whisper: ${e.message}", fatal))
        } catch (e: Exception) {
            emit(SpeechEvent.Error("Lỗi kết nối Whisper: ${e.message ?: e.javaClass.simpleName}", false))
        }
    }

    private fun launchPartial(segId: Long, pcm: ShortArray, cfg: ListenConfig) {
        if (!partialInFlight.compareAndSet(false, true)) return
        scope.launch {
            try {
                val t = api.transcribe(cloudConfig(), Wav.encode(normalizeGain(pcm), RATE), cfg.forced?.code)
                val text = t.text.trim()
                if (running && segId > queuedFinalId.get() && text.isNotBlank() &&
                    !isHallucination(text, t.noSpeechProb, pcm.size)
                ) {
                    emit(SpeechEvent.Partial(text, Languages.fromWhisper(t.language)?.code))
                }
            } catch (_: Exception) {
            } finally {
                partialInFlight.set(false)
            }
        }
    }

    private fun emit(e: SpeechEvent) {
        channel.trySend(e)
    }

    companion object {
        private const val RATE = 16_000
        private const val FRAME_MS = 30
        private const val FRAME = RATE * FRAME_MS / 1000 // 480 mẫu
        private const val PRE_ROLL_FRAMES = 10            // 300 ms trước khi bắt đầu nói
        private const val MIN_SPEECH_FRAMES = 12          // tối thiểu ~360 ms tiếng nói
        private const val PARTIAL_EVERY_FRAMES = 70       // ~2,1 giây

        private val HALLUCINATIONS = listOf(
            "thank you for watching", "thanks for watching", "please subscribe", "like and subscribe",
            "subtitles by", "amara.org", "hãy subscribe", "đăng ký kênh", "cảm ơn các bạn đã theo dõi",
            "ご視聴ありがとうございました", "字幕由", "продолжение следует", "untertitel", "sous-titres"
        )

        fun isHallucination(text: String, noSpeechProb: Double?, samples: Int): Boolean {
            val lower = text.lowercase().trim(' ', '.', '!', '?', '。', '…')
            if (lower.isEmpty()) return true
            if (noSpeechProb != null && noSpeechProb > 0.8) return true
            val shortAudio = samples < RATE * 3
            if (HALLUCINATIONS.any { lower.contains(it) } && (shortAudio || lower.length < 60)) return true
            if (shortAudio && (lower == "thank you" || lower == "you" || lower == "bye")) return true
            return false
        }

        private fun rms(buf: ShortArray, n: Int): Double {
            var sum = 0.0
            for (i in 0 until n) {
                val v = buf[i].toDouble()
                sum += v * v
            }
            return sqrt(sum / n)
        }

        /** Khuếch đại âm thanh nhỏ (người nói ở xa) để Whisper nghe rõ hơn. */
        fun normalizeGain(pcm: ShortArray): ShortArray {
            var peak = 1
            for (s in pcm) peak = max(peak, abs(s.toInt()))
            if (peak >= 12_000) return pcm
            val gain = min(8.0, 20_000.0 / peak)
            return ShortArray(pcm.size) { i ->
                (pcm[i] * gain).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
    }
}

/** Bộ đệm PCM 16-bit có thể tăng kích thước. */
internal class PcmBuffer {
    private var data = ShortArray(RATE_HINT * 4)
    private var size = 0

    fun add(chunk: ShortArray) {
        if (size + chunk.size > data.size) {
            data = data.copyOf(max(data.size * 2, size + chunk.size))
        }
        System.arraycopy(chunk, 0, data, size, chunk.size)
        size += chunk.size
    }

    fun clear() {
        size = 0
    }

    fun frames(frameSize: Int): Int = size / frameSize

    fun toShortArray(dropLast: Int): ShortArray = data.copyOf((size - dropLast).coerceAtLeast(0))

    private companion object {
        const val RATE_HINT = 16_000
    }
}

object Wav {
    fun encode(pcm: ShortArray, sampleRate: Int): ByteArray {
        val dataLen = pcm.size * 2
        val b = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII))
        b.putInt(36 + dataLen)
        b.put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII))
        b.putInt(16)
        b.putShort(1.toShort())          // PCM
        b.putShort(1.toShort())          // mono
        b.putInt(sampleRate)
        b.putInt(sampleRate * 2)         // byte rate
        b.putShort(2.toShort())          // block align
        b.putShort(16.toShort())         // bits per sample
        b.put("data".toByteArray(Charsets.US_ASCII))
        b.putInt(dataLen)
        for (s in pcm) b.putShort(s)
        return b.array()
    }
}
