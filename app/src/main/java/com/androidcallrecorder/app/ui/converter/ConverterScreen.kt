package com.androidcallrecorder.app.ui.converter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidcallrecorder.app.data.ConvertTarget
import com.androidcallrecorder.app.data.MediaKind
import com.androidcallrecorder.app.data.RecordingItem
import com.androidcallrecorder.app.data.RecordingStore
import com.androidcallrecorder.app.data.SettingsStore
import com.androidcallrecorder.app.record.FormatConverter
import com.androidcallrecorder.app.ui.components.ChipRow
import com.androidcallrecorder.app.ui.components.SectionCard
import com.androidcallrecorder.app.ui.i18n.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(preselectedId: String? = null) {
    val items by RecordingStore.items.collectAsStateWithLifecycle()
    val usable = items.filter { !it.missed && it.filePath.isNotBlank() }
    var selected by remember(preselectedId, usable) {
        mutableStateOf(usable.firstOrNull { it.id == preselectedId } ?: usable.firstOrNull())
    }
    var second by remember { mutableStateOf<RecordingItem?>(null) }
    var target by remember { mutableStateOf(ConvertTarget.M4A) }
    var expanded by remember { mutableStateOf(false) }
    var expanded2 by remember { mutableStateOf(false) }
    var startSec by remember { mutableStateOf("0") }
    var endSec by remember { mutableStateOf("") }
    var status by remember {
        mutableStateOf(L.t("Pick a file, then convert, trim, or merge.", "ফাইল বেছে কনভার্ট, ট্রিম বা মার্জ করুন।"))
    }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val settings = SettingsStore.current()

    fun runJob(label: String, block: () -> String) {
        busy = true
        status = label
        scope.launch {
            status = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                "${L.t("Failed", "ব্যর্থ")}: ${e.message}"
            }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(L.t("Convert recording", "রেকর্ড কনভার্ট"), status) {
            FileDropdown(
                label = L.t("Source", "সোর্স"),
                items = usable,
                selected = selected,
                expanded = expanded,
                onExpanded = { expanded = it },
                onSelect = { selected = it; expanded = false },
            )
            ChipRow(ConvertTarget.entries.map { it to it.label }, target) { target = it }
            Text(
                L.t(
                    "Bitrate ${settings.bitRate / 1000} kbps · ${settings.sampleRate} Hz · ${settings.channels.name.lowercase()}",
                    "বিটরেট ${settings.bitRate / 1000} kbps · ${settings.sampleRate} Hz · ${settings.channels.name}",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                L.t(
                    "Android encodes AAC/M4A/WAV/MP4. MP3/AVI/MKV are remuxed to a supported container.",
                    "অ্যান্ড্রয়েড AAC/M4A/WAV/MP4 এনকোড করে। MP3/AVI/MKV সাপোর্টেড কন্টেইনারে যাবে।",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = selected != null && !busy,
                onClick = {
                    val item = selected ?: return@Button
                    runJob(L.t("Converting…", "কনভার্ট হচ্ছে…")) {
                        "Saved ${FormatConverter.convert(item, target).displayName}"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "…" else L.t("Convert", "কনভার্ট")) }
            Button(
                enabled = selected != null && selected?.kind != MediaKind.AUDIO && !busy,
                onClick = {
                    val item = selected ?: return@Button
                    runJob(L.t("Extracting audio…", "অডিও বের হচ্ছে…")) {
                        "Audio saved as ${FormatConverter.videoToAudio(item, target).displayName}"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(L.t("Video → audio", "ভিডিও → অডিও")) }
        }

        SectionCard(L.t("Trim / cut", "ট্রিম / কাট")) {
            OutlinedTextField(
                value = startSec,
                onValueChange = { startSec = it.filter { c -> c.isDigit() } },
                label = { Text(L.t("Start (seconds)", "শুরু (সেকেন্ড)")) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = endSec,
                onValueChange = { endSec = it.filter { c -> c.isDigit() } },
                label = { Text(L.t("End (seconds)", "শেষ (সেকেন্ড)")) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = selected != null && !busy,
                onClick = {
                    val item = selected ?: return@Button
                    val start = startSec.toLongOrNull()?.times(1000) ?: 0L
                    val end = endSec.toLongOrNull()?.times(1000) ?: item.durationMs
                    runJob(L.t("Trimming…", "ট্রিম হচ্ছে…")) {
                        "Saved ${FormatConverter.trim(item, start, end).displayName}"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(L.t("Trim", "ট্রিম")) }
        }

        SectionCard(L.t("Merge", "মার্জ"), L.t("Decoded to WAV then joined.", "WAV করে জোড়া হবে।")) {
            FileDropdown(
                label = L.t("Second file", "দ্বিতীয় ফাইল"),
                items = usable,
                selected = second,
                expanded = expanded2,
                onExpanded = { expanded2 = it },
                onSelect = { second = it; expanded2 = false },
            )
            Button(
                enabled = selected != null && second != null && !busy,
                onClick = {
                    val a = selected ?: return@Button
                    val b = second ?: return@Button
                    runJob(L.t("Merging…", "মার্জ হচ্ছে…")) {
                        "Saved ${FormatConverter.merge(a, b).displayName}"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(L.t("Merge", "মার্জ")) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileDropdown(
    label: String,
    items: List<RecordingItem>,
    selected: RecordingItem?,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onSelect: (RecordingItem) -> Unit,
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpanded) {
        OutlinedTextField(
            value = selected?.displayName ?: L.t("Select a recording", "একটি রেকর্ড বাছুন"),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            label = { Text(label) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item.displayName) }, onClick = { onSelect(item) })
            }
        }
    }
}
