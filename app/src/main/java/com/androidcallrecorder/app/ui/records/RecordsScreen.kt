package com.androidcallrecorder.app.ui.records

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidcallrecorder.app.data.CallDirection
import com.androidcallrecorder.app.data.FileNamer
import com.androidcallrecorder.app.data.GroupMode
import com.androidcallrecorder.app.data.MediaKind
import com.androidcallrecorder.app.data.RecordingItem
import com.androidcallrecorder.app.data.RecordingStore
import com.androidcallrecorder.app.data.SortMode
import com.androidcallrecorder.app.record.SilenceTrimmer
import com.androidcallrecorder.app.record.Summarizer
import com.androidcallrecorder.app.security.Vault
import com.androidcallrecorder.app.ui.components.SectionCard
import com.androidcallrecorder.app.ui.i18n.L
import com.androidcallrecorder.app.ui.player.WaveformPlayerDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class RecordFilter { ALL, INCOMING, OUTGOING, MISSED, VOIP, SCREEN, VIDEO }

@Composable
fun RecordsScreen(
    onShare: (RecordingItem) -> Unit,
    onDelete: (RecordingItem) -> Unit,
    onConvert: (RecordingItem) -> Unit,
    onBulkDelete: (List<RecordingItem>) -> Unit,
    onExportPdf: (RecordingItem) -> Unit,
) {
    val items by RecordingStore.items.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(RecordFilter.ALL) }
    var sort by remember { mutableStateOf(SortMode.NEWEST) }
    var group by remember { mutableStateOf(GroupMode.NONE) }
    var contactFilter by remember { mutableStateOf("") }
    var datePreset by remember { mutableStateOf("any") }
    var playing by remember { mutableStateOf<RecordingItem?>(null) }
    var renameTarget by remember { mutableStateOf<RecordingItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var notesTarget by remember { mutableStateOf<RecordingItem?>(null) }
    var notesText by remember { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }

    val filtered = items
        .filter { item ->
            val q = query.trim().lowercase()
            val textOk = q.isEmpty() ||
                item.displayName.lowercase().contains(q) ||
                item.contact.lowercase().contains(q) ||
                item.number.contains(q) ||
                item.source.lowercase().contains(q)
            val filterOk = when (filter) {
                RecordFilter.ALL -> true
                RecordFilter.INCOMING -> item.direction == CallDirection.INCOMING && !item.missed
                RecordFilter.OUTGOING -> item.direction == CallDirection.OUTGOING
                RecordFilter.MISSED -> item.missed
                RecordFilter.VOIP -> item.source != "phone" && item.kind != MediaKind.SCREEN
                RecordFilter.SCREEN -> item.kind == MediaKind.SCREEN
                RecordFilter.VIDEO -> item.kind == MediaKind.VIDEO
            }
            val contactOk = contactFilter.isBlank() ||
                item.contact.contains(contactFilter, true) ||
                item.number.contains(contactFilter)
            val dateOk = when (datePreset) {
                "today" -> item.startedAt >= startOfDay()
                "7d" -> item.startedAt >= System.currentTimeMillis() - 7L * 86400000
                "30d" -> item.startedAt >= System.currentTimeMillis() - 30L * 86400000
                else -> true
            }
            textOk && filterOk && contactOk && dateOk
        }
        .let { list ->
            when (sort) {
                SortMode.NEWEST -> list.sortedByDescending { it.startedAt }
                SortMode.OLDEST -> list.sortedBy { it.startedAt }
                SortMode.LARGEST -> list.sortedByDescending { it.sizeBytes }
                SortMode.LONGEST -> list.sortedByDescending { it.durationMs }
            }
        }

    val grouped: List<Pair<String, List<RecordingItem>>> = when (group) {
        GroupMode.NONE -> listOf("" to filtered)
        GroupMode.CONTACT -> filtered.groupBy { it.contact.ifBlank { it.number.ifBlank { "Unknown" } } }.toList()
        GroupMode.DATE -> filtered.groupBy {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it.startedAt))
        }.toList()
        GroupMode.APP -> filtered.groupBy { it.source }.toList()
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(L.t("Search name, number, app", "নাম, নম্বর, অ্যাপ খুঁজুন")) },
            singleLine = true,
        )
        ChipScroll(RecordFilter.entries.map { it to it.name.lowercase() }, filter) { filter = it }
        ChipScroll(
            listOf(SortMode.NEWEST to "Newest", SortMode.OLDEST to "Oldest", SortMode.LARGEST to "Largest", SortMode.LONGEST to "Longest"),
            sort,
        ) { sort = it }
        ChipScroll(
            listOf(GroupMode.NONE to "No group", GroupMode.CONTACT to "Contact", GroupMode.DATE to "Date", GroupMode.APP to "App"),
            group,
        ) { group = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = contactFilter,
                onValueChange = { contactFilter = it },
                modifier = Modifier.weight(1f),
                label = { Text(L.t("Contact", "কন্টাক্ট")) },
                singleLine = true,
            )
        }
        ChipScroll(
            listOf("any" to "Any date", "today" to "Today", "7d" to "7 days", "30d" to "30 days"),
            datePreset,
        ) { datePreset = it }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { selecting = !selecting; if (!selecting) selected = emptySet() }) {
                Text(if (selecting) L.t("Cancel", "বাতিল") else L.t("Select", "সিলেক্ট"))
            }
            if (selecting && selected.isNotEmpty()) {
                TextButton(onClick = {
                    onBulkDelete(filtered.filter { it.id in selected })
                    selected = emptySet()
                    selecting = false
                }) { Text(L.t("Delete selected", "নির্বাচিত মুছুন")) }
            }
        }

        if (filtered.isEmpty()) {
            SectionCard(L.t("No recordings", "কোনো রেকর্ড নেই"), L.t("Finished captures appear here.", "শেষ হওয়া রেকর্ড এখানে আসবে।")) {}
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                grouped.forEach { (header, rows) ->
                    if (header.isNotBlank()) {
                        item(key = "h-$header") {
                            Text(header, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    items(rows, key = { it.id }) { item ->
                        RecordingRow(
                            item = item,
                            selecting = selecting,
                            checked = item.id in selected,
                            onCheck = {
                                selected = if (item.id in selected) selected - item.id else selected + item.id
                            },
                            onPlay = { if (item.filePath.isNotBlank()) playing = item },
                            onShare = { onShare(item) },
                            onDelete = { onDelete(item) },
                            onRename = {
                                renameTarget = item
                                renameText = item.displayName
                            },
                            onLock = { RecordingStore.update(item.copy(locked = !item.locked)) },
                            onConvert = { onConvert(item) },
                            onNotes = {
                                notesTarget = item
                                notesText = item.notes
                            },
                            onPdf = { onExportPdf(item) },
                            onVault = {
                                if (!item.encrypted && item.filePath.isNotBlank()) {
                                    val dest = Vault.encrypt(File(item.filePath))
                                    RecordingStore.update(
                                        item.copy(filePath = dest.absolutePath, encrypted = true, displayName = dest.name),
                                    )
                                }
                            },
                            onSilence = {
                                if (item.filePath.isNotBlank() && !item.encrypted) {
                                    SilenceTrimmer.removeSilence(item)
                                }
                            },
                            onSummarize = {
                                val sum = Summarizer.threeLines(item.transcript.ifBlank { item.notes })
                                RecordingStore.update(item.copy(summary = sum))
                            },
                        )
                    }
                }
            }
        }
    }

    playing?.let { item ->
        WaveformPlayerDialog(item = item, onDismiss = { playing = null })
    }

    notesTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { notesTarget = null },
            title = { Text(L.t("Notes & transcript", "নোট ও ট্রান্সক্রিপ্ট")) },
            text = {
                Column {
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text(L.t("Call notes", "কল নোট")) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                    if (item.transcript.isNotBlank()) {
                        Text(item.transcript, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (item.summary.isNotBlank()) {
                        Text(item.summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    RecordingStore.update(item.copy(notes = notesText))
                    notesTarget = null
                }) { Text(L.t("Save", "সেভ")) }
            },
            dismissButton = { TextButton(onClick = { notesTarget = null }) { Text(L.t("Cancel", "বাতিল")) } },
        )
    }

    renameTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(L.t("Rename", "নাম বদলান")) },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    RecordingStore.rename(item.id, renameText)
                    renameTarget = null
                }) { Text(L.t("Save", "সেভ")) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(L.t("Cancel", "বাতিল")) } },
        )
    }
}

