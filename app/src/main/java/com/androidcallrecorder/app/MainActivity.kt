package com.androidcallrecorder.app

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidcallrecorder.app.data.RecordMode
import com.androidcallrecorder.app.data.RecordingItem
import com.androidcallrecorder.app.data.RecordingStore
import com.androidcallrecorder.app.data.SettingsStore
import com.androidcallrecorder.app.data.StorageHelper
import com.androidcallrecorder.app.data.ThemeMode
import com.androidcallrecorder.app.record.AppWatcherService
import com.androidcallrecorder.app.record.BackupHelper
import com.androidcallrecorder.app.record.PdfExporter
import com.androidcallrecorder.app.record.RecordingService
import com.androidcallrecorder.app.record.RecordingSession
import com.androidcallrecorder.app.record.StorageGuard
import com.androidcallrecorder.app.security.AppLock
import com.androidcallrecorder.app.ui.converter.ConverterScreen
import com.androidcallrecorder.app.ui.home.HomeScreen
import com.androidcallrecorder.app.ui.lock.LockScreen
import com.androidcallrecorder.app.ui.onboarding.DisclaimerScreen
import com.androidcallrecorder.app.ui.records.RecordsScreen
import com.androidcallrecorder.app.ui.settings.SettingsScreen
import com.androidcallrecorder.app.ui.theme.AppScreenBackground
import com.androidcallrecorder.app.ui.theme.CallRecorderTheme
import com.androidcallrecorder.app.ui.theme.IvacBlue
import com.androidcallrecorder.app.ui.theme.accentColor
import java.io.File

