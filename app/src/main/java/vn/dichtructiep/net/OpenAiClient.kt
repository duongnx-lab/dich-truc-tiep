package vn.dichtructiep.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import vn.dichtructiep.data.CloudConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(message: String, val code: Int = 0) : Exception(message)

data class Transcription(
    val text: String,
    /** Tên/mã ngôn ngữ do dịch vụ trả về (Whisper: "english", "vietnamese"...). */
    val language: String?,
    /** Xác suất "không có tiếng nói" trung bình (chỉ có với verbose_json). */
    val noSpeechProb: Double?,
)

/**
 * Client tối giản cho mọi API tương thích OpenAI (OpenAI, Groq, máy chủ tự host...).
 */
class OpenAiClient {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Chuyển âm thanh WAV thành văn bản. language = null → tự nhận diện ngôn ngữ. */
    suspend fun transcribe(cfg: CloudConfig, wav: ByteArray, language: String?): Transcription {
        require(cfg.baseUrl.isNotBlank() && cfg.apiKey.isNotBlank()) { "Chưa cấu hình API" }
        val verbose = cfg.sttModel.contains("whisper", ignoreCase = true)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", cfg.sttModel)
            .addFormDataPart("file", "audio.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("response_format", if (verbose) "verbose_json" else "json")
            .addFormDataPart("temperature", "0")
            .apply { if (!language.isNullOrBlank()) addFormDataPart("language", language) }
            .build()
        val req = Request.Builder()
            .url("${cfg.baseUrl}/audio/transcriptions")
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .post(body)
            .build()
        val json = JSONObject(execute(req))
        var noSpeech: Double? = null
        json.optJSONArray("segments")?.let { segs ->
            if (segs.length() > 0) {
                var sum = 0.0
                for (i in 0 until segs.length()) sum += segs.getJSONObject(i).optDouble("no_speech_prob", 0.0)
                noSpeech = sum / segs.length()
            }
        }
        return Transcription(
            text = json.optString("text").trim(),
            language = json.optString("language").ifBlank { null },
            noSpeechProb = noSpeech,
        )
    }

    /** Gọi chat completion, trả về nội dung trả lời. */
    suspend fun chat(
        cfg: CloudConfig,
        system: String,
        user: String,
        temperature: Double? = 0.2,
    ): String {
        require(cfg.baseUrl.isNotBlank() && cfg.apiKey.isNotBlank()) { "Chưa cấu hình API" }
        val payload = JSONObject()
            .put("model", cfg.chatModel)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user))
            )
        if (temperature != null) payload.put("temperature", temperature)
        val req = Request.Builder()
            .url("${cfg.baseUrl}/chat/completions")
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        val raw = try {
            execute(req)
        } catch (e: ApiException) {
            // Một số mô hình mới không cho phép đặt temperature → thử lại không có tham số này.
            if (temperature != null && e.code == 400 && e.message?.contains("temperature", true) == true) {
                return chat(cfg, system, user, null)
            }
            throw e
        }
        val json = JSONObject(raw)
        val content = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?: throw ApiException("Phản hồi không hợp lệ từ API")
        return stripThinking(content).trim()
    }

    /** Kiểm tra kết nối & API key bằng cách liệt kê mô hình. */
    suspend fun test(cfg: CloudConfig): String {
        val req = Request.Builder()
            .url("${cfg.baseUrl}/models")
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .get()
            .build()
        val json = JSONObject(execute(req))
        val data = json.optJSONArray("data")
        val ids = (0 until (data?.length() ?: 0)).mapNotNull { data?.optJSONObject(it)?.optString("id") }
        val sttOk = ids.isEmpty() || ids.any { it == cfg.sttModel }
        val chatOk = ids.isEmpty() || ids.any { it == cfg.chatModel }
        return buildString {
            append("Kết nối thành công (${ids.size} mô hình).")
            if (!sttOk) append("\n⚠ Không thấy mô hình nhận dạng \"${cfg.sttModel}\".")
            if (!chatOk) append("\n⚠ Không thấy mô hình dịch \"${cfg.chatModel}\".")
        }
    }

    private suspend fun execute(req: Request): String = withContext(Dispatchers.IO) {
        val response = http.newCall(req).await()
        response.use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: body.take(200)
                throw ApiException("HTTP ${r.code}: $msg", r.code)
            }
            body
        }
    }

    private fun stripThinking(s: String): String =
        s.replace(Regex("(?s)<think>.*?</think>"), "")

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }
}