@Composable
private fun <T> ChipScroll(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun RecordingRow(
    item: RecordingItem,
    selecting: Boolean,
    checked: Boolean,
    onCheck: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onLock: () -> Unit,
    onConvert: () -> Unit,
    onNotes: () -> Unit,
    onPdf: () -> Unit,
    onVault: () -> Unit,
    onSilence: () -> Unit,
    onSummarize: () -> Unit,
) {
    val date = remember(item.startedAt) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(item.startedAt))
    }
    SectionCard(
        item.displayName,
        "$date · ${item.source} · ${if (item.missed) "missed" else item.direction.name.lowercase()}",
    ) {
        Text(
            "${FileNamer.formatDuration(item.durationMs)}  ·  ${formatSize(item.sizeBytes)}  ·  ${item.format.uppercase()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (selecting) {
                Checkbox(checked = checked, onCheckedChange = { onCheck() })
            }
            if (!item.missed) IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, "Play") }
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Share") }
            IconButton(onClick = onRename) { Icon(Icons.Filled.DriveFileRenameOutline, "Rename") }
            IconButton(onClick = onLock) {
                Icon(if (item.locked) Icons.Filled.Lock else Icons.Filled.LockOpen, "Lock")
            }
            if (!item.missed) IconButton(onClick = onConvert) { Icon(Icons.Filled.SwapHoriz, "Convert") }
            IconButton(onClick = onNotes) { Icon(Icons.Filled.Notes, "Notes") }
            IconButton(onClick = onPdf) { Icon(Icons.Filled.PictureAsPdf, "PDF") }
            if (!item.encrypted) IconButton(onClick = onVault) { Icon(Icons.Filled.Security, "Vault") }
            if (!item.missed && !item.encrypted) {
                TextButton(onClick = onSilence) { Text(L.t("Silence", "নীরবতা")) }
                TextButton(onClick = onSummarize) { Text(L.t("Summary", "সারাংশ")) }
            }
            if (!item.locked) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
        }
    }
}

private fun startOfDay(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    return String.format(Locale.US, "%.1f MB", kb / 1024.0)
}