class MainActivity : FragmentActivity() {
    private var pendingMode: RecordMode = RecordMode.AUDIO
    private var pendingVoipApp: String = "app"
    private var pendingPdf: RecordingItem? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* UI reads permission state live */ }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            RecordingService.start(
                context = this,
                mode = pendingMode,
                source = pendingVoipApp,
                resultCode = result.resultCode,
                resultData = result.data,
            )
        } else {
            Toast.makeText(this, "Screen audio denied — using microphone / speaker", Toast.LENGTH_LONG).show()
            RecordingService.start(this, RecordMode.AUDIO, pendingVoipApp)
        }
    }

    private val folderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        SettingsStore.update { it.copy(saveTreeUri = uri.toString()) }
    }

    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val n = BackupHelper.exportZip(this, uri)
            Toast.makeText(this, "Backed up $n files", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val pdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val item = pendingPdf ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        try {
            PdfExporter.write(this, item, uri)
            Toast.makeText(this, "PDF saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "PDF failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val n = BackupHelper.restoreZip(this, uri)
            Toast.makeText(this, "Restored $n files", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.init(this)
        RecordingStore.init(this)
        if (SettingsStore.current().watchCallingApps) {
            AppWatcherService.start(this)
        }
        handleVoipExtra(intent)
        setContent {
            val settings by SettingsStore.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> null
            }
            CallRecorderTheme(darkTheme = dark, accent = accentColor(settings.accent)) {
                AppShell()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleVoipExtra(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun handleVoipExtra(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_VOIP, false) == true) {
            pendingVoipApp = intent.getStringExtra(EXTRA_VOIP_APP) ?: "app"
            com.androidcallrecorder.app.record.CallPrompt.show(this, source = pendingVoipApp)
        }
    }

    private fun requestPermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
        )
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(needed.toTypedArray())
        if (!hasUsageAccess()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestCaptureOrStart(mode: RecordMode, source: String) {
        pendingMode = mode
        pendingVoipApp = source
        val needsProjection = mode != RecordMode.AUDIO ||
            SettingsStore.current().voiceTrack == com.androidcallrecorder.app.data.VoiceTrack.REMOTE_ONLY
        if (needsProjection && mode != RecordMode.AUDIO) {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions()
        } else {
            RecordingService.start(this, mode, source)
        }
    }

    private fun shareBackupZip() {
        try {
            val file = File(cacheDir, "call-recorder-backup.zip")
            val n = BackupHelper.exportToFile(this, file)
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType("application/zip")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    "Share backup ($n files)",
                ),
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun permissionSummary(): String {
        fun ok(p: String) =
            if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED) "granted" else "missing"
        return "Mic ${ok(Manifest.permission.RECORD_AUDIO)} · Phone ${ok(Manifest.permission.READ_PHONE_STATE)} · " +
            "Contacts ${ok(Manifest.permission.READ_CONTACTS)} · Usage ${if (hasUsageAccess()) "granted" else "missing"}"
    }

    private fun requestDelete(
        items: List<RecordingItem>,
        settings: com.androidcallrecorder.app.data.AppSettings,
        setPending: (List<RecordingItem>?) -> Unit,
        setGate: (UnlockGate) -> Unit,
    ) {
        val run = {
            items.filter { !it.locked }.forEach { RecordingStore.delete(it.id) }
        }
        val afterWarn = {
            if (settings.lockDelete && AppLock.hasUnlock()) setGate(UnlockGate("Confirm delete", run)) else run()
        }
        if (settings.warnBeforeDelete) setPending(items) else afterWarn()
    }

    private fun share(item: RecordingItem) {
        val file = File(item.filePath)
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(StorageHelper.mimeFor(file))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Share recording"))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppShell() {
        val settings by SettingsStore.settings.collectAsStateWithLifecycle()
        var selected by remember { mutableStateOf(Tab.HOME) }
        var unlocked by remember { mutableStateOf(!settings.lockOnOpen || !AppLock.hasUnlock()) }
        var listUnlocked by remember { mutableStateOf(!settings.lockList) }
        var convertId by remember { mutableStateOf<String?>(null) }
        var pendingDelete by remember { mutableStateOf<List<RecordingItem>?>(null) }
        var gate by remember { mutableStateOf<UnlockGate?>(null) }
        val session by RecordingSession.ui.collectAsStateWithLifecycle()
        if (!settings.disclaimerAccepted) {
            DisclaimerScreen { }
            return
        }
        val darkChrome = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val topBarColor = if (darkChrome) MaterialTheme.colorScheme.surface else IvacBlue
        val topContent = if (darkChrome) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.White

        if (!unlocked) {
            LockScreen { unlocked = true }
            return
        }
        val pending = gate
        if (pending != null) {
            LockScreen(title = pending.title) {
                val action = pending.action
                gate = null
                action()
            }
            return
        }

        pendingDelete?.let { doomed ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete ${doomed.size} item(s)?") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDelete = null
                        requestDelete(doomed, settings.copy(warnBeforeDelete = false), { pendingDelete = it }, { gate = it })
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (session.active) "Recording · ${session.source}" else "Call Recorder",
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                SettingsStore.update { current ->
                                    current.copy(
                                        themeMode = if (current.themeMode == ThemeMode.DARK) {
                                            ThemeMode.LIGHT
                                        } else {
                                            ThemeMode.DARK
                                        },
                                    )
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (darkChrome) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "Toggle light and dark theme",
                                tint = topContent,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = topBarColor,
                        titleContentColor = topContent,
                        actionIconContentColor = topContent,
                    ),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selected == tab,
                            onClick = {
                                if (tab == Tab.RECORDS && settings.lockList && AppLock.hasUnlock() && !listUnlocked) {
                                    gate = UnlockGate("Unlock records") {
                                        listUnlocked = true
                                        selected = Tab.RECORDS
                                    }
                                } else {
                                    selected = tab
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            AppScreenBackground(Modifier.padding(padding)) {
                when (selected) {
                    Tab.HOME -> HomeScreen(
                        onToggleRec = {
                            if (RecordingSession.ui.value.active) {
                                RecordingService.stop(this)
                            } else {
                                val mode = if (settings.videoCallAsAudio) {
                                    RecordMode.VIDEO_AS_AUDIO
                                } else {
                                    settings.recordMode
                                }
                                requestCaptureOrStart(mode, if (mode == RecordMode.AUDIO) "phone" else "app")
                            }
                        },
                        onStartScreen = { requestCaptureOrStart(RecordMode.SCREEN, "screen") },
                        onOpenConverter = { selected = Tab.CONVERTER },
                        onBackup = { backupLauncher.launch("call-recorder-backup.zip") },
                        onOpenRecords = { selected = Tab.RECORDS },
                        onPause = { RecordingService.pause(this) },
                        onResume = { RecordingService.resume(this) },
                        onStop = { RecordingService.stop(this) },
                        onMemo = { RecordingService.start(this, RecordMode.MEMO, "memo") },
                        onStartVoip = { appId ->
                            requestPermissions()
                            com.androidcallrecorder.app.record.CallPrompt.show(this, source = appId)
                        },
                        onCompress = {
                            val n = StorageGuard.compressOldest()
                            Toast.makeText(this, "Compressed $n file(s)", Toast.LENGTH_SHORT).show()
                        },
                    )
                    Tab.RECORDS -> RecordsScreen(
                        onShare = { item ->
                            val go = { share(item) }
                            if (settings.lockShare && AppLock.hasUnlock()) {
                                gate = UnlockGate("Unlock share", go)
                            } else {
                                go()
                            }
                        },
                        onDelete = { item -> requestDelete(listOf(item), settings, { pendingDelete = it }, { gate = it }) },
                        onConvert = { item ->
                            convertId = item.id
                            selected = Tab.CONVERTER
                        },
                        onBulkDelete = { list -> requestDelete(list, settings, { pendingDelete = it }, { gate = it }) },
                        onExportPdf = { item ->
                            pendingPdf = item
                            pdfLauncher.launch(item.displayName.substringBeforeLast('.') + ".pdf")
                        },
                    )
                    Tab.CONVERTER -> ConverterScreen(preselectedId = convertId)
                    Tab.SETTINGS -> SettingsScreen(
                        saveFolderLabel = settings.saveTreeUri.ifBlank {
                            StorageHelper.defaultDir(this).absolutePath
                        },
                        permissionSummary = permissionSummary(),
                        onPickFolder = { folderLauncher.launch(null) },
                        onBackup = { backupLauncher.launch("call-recorder-backup.zip") },
                        onShareBackup = { shareBackupZip() },
                        onRestore = { restoreLauncher.launch(arrayOf("application/zip", "*/*")) },
                        onOpenUsageAccess = {
                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                        onOpenNotificationAccess = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onResetSettings = {
                            SettingsStore.reset()
                            Toast.makeText(this, "Settings reset", Toast.LENGTH_SHORT).show()
                        },
                        onRequireUnlock = { action ->
                            gate = UnlockGate("Unlock settings", action)
                        },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_START_VOIP = "start_voip"
        const val EXTRA_VOIP_APP = "voip_app"
    }
}

private data class UnlockGate(val title: String, val action: () -> Unit)

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    RECORDS("Records", Icons.AutoMirrored.Filled.List),
    CONVERTER("Convert", Icons.Filled.SwapHoriz),
    SETTINGS("Settings", Icons.Filled.Settings),
}
