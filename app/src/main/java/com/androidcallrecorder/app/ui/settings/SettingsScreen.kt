package com.androidcallrecorder.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidcallrecorder.app.data.AccentOption
import com.androidcallrecorder.app.data.AppLang
import com.androidcallrecorder.app.data.AppSettings
import com.androidcallrecorder.app.data.AudioContainer
import com.androidcallrecorder.app.data.AudioSourceOption
import com.androidcallrecorder.app.data.AutoDeleteAfter
import com.androidcallrecorder.app.data.AutoRecordMode
import com.androidcallrecorder.app.data.BackupSchedule
import com.androidcallrecorder.app.data.ChannelMode
import com.androidcallrecorder.app.data.ContactScope
import com.androidcallrecorder.app.data.DurationLimit
import com.androidcallrecorder.app.data.FolderLayout
import com.androidcallrecorder.app.data.OrientationMode
import com.androidcallrecorder.app.data.SettingsStore
import com.androidcallrecorder.app.data.SupportedApps
import com.androidcallrecorder.app.data.ThemeMode
import com.androidcallrecorder.app.data.VideoFps
import com.androidcallrecorder.app.data.VideoResolution
import com.androidcallrecorder.app.record.RootAccess
import com.androidcallrecorder.app.security.AppLock
import com.androidcallrecorder.app.ui.components.ChoiceRow
import com.androidcallrecorder.app.ui.components.SectionCard
import com.androidcallrecorder.app.ui.components.SettingSwitch
import com.androidcallrecorder.app.ui.components.SettingsNavRow
import com.androidcallrecorder.app.ui.i18n.L
import com.androidcallrecorder.app.ui.lock.PatternPad

private enum class SettingsPage {
    HUB, RECORDING, AUDIO, VIDEO, STORAGE, SECURITY, CLEANUP, BACKUP, EXTRAS, APPEARANCE, ABOUT,
}

