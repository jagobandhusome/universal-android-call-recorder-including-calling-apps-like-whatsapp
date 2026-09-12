package com.androidcallrecorder.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidcallrecorder.app.data.FileNamer
import com.androidcallrecorder.app.data.RecordMode
import com.androidcallrecorder.app.data.RecordingStore
import com.androidcallrecorder.app.data.SettingsStore
import com.androidcallrecorder.app.data.SupportedApps
import com.androidcallrecorder.app.data.VoiceTrack
import com.androidcallrecorder.app.record.RecordingSession
import com.androidcallrecorder.app.record.StorageGuard
import com.androidcallrecorder.app.ui.components.ChipRow
import com.androidcallrecorder.app.ui.components.SectionCard
import com.androidcallrecorder.app.ui.i18n.L
import com.androidcallrecorder.app.ui.theme.IvacCoral
import java.util.Calendar
import java.util.Locale

@Composable
fun HomeScreen(
    onToggleRec: () -> Unit,
    onStartScreen: () -> Unit,
    onOpenConverter: () -> Unit,
    onBackup: () -> Unit,
    onOpenRecords: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onMemo: () -> Unit,
    onStartVoip: (String) -> Unit,
    onCompress: () -> Unit,
) {
    val session by RecordingSession.ui.collectAsStateWithLifecycle()
    val settings by SettingsStore.settings.collectAsStateWithLifecycle()
    val items by RecordingStore.items.collectAsStateWithLifecycle()
    val startOfDay = rememberStartOfDay()
    val todayCount = items.count { it.startedAt >= startOfDay && !it.missed }
    val used = items.sumOf { it.sizeBytes }

    val status = when {
        session.active && session.paused -> L.t("Paused", "বিরতি")
        session.active -> L.t("Recording…", "রেকর্ড হচ্ছে…")
        session.message.isNotBlank() && !session.active -> L.t("Idle", "নিষ্ক্রিয়")
        else -> L.t("Ready", "প্রস্তুত")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (StorageGuard.isLow()) {
            SectionCard(L.t("Low storage", "স্টোরেজ কম"), L.t("${StorageGuard.freeMb()} MB free. Compress old files?", "খালি ${StorageGuard.freeMb()} MB। পুরনো ফাইল কমপ্রেস করবেন?")) {
                FilledTonalButton(onClick = onCompress, modifier = Modifier.fillMaxWidth()) {
                    Text(L.t("Auto-compress oldest", "পুরনো ফাইল কমপ্রেস"))
                }
            }
        }

        SectionCard(status, session.message.ifBlank { L.t("Notification stays on while recording.", "রেকর্ড চলাকালে নোটিফিকেশন দেখা যাবে।") }) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(
                            if (session.active) IvacCoral else MaterialTheme.colorScheme.primary,
                            CircleShape,
                        )
                        .clickable(onClick = onToggleRec),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (session.active) "STOP" else "REC",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (session.active) FileNamer.formatDuration(session.elapsedMs) else "00m00s",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (session.active && settings.pauseButtonEnabled) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = { if (session.paused) onResume() else onPause() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(if (session.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null)
                        Text(if (session.paused) " Resume" else " Pause")
                    }
                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = IvacCoral),
                    ) {
                        Icon(Icons.Filled.Stop, null)
                        Text(" Stop")
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(L.t("Today", "আজ"), "$todayCount", Modifier.weight(1f))
            StatCard(L.t("Storage", "স্টোরেজ"), formatMb(used), Modifier.weight(1f))
        }

        SectionCard(L.t("Quick actions", "দ্রুত কাজ")) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTile(L.t("Screen", "স্ক্রিন"), onStartScreen, Modifier.weight(1f))
                ActionTile(L.t("Memo", "মেমো"), onMemo, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTile(L.t("Convert", "কনভার্ট"), onOpenConverter, Modifier.weight(1f))
                ActionTile(L.t("Backup", "ব্যাকআপ"), onBackup, Modifier.weight(1f))
            }
        }

        SectionCard(
            L.t("WhatsApp & other apps", "WhatsApp ও অন্য অ্যাপ"),
            L.t(
                "Any detected call asks Record audio or Record video. Audio is sound only.",
                "যেকোনো ধরা পড়া কলে অডিও বা ভিডিও জিজ্ঞাসা। অডিও শুধু শব্দ।",
            ),
        ) {
            ChipRow(
                options = SupportedApps.all.filter { it.id != "phone" }.map { it.id to it.label },
                selected = "",
                onSelect = onStartVoip,
            )
        }

        if (session.liveTranscript.isNotBlank()) {
            SectionCard(L.t("Live transcript", "লাইভ ট্রান্সক্রিপ্ট"), session.liveTranscript) {}
        }

        SectionCard(L.t("Voice focus", "ভয়েস ফোকাস")) {
            com.androidcallrecorder.app.ui.components.ChoiceRow(
                options = listOf(
                    VoiceTrack.BOTH to L.t("Both sides", "দুই পক্ষ"),
                    VoiceTrack.LOCAL_ONLY to L.t("My voice", "আমার কণ্ঠ"),
                    VoiceTrack.REMOTE_ONLY to L.t("Other side", "অন্য পক্ষ"),
                ),
                selected = settings.voiceTrack,
                onSelect = { track -> SettingsStore.update { it.copy(voiceTrack = track) } },
            )
        }

        SectionCard(L.t("Recent recordings", "সাম্প্রতিক রেকর্ড")) {
            val recent = items.filter { !it.missed }.take(5)
            if (recent.isEmpty()) {
                Text(L.t("Nothing yet.", "এখনো কিছু নেই।"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                recent.forEach { item ->
                    Text(
                        item.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenRecords)
                            .padding(vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${item.source} · ${FileNamer.formatDuration(item.durationMs)} · ${formatMb(item.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
    ) { Text(label) }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Box(modifier) {
        SectionCard(title) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun rememberStartOfDay(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun formatMb(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    return String.format(Locale.US, "%.1f MB", kb / 1024.0)
}
