package vn.dichtructiep.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Một câu đã dịch trong phiên. side: 0 = không phân vai, 1 = người A, 2 = người B. */
data class HistoryEntry(
    val time: Long,
    val side: Int,
    val source: String,
    val sourceLang: String?,
    val translation: String,
    val targetLang: String,
)

data class SessionRecord(
    val id: String,
    val mode: String,          // "listen" | "conversation" | "text"
    val startedAt: Long,
    val title: String,
    val entries: List<HistoryEntry>,
)

class HistoryRepository(context: Context) {

    private val dir = File(context.filesDir, "sessions").apply { mkdirs() }
    private val mutex = Mutex()
    private val _sessions = MutableStateFlow<List<SessionRecord>>(emptyList())
    val sessions: StateFlow<List<SessionRecord>> = _sessions.asStateFlow()
    private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val list = dir.listFiles { f -> f.name.endsWith(".json") }
                ?.mapNotNull { f -> runCatching { fromJson(JSONObject(f.readText())) }.getOrNull() }
                ?.sortedByDescending { it.startedAt }
                ?: emptyList()
            _sessions.value = list
            loaded = true
        }
    }

    suspend fun save(record: SessionRecord) = withContext(Dispatchers.IO) {
        if (!loaded) load()
        mutex.withLock {
            File(dir, "${record.id}.json").writeText(toJson(record).toString())
            _sessions.value = (listOf(record) + _sessions.value.filter { it.id != record.id })
                .sortedByDescending { it.startedAt }
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(dir, "$id.json").delete()
            _sessions.value = _sessions.value.filter { it.id != id }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            dir.listFiles()?.forEach { it.delete() }
            _sessions.value = emptyList()
        }
    }

    fun get(id: String): SessionRecord? = _sessions.value.firstOrNull { it.id == id }

    companion object {
        private val dateFmt = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale("vi"))
        private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale("vi"))

        fun formatDate(t: Long): String = dateFmt.format(Date(t))
        fun formatTime(t: Long): String = timeFmt.format(Date(t))

        fun modeLabel(mode: String): String = when (mode) {
            "listen" -> "Nghe & dịch"
            "conversation" -> "Hội thoại"
            else -> "Văn bản"
        }

        fun toPlainText(r: SessionRecord): String = buildString {
            appendLine("${modeLabel(r.mode)} — ${r.title}")
            appendLine("Bắt đầu: ${formatDate(r.startedAt)}")
            appendLine()
            for (e in r.entries) {
                val who = when (e.side) { 1 -> "[A] "; 2 -> "[B] "; else -> "" }
                appendLine("${formatTime(e.time)} $who(${Languages.name(e.sourceLang)}) ${e.source}")
                appendLine("    → (${Languages.name(e.targetLang)}) ${e.translation}")
                appendLine()
            }
        }

        private fun toJson(r: SessionRecord): JSONObject = JSONObject()
            .put("id", r.id)
            .put("mode", r.mode)
            .put("startedAt", r.startedAt)
            .put("title", r.title)
            .put("entries", JSONArray().apply {
                r.entries.forEach { e ->
                    put(
                        JSONObject()
                            .put("t", e.time)
                            .put("side", e.side)
                            .put("src", e.source)
                            .put("srcLang", e.sourceLang ?: "")
                            .put("dst", e.translation)
                            .put("dstLang", e.targetLang)
                    )
                }
            })

        private fun fromJson(o: JSONObject): SessionRecord {
            val arr = o.optJSONArray("entries") ?: JSONArray()
            val entries = (0 until arr.length()).map { i ->
                val e = arr.getJSONObject(i)
                HistoryEntry(
                    time = e.optLong("t"),
                    side = e.optInt("side"),
                    source = e.optString("src"),
                    sourceLang = e.optString("srcLang").ifBlank { null },
                    translation = e.optString("dst"),
                    targetLang = e.optString("dstLang"),
                )
            }
            return SessionRecord(
                id = o.getString("id"),
                mode = o.optString("mode", "listen"),
                startedAt = o.optLong("startedAt"),
                title = o.optString("title"),
                entries = entries,
            )
        }
    }
}
