package com.androidcallrecorder.app.record

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.androidcallrecorder.app.data.AutoRecordMode
import com.androidcallrecorder.app.data.CallDirection
import com.androidcallrecorder.app.data.ContactScope
import com.androidcallrecorder.app.data.MediaKind
import com.androidcallrecorder.app.data.RecordMode
import com.androidcallrecorder.app.data.RecordingItem
import com.androidcallrecorder.app.data.RecordingStore
import com.androidcallrecorder.app.data.SettingsStore

class PhoneStateMonitor(private val context: Context) {
    private val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var ringing = false
    private var pendingNumber = ""
    private var callback: Any? = null

    fun start() {
        if (!has(Manifest.permission.READ_PHONE_STATE)) return
        if (Build.VERSION.SDK_INT >= 31) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handle(state, pendingNumber)
                }
            }
            callback = cb
            tm.registerTelephonyCallback(context.mainExecutor, cb)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    pendingNumber = phoneNumber.orEmpty()
                    handle(state, pendingNumber)
                }
            }
            callback = listener
            @Suppress("DEPRECATION")
            tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= 31) {
            (callback as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
        } else {
            @Suppress("DEPRECATION")
            (callback as? PhoneStateListener)?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
        callback = null
    }

    private fun handle(state: Int, number: String) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                ringing = true
                pendingNumber = number
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (lastState != TelephonyManager.CALL_STATE_OFFHOOK) {
                    val direction = if (ringing) CallDirection.INCOMING else CallDirection.OUTGOING
                    maybeRecord(direction, pendingNumber.ifBlank { number })
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (ringing && lastState == TelephonyManager.CALL_STATE_RINGING) {
                    addMissed(pendingNumber.ifBlank { number })
                }
                if (RecordingSession.ui.value.active && RecordingSession.ui.value.source == "phone") {
                    RecordingService.stop(context)
                }
                ringing = false
                pendingNumber = ""
            }
        }
        lastState = state
    }

    private fun maybeRecord(direction: CallDirection, number: String) {
        val settings = SettingsStore.current()
        if (RecordingSession.ui.value.active) return
        if (settings.excludeNumbers.any { digits(it) == digits(number) && digits(it).isNotEmpty() }) return
        val saved = isSavedContact(number)
        when (settings.contactScope) {
            ContactScope.ALL -> Unit
            ContactScope.UNKNOWN_ONLY -> if (saved) return
            ContactScope.SPECIFIC -> {
                val match = settings.specificNumbers.any { digits(it) == digits(number) && digits(it).isNotEmpty() }
                if (!match) return
            }
            ContactScope.EXCLUDE -> Unit
        }
        val name = contactName(number)
        if (!has(Manifest.permission.RECORD_AUDIO)) return
        if (settings.promptOnCall) {
            CallPrompt.show(
                context = context,
                source = "phone",
                contact = name,
                number = number,
                direction = direction,
            )
            return
        }
        if (!settings.enabledSources.contains("phone")) return
        when (settings.autoRecord) {
            AutoRecordMode.OFF -> return
            AutoRecordMode.INCOMING -> if (direction != CallDirection.INCOMING) return
            AutoRecordMode.OUTGOING -> if (direction != CallDirection.OUTGOING) return
            AutoRecordMode.ALL -> Unit
        }
        RecordingService.start(
            context = context,
            mode = RecordMode.AUDIO,
            source = "phone",
            contact = name,
            number = number,
            direction = direction,
            voice = settings.voiceTrack,
        )
    }

    private fun addMissed(number: String) {
        val name = contactName(number)
        RecordingStore.add(
            RecordingItem(
                id = RecordingStore.newId(),
                filePath = "",
                displayName = "Missed ${name.ifBlank { number.ifBlank { "unknown" } }}",
                source = "phone",
                contact = name,
                number = number,
                direction = CallDirection.INCOMING,
                startedAt = System.currentTimeMillis(),
                durationMs = 0,
                sizeBytes = 0,
                kind = MediaKind.AUDIO,
                format = "",
                missed = true,
            ),
        )
    }

    private fun isSavedContact(number: String): Boolean = contactName(number).isNotBlank()

    private fun contactName(number: String): String {
        if (number.isBlank() || !has(Manifest.permission.READ_CONTACTS)) return ""
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(number)
            .build()
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )
            if (cursor != null && cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        } catch (_: Exception) {
            ""
        } finally {
            cursor?.close()
        }
    }

    private fun digits(raw: String) = raw.filter { it.isDigit() }

    private fun has(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
