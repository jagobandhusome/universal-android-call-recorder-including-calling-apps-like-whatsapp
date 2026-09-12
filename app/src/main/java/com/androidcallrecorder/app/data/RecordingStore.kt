package com.androidcallrecorder.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object RecordingStore {
    private const val FILE = "recordings_index.json"
    private var appContext: Context? = null
    private val _items = MutableStateFlow<List<RecordingItem>>(emptyList())
    val items: StateFlow<List<RecordingItem>> = _items.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        _items.value = read().sortedByDescending { it.startedAt }
    }

    fun all(): List<RecordingItem> = _items.value

    fun reload() {
        _items.value = read().sortedByDescending { it.startedAt }
    }

    fun add(item: RecordingItem) {
        val next = listOf(item) + _items.value.filterNot { it.id == item.id }
        persist(next)
    }

    fun update(item: RecordingItem) {
        persist(_items.value.map { if (it.id == item.id) item else it })
    }

    fun delete(id: String) {
        val item = _items.value.firstOrNull { it.id == id }
        item?.let { File(it.filePath).delete() }
        persist(_items.value.filterNot { it.id == id })
    }

    fun deleteOlderThan(days: Int): Int {
        if (days <= 0) return 0
        val s = SettingsStore.current()
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val stale = _items.value.filter { item ->
            item.startedAt < cutoff &&
                !(s.keepStarred && item.starred) &&
                !(s.keepLocked && item.locked)
        }
        stale.forEach { File(it.filePath).takeIf { f -> f.exists() }?.delete() }
        persist(_items.value.filter { keep -> stale.none { it.id == keep.id } })
        return stale.size
    }

    fun deleteMany(ids: Collection<String>) {
        val remove = _items.value.filter { it.id in ids && !it.locked }
        remove.forEach { File(it.filePath).takeIf { f -> f.exists() }?.delete() }
        persist(_items.value.filterNot { it.id in remove.map { r -> r.id } })
    }

    fun rename(id: String, newName: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        val src = File(item.filePath)
        val dest = File(src.parentFile, FileNamer.sanitize(newName) + if (newName.contains('.')) "" else ".${src.extension}")
        if (src.exists()) src.renameTo(dest)
        update(item.copy(filePath = dest.absolutePath, displayName = dest.name))
    }

    fun newId(): String = UUID.randomUUID().toString()

    private fun persist(list: List<RecordingItem>) {
        _items.value = list.sortedByDescending { it.startedAt }
        val ctx = appContext ?: return
        ctx.fileStream(FILE).writeText(toJson(list))
    }

    private fun read(): List<RecordingItem> {
        val ctx = appContext ?: return emptyList()
        val file = ctx.fileStream(FILE)
        if (!file.exists()) return emptyList()
        return try {
            fromJson(file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun Context.fileStream(name: String): File = File(filesDir, name)

    private fun toJson(list: List<RecordingItem>): String {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("filePath", r.filePath)
                    .put("displayName", r.displayName)
                    .put("source", r.source)
                    .put("contact", r.contact)
                    .put("number", r.number)
                    .put("direction", r.direction.name)
                    .put("startedAt", r.startedAt)
                    .put("durationMs", r.durationMs)
                    .put("sizeBytes", r.sizeBytes)
                    .put("kind", r.kind.name)
                    .put("format", r.format)
                    .put("starred", r.starred)
                    .put("locked", r.locked)
                    .put("missed", r.missed)
                    .put("notes", r.notes)
                    .put("transcript", r.transcript)
                    .put("summary", r.summary)
                    .put("encrypted", r.encrypted),
            )
        }
        return arr.toString()
    }

    private fun fromJson(raw: String): List<RecordingItem> {
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    RecordingItem(
                        id = o.getString("id"),
                        filePath = o.getString("filePath"),
                        displayName = o.optString("displayName"),
                        source = o.optString("source"),
                        contact = o.optString("contact"),
                        number = o.optString("number"),
                        direction = runCatching {
                            CallDirection.valueOf(o.optString("direction"))
                        }.getOrDefault(CallDirection.UNKNOWN),
                        startedAt = o.optLong("startedAt"),
                        durationMs = o.optLong("durationMs"),
                        sizeBytes = o.optLong("sizeBytes"),
                        kind = runCatching { MediaKind.valueOf(o.optString("kind")) }
                            .getOrDefault(MediaKind.AUDIO),
                        format = o.optString("format"),
                        starred = o.optBoolean("starred"),
                        locked = o.optBoolean("locked"),
                        missed = o.optBoolean("missed"),
                        notes = o.optString("notes"),
                        transcript = o.optString("transcript"),
                        summary = o.optString("summary"),
                        encrypted = o.optBoolean("encrypted"),
                    ),
                )
            }
        }
    }
}