@Composable
fun SettingsScreen(
    saveFolderLabel: String,
    permissionSummary: String,
    onPickFolder: () -> Unit,
    onBackup: () -> Unit,
    onShareBackup: () -> Unit,
    onRestore: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onResetSettings: () -> Unit,
    onRequireUnlock: (action: () -> Unit) -> Unit,
) {
    val stored by SettingsStore.settings.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(stored) }
    var page by remember { mutableStateOf(SettingsPage.HUB) }
    var savedFlash by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinMsg by remember { mutableStateOf("") }
    var patternDraft by remember { mutableStateOf(listOf<Int>()) }

    val dirty = draft != stored

    fun persist() {
        val apply = { SettingsStore.update { draft }; savedFlash = L.t("Settings saved", "সেটিংস সেভ হয়েছে") }
        if (stored.lockSettings && AppLock.hasUnlock()) onRequireUnlock(apply) else apply()
    }

    fun discard() {
        draft = stored
        savedFlash = ""
    }

    Column(Modifier.fillMaxSize()) {
        if (page != SettingsPage.HUB) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                IconButton(onClick = { page = SettingsPage.HUB }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, L.t("Back", "ফিরে"))
                }
                Text(pageTitle(page), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(
                L.t("Settings", "সেটিংস"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (savedFlash.isNotBlank() && page == SettingsPage.HUB) {
                Text(savedFlash, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
            }
            when (page) {
                SettingsPage.HUB -> Hub(draft) { page = it }
                SettingsPage.RECORDING -> RecordingPage(draft) { draft = it }
                SettingsPage.AUDIO -> AudioPage(draft) { draft = it }
                SettingsPage.VIDEO -> VideoPage(draft) { draft = it }
                SettingsPage.STORAGE -> StoragePage(draft, saveFolderLabel, onPickFolder) { draft = it }
                SettingsPage.SECURITY -> SecurityPage(
                    draft = draft,
                    onDraft = { draft = it },
                    pin = pin,
                    pinConfirm = pinConfirm,
                    pinMsg = pinMsg,
                    onPin = { pin = it },
                    onPinConfirm = { pinConfirm = it },
                    onPinMsg = { pinMsg = it },
                    patternDraft = patternDraft,
                    onPattern = { patternDraft = it },
                    onRequireUnlock = onRequireUnlock,
                )
                SettingsPage.CLEANUP -> CleanupPage(draft) { draft = it }
                SettingsPage.BACKUP -> BackupPage(draft, onBackup, onShareBackup, onRestore) { draft = it }
                SettingsPage.EXTRAS -> ExtrasPage(draft) { draft = it }
                SettingsPage.APPEARANCE -> AppearancePage(draft) { draft = it }
                SettingsPage.ABOUT -> AboutPage(
                    permissionSummary,
                    onOpenUsageAccess,
                    onOpenNotificationAccess,
                    onResetSettings,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (page != SettingsPage.HUB && page != SettingsPage.ABOUT) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = ::discard,
                    enabled = dirty,
                    modifier = Modifier.weight(1f),
                ) { Text(L.t("Discard", "বাতিল")) }
                Button(
                    onClick = ::persist,
                    enabled = dirty,
                    modifier = Modifier.weight(1f),
                ) { Text(L.t("Save", "সেভ")) }
            }
        }
    }
}

@Composable
private fun Hub(draft: AppSettings, open: (SettingsPage) -> Unit) {
    SectionCard {
        SettingsNavRow(
            Icons.Filled.PhonelinkRing,
            L.t("Recording", "রেকর্ডিং"),
            draft.autoRecord.name.lowercase(),
        ) { open(SettingsPage.RECORDING) }
        HorizontalDivider()
        SettingsNavRow(
            Icons.Filled.Mic,
            L.t("Audio quality", "অডিও কোয়ালিটি"),
            "${draft.container.label} · ${draft.bitRate / 1000} kbps",
        ) { open(SettingsPage.AUDIO) }
        HorizontalDivider()
        SettingsNavRow(
            Icons.Filled.Videocam,
            L.t("Video & screen", "ভিডিও ও স্ক্রিন"),
            "${draft.videoResolution.label} · ${draft.videoFps.fps} fps",
        ) { open(SettingsPage.VIDEO) }
        HorizontalDivider()
        SettingsNavRow(
            Icons.Filled.Folder,
            L.t("Storage", "স্টোরেজ"),
            draft.folderLayout.name.lowercase().replace('_', ' '),
        ) { open(SettingsPage.STORAGE) }
    }
    SectionCard {
        SettingsNavRow(
            Icons.Filled.Security,
            L.t("Security", "নিরাপত্তা"),
            if (AppLock.hasUnlock()) L.t("Lock on", "লক চালু") else L.t("No lock", "লক নেই"),
        ) { open(SettingsPage.SECURITY) }
        HorizontalDivider()
        SettingsNavRow(
            Icons.Filled.CleaningServices,
            L.t("Auto-cleanup", "অটো ক্লিনআপ"),
            draft.autoDelete.label,
        ) { open(SettingsPage.CLEANUP) }
        HorizontalDivider()
        SettingsNavRow(
            Icons.Filled.Backup,
            L.t("Backup", "ব্যাকআপ"),
            draft.backupSchedule.name.lowercase(),
        ) { open(SettingsPage.BACKUP) }
        HorizontalDivider()
        SettingsNavRow(
            Icons.Filled.Tune,
            L.t("Capture extras", "অতিরিক্ত ক্যাপচার"),
            L.t("Shake, beep, transcript", "ঝাঁকুনি, বীপ, ট্রান্সক্রিপ্ট"),
        ) { open(SettingsPage.EXTRAS) }
    }
    SectionCard {
        SettingsNavRow(
            Icons.Filled.ColorLens,
            L.t("Appearance", "চেহারা"),
            "${draft.themeMode.name.lowercase()} · ${if (draft.language == AppLang.BN) "বাংলা" else "English"}",
        ) { open(SettingsPage.APPEARANCE) }
        HorizontalDivider()
        SettingsNavRow(
            Icons.Filled.Info,
            L.t("About", "সম্পর্কে"),
            "1.0.0",
        ) { open(SettingsPage.ABOUT) }
    }
}

@Composable
private fun RecordingPage(draft: AppSettings, onDraft: (AppSettings) -> Unit) {
    SectionCard(L.t("Auto-record phone calls", "ফোন কল অটো-রেকর্ড")) {
        ChoiceRow(
            listOf(
                AutoRecordMode.ALL to L.t("Incoming and outgoing", "ইনকামিং ও আউটগোয়িং"),
                AutoRecordMode.INCOMING to L.t("Incoming only", "শুধু ইনকামিং"),
                AutoRecordMode.OUTGOING to L.t("Outgoing only", "শুধু আউটগোয়িং"),
                AutoRecordMode.OFF to L.t("Off", "বন্ধ"),
            ),
            draft.autoRecord,
        ) { onDraft(draft.copy(autoRecord = it)) }
    }
    SectionCard(
        L.t("Apps to watch", "যে অ্যাপগুলো দেখা হবে"),
        L.t(
            "Used when auto-record is on without a prompt. The audio/video prompt still appears for any detected call.",
            "প্রম্পট ছাড়া অটো-রেকর্ডের জন্য। যেকোনো কল ধরা পড়লে অডিও/ভিডিও প্রম্পট আসবেই।",
        ),
    ) {
        SupportedApps.all.forEach { app ->
            SettingSwitch(app.label, "", draft.enabledSources.contains(app.id)) { on ->
                val next = draft.enabledSources.toMutableSet()
                if (on) next += app.id else next -= app.id
                onDraft(draft.copy(enabledSources = next))
            }
        }
    }
    SectionCard(L.t("Contact rules", "কন্টাক্ট নিয়ম")) {
        ChoiceRow(
            listOf(
                ContactScope.ALL to L.t("Everyone", "সবাই"),
                ContactScope.SPECIFIC to L.t("Specific numbers", "নির্দিষ্ট নম্বর"),
                ContactScope.UNKNOWN_ONLY to L.t("Unknown only", "শুধু অজানা"),
                ContactScope.EXCLUDE to L.t("Use exclude list", "বাদ তালিকা"),
            ),
            draft.contactScope,
        ) { onDraft(draft.copy(contactScope = it)) }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.specificNumbers.joinToString(", "),
            onValueChange = { onDraft(draft.copy(specificNumbers = splitNums(it))) },
            label = { Text(L.t("Specific numbers", "নির্দিষ্ট নম্বর")) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.excludeNumbers.joinToString(", "),
            onValueChange = { onDraft(draft.copy(excludeNumbers = splitNums(it))) },
            label = { Text(L.t("Exclude list", "বাদ তালিকা")) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SectionCard(L.t("File names", "ফাইলের নাম")) {
        OutlinedTextField(
            value = draft.phoneNamePattern,
            onValueChange = { onDraft(draft.copy(phoneNamePattern = it)) },
            label = { Text(L.t("Phone calls", "ফোন কল")) },
            supportingText = { Text("{number} {time} {duration}") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.appNamePattern,
            onValueChange = { onDraft(draft.copy(appNamePattern = it)) },
            label = { Text(L.t("App calls", "অ্যাপ কল")) },
            supportingText = { Text("{app} {name} {number} {time} {duration}") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AudioPage(draft: AppSettings, onDraft: (AppSettings) -> Unit) {
    SectionCard(L.t("Source", "সোর্স")) {
        ChoiceRow(
            AudioSourceOption.entries.map { it to if (draft.language == AppLang.BN) it.labelBn else it.labelEn },
            draft.audioSource,
        ) { onDraft(draft.copy(audioSource = it)) }
    }
    SectionCard(L.t("Format", "ফরম্যাট")) {
        ChoiceRow(AudioContainer.entries.map { it to it.label }, draft.container) {
            onDraft(draft.copy(container = it))
        }
    }
    SectionCard(L.t("Bitrate", "বিটরেট")) {
        ChoiceRow(
            listOf(64_000, 96_000, 128_000, 192_000, 256_000).map { it to "${it / 1000} kbps" },
            draft.bitRate,
        ) { onDraft(draft.copy(bitRate = it)) }
    }
    SectionCard(L.t("Sample rate", "স্যাম্পল রেট")) {
        ChoiceRow(
            listOf(8_000, 16_000, 22_050, 44_100, 48_000).map { it to "${it / 1000} kHz" },
            draft.sampleRate,
        ) { onDraft(draft.copy(sampleRate = it)) }
    }
    SectionCard(L.t("Channels & length", "চ্যানেল ও দৈর্ঘ্য")) {
        ChoiceRow(
            listOf(ChannelMode.MONO to "Mono", ChannelMode.STEREO to "Stereo"),
            draft.channels,
        ) { onDraft(draft.copy(channels = it)) }
        Spacer(Modifier.height(8.dp))
        ChoiceRow(DurationLimit.entries.map { it to it.label }, draft.durationLimit) {
            onDraft(draft.copy(durationLimit = it))
        }
    }
}

@Composable
private fun VideoPage(draft: AppSettings, onDraft: (AppSettings) -> Unit) {
    SectionCard(L.t("Resolution", "রেজোলিউশন")) {
        ChoiceRow(VideoResolution.entries.map { it to it.label }, draft.videoResolution) {
            onDraft(draft.copy(videoResolution = it))
        }
    }
    SectionCard("FPS") {
        ChoiceRow(VideoFps.entries.map { it to "${it.fps} fps" }, draft.videoFps) {
            onDraft(draft.copy(videoFps = it))
        }
    }
    SectionCard(L.t("Orientation", "অরিয়েন্টেশন")) {
        ChoiceRow(OrientationMode.entries.map { it to it.name.lowercase() }, draft.orientation) {
            onDraft(draft.copy(orientation = it))
        }
    }
    SectionCard {
        SettingSwitch(
            L.t("Record audio with screen", "স্ক্রিনের সাথে অডিও"),
            "",
            draft.screenWithAudio,
        ) { onDraft(draft.copy(screenWithAudio = it)) }
        SettingSwitch(
            L.t("Video call as audio-only", "ভিডিও কল শুধু অডিও"),
            "",
            draft.videoCallAsAudio,
        ) { onDraft(draft.copy(videoCallAsAudio = it)) }
    }
}

@Composable
private fun StoragePage(
    draft: AppSettings,
    saveFolderLabel: String,
    onPickFolder: () -> Unit,
    onDraft: (AppSettings) -> Unit,
) {
    SectionCard(L.t("Save location", "সেভ লোকেশন"), saveFolderLabel) {
        OutlinedButton(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) {
            Text(L.t("Choose folder", "ফোল্ডার বাছুন"))
        }
    }
    SectionCard(L.t("Folder structure", "ফোল্ডার গঠন")) {
        ChoiceRow(
            listOf(
                FolderLayout.FLAT to L.t("Flat", "এক ফোল্ডার"),
                FolderLayout.BY_DATE to L.t("By date", "তারিখ অনুযায়ী"),
                FolderLayout.BY_CONTACT to L.t("By contact", "কন্টাক্ট অনুযায়ী"),
                FolderLayout.BY_APP to L.t("By app", "অ্যাপ অনুযায়ী"),
            ),
            draft.folderLayout,
        ) { onDraft(draft.copy(folderLayout = it)) }
    }
}

@Composable
private fun SecurityPage(
    draft: AppSettings,
    onDraft: (AppSettings) -> Unit,
    pin: String,
    pinConfirm: String,
    pinMsg: String,
    onPin: (String) -> Unit,
    onPinConfirm: (String) -> Unit,
    onPinMsg: (String) -> Unit,
    patternDraft: List<Int>,
    onPattern: (List<Int>) -> Unit,
    onRequireUnlock: (action: () -> Unit) -> Unit,
) {
    SectionCard(L.t("Lock scope", "লকের পরিধি")) {
        SettingSwitch(L.t("Lock app on open", "অ্যাপ খুলতে লক"), "", draft.lockOnOpen) {
            onDraft(draft.copy(lockOnOpen = it))
        }
        SettingSwitch(L.t("Lock record list", "লিস্ট লক"), "", draft.lockList) {
            onDraft(draft.copy(lockList = it))
        }
        SettingSwitch(L.t("Lock settings", "সেটিংস লক"), "", draft.lockSettings) {
            onDraft(draft.copy(lockSettings = it))
        }
        SettingSwitch(L.t("Lock delete", "ডিলিট লক"), "", draft.lockDelete) {
            onDraft(draft.copy(lockDelete = it))
        }
        SettingSwitch(L.t("Lock share", "শেয়ার লক"), "", draft.lockShare) {
            onDraft(draft.copy(lockShare = it))
        }
        SettingSwitch(L.t("Fingerprint / face", "ফিঙ্গারপ্রিন্ট / ফেস"), "", draft.useBiometric) {
            onDraft(draft.copy(useBiometric = it))
        }
    }
    SectionCard(L.t("PIN", "PIN")) {
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8) onPin(it.filter { c -> c.isDigit() }) },
            label = { Text(L.t("New PIN", "নতুন PIN")) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pinConfirm,
            onValueChange = { if (it.length <= 8) onPinConfirm(it.filter { c -> c.isDigit() }) },
            label = { Text(L.t("Confirm PIN", "PIN নিশ্চিত")) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        if (pinMsg.isNotBlank()) {
            Text(pinMsg, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                when {
                    pin.length < 4 -> onPinMsg(L.t("Use at least 4 digits", "কমপক্ষে ৪ ডিজিট"))
                    pin != pinConfirm -> onPinMsg(L.t("PINs do not match", "PIN মিলেনি"))
                    else -> {
                        AppLock.setPin(pin)
                        onPin(""); onPinConfirm("")
                        onPinMsg(L.t("PIN saved", "PIN সেভ হয়েছে"))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(L.t("Save PIN", "PIN সেভ")) }
    }
    SectionCard(L.t("Pattern", "প্যাটার্ন"), L.t("Connect at least 4 dots, then save", "কমপক্ষে ৪টি ডট")) {
        PatternPad(onComplete = onPattern)
        Button(
            onClick = {
                if (patternDraft.size < 4) onPinMsg(L.t("Pattern too short", "প্যাটার্ন ছোট"))
                else {
                    AppLock.setPattern(patternDraft)
                    onPinMsg(L.t("Pattern saved", "প্যাটার্ন সেভ হয়েছে"))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(L.t("Save pattern", "প্যাটার্ন সেভ")) }
        OutlinedButton(
            onClick = {
                val clear = { AppLock.clearPin(); onPinMsg(L.t("Lock removed", "লক সরানো হয়েছে")) }
                if (AppLock.hasUnlock()) onRequireUnlock(clear) else clear()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(L.t("Remove lock", "লক সরান")) }
    }
}

@Composable
private fun CleanupPage(draft: AppSettings, onDraft: (AppSettings) -> Unit) {
    SectionCard(L.t("Delete older than", "এর চেয়ে পুরনো মুছুন")) {
        ChoiceRow(AutoDeleteAfter.entries.map { it to it.label }, draft.autoDelete) {
            onDraft(draft.copy(autoDelete = it))
        }
    }
    SectionCard {
        SettingSwitch(L.t("Keep starred", "স্টার রাখুন"), "", draft.keepStarred) {
            onDraft(draft.copy(keepStarred = it))
        }
        SettingSwitch(L.t("Keep locked", "লক রাখুন"), "", draft.keepLocked) {
            onDraft(draft.copy(keepLocked = it))
        }
        SettingSwitch(L.t("Warn before delete", "মুছার আগে জিজ্ঞাসা"), "", draft.warnBeforeDelete) {
            onDraft(draft.copy(warnBeforeDelete = it))
        }
    }
}

@Composable
private fun BackupPage(
    draft: AppSettings,
    onBackup: () -> Unit,
    onShareBackup: () -> Unit,
    onRestore: () -> Unit,
    onDraft: (AppSettings) -> Unit,
) {
    SectionCard(L.t("Actions", "কাজ")) {
        Button(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
            Text(L.t("Save ZIP backup", "ZIP ব্যাকআপ সেভ"))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onShareBackup, modifier = Modifier.fillMaxWidth()) {
            Text(L.t("Share / Google Drive", "শেয়ার / Google Drive"))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
            Text(L.t("Restore from ZIP", "ZIP থেকে রিস্টোর"))
        }
    }
    SectionCard(L.t("Auto-backup reminder", "অটো ব্যাকআপ")) {
        ChoiceRow(BackupSchedule.entries.map { it to it.name.lowercase() }, draft.backupSchedule) {
            onDraft(draft.copy(backupSchedule = it))
        }
    }
}

@Composable
private fun ExtrasPage(draft: AppSettings, onDraft: (AppSettings) -> Unit) {
    SectionCard {
        SettingSwitch(
            L.t("Pause button while recording", "রেকর্ডে পজ বাটন"),
            "",
            draft.pauseButtonEnabled,
        ) { onDraft(draft.copy(pauseButtonEnabled = it)) }
        SettingSwitch(
            L.t("Mute my mic (best effort)", "আমার মাইক মিউট"),
            "",
            draft.muteMyMic,
        ) { onDraft(draft.copy(muteMyMic = it)) }
        SettingSwitch(
            L.t("Shake to start / stop", "ঝাঁকিয়ে শুরু/বন্ধ"),
            "",
            draft.shakeToRecord,
        ) { onDraft(draft.copy(shakeToRecord = it)) }
        SettingSwitch(
            L.t("Consent beep every 15s", "প্রতি ১৫ সেকেন্ডে সম্মতি বীপ"),
            "",
            draft.consentBeep,
        ) { onDraft(draft.copy(consentBeep = it)) }
        SettingSwitch(
            L.t("Auto-transcription", "অটো ট্রান্সক্রিপ্ট"),
            "",
            draft.autoTranscribe,
        ) { onDraft(draft.copy(autoTranscribe = it)) }
        SettingSwitch(
            L.t("Rooted mode", "রুটেড মোড"),
            if (RootAccess.isDeviceRooted()) {
                L.t("Retries public VOICE_CALL only", "শুধু পাবলিক VOICE_CALL")
            } else {
                L.t("No root detected", "রুট নেই")
            },
            draft.rootMode,
        ) { onDraft(draft.copy(rootMode = it)) }
        SettingSwitch(
            L.t("Watch calling apps", "কলিং অ্যাপ দেখুন"),
            "",
            draft.watchCallingApps,
        ) { onDraft(draft.copy(watchCallingApps = it)) }
        SettingSwitch(
            L.t("Ask audio or video on each call", "প্রতি কলে অডিও/ভিডিও জিজ্ঞাসা"),
            L.t(
                "Shows on every detected call: phone, WhatsApp, Telegram, and any other call notification. Voice calls record audio only unless you pick video.",
                "যেকোনো ধরা পড়া কলে প্রম্পট: ফোন, WhatsApp, Telegram ও অন্য কল নোটিফিকেশন। ভয়েস কলে শুধু অডিও।",
            ),
            draft.promptOnCall,
        ) { onDraft(draft.copy(promptOnCall = it)) }
    }
}

@Composable
private fun AppearancePage(draft: AppSettings, onDraft: (AppSettings) -> Unit) {
    SectionCard(L.t("Theme", "থিম")) {
        ChoiceRow(
            listOf(
                ThemeMode.LIGHT to L.t("Light", "লাইট"),
                ThemeMode.DARK to L.t("Dark", "ডার্ক"),
                ThemeMode.SYSTEM to L.t("System", "সিস্টেম"),
            ),
            draft.themeMode,
        ) { onDraft(draft.copy(themeMode = it)) }
    }
    SectionCard(L.t("Accent", "অ্যাকসেন্ট")) {
        ChoiceRow(
            AccentOption.entries.map { it to it.name.lowercase() },
            draft.accent,
        ) { onDraft(draft.copy(accent = it)) }
    }
    SectionCard(L.t("Language", "ভাষা")) {
        ChoiceRow(
            listOf(AppLang.EN to "English", AppLang.BN to "বাংলা"),
            draft.language,
        ) { onDraft(draft.copy(language = it)) }
    }
}

@Composable
private fun AboutPage(
    permissionSummary: String,
    onOpenUsageAccess: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onResetSettings: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }
    SectionCard("Call Recorder", "1.0.0") {
        Text(permissionSummary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenUsageAccess, modifier = Modifier.fillMaxWidth()) {
            Text(L.t("Open usage access", "ইউজেজ অ্যাকসেস খুলুন"))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenNotificationAccess, modifier = Modifier.fillMaxWidth()) {
            Text(L.t("Enable call notification access", "কল নোটিফিকেশন অ্যাকসেস"))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            L.t(
                "Turn on notification access so any calling app can show Record audio or Record video when a call is detected. Voice calls use audio only unless you choose video.",
                "যেকোনো কলিং অ্যাপের কল ধরতে নোটিফিকেশন অ্যাকসেস চালু করুন। ভয়েস কলে শুধু অডিও।",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
            Text(L.t("Reset all settings", "সব সেটিংস রিসেট"))
        }
        if (confirmReset) {
            Text(
                L.t("This restores defaults. Your PIN stays. Recordings are not deleted.", "ডিফল্ট ফিরে আসবে। PIN থাকবে। রেকর্ড মুছবে না।"),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = { confirmReset = false; onResetSettings() }, modifier = Modifier.fillMaxWidth()) {
                Text(L.t("Reset now", "এখন রিসেট"))
            }
        }
    }
}

private fun pageTitle(page: SettingsPage): String = when (page) {
    SettingsPage.HUB -> L.t("Settings", "সেটিংস")
    SettingsPage.RECORDING -> L.t("Recording", "রেকর্ডিং")
    SettingsPage.AUDIO -> L.t("Audio quality", "অডিও কোয়ালিটি")
    SettingsPage.VIDEO -> L.t("Video & screen", "ভিডিও ও স্ক্রিন")
    SettingsPage.STORAGE -> L.t("Storage", "স্টোরেজ")
    SettingsPage.SECURITY -> L.t("Security", "নিরাপত্তা")
    SettingsPage.CLEANUP -> L.t("Auto-cleanup", "অটো ক্লিনআপ")
    SettingsPage.BACKUP -> L.t("Backup", "ব্যাকআপ")
    SettingsPage.EXTRAS -> L.t("Capture extras", "অতিরিক্ত")
    SettingsPage.APPEARANCE -> L.t("Appearance", "চেহারা")
    SettingsPage.ABOUT -> L.t("About", "সম্পর্কে")
}

private fun splitNums(raw: String) =
    raw.split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }
