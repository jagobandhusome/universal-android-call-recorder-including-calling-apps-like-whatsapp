package com.androidcallrecorder.app.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import com.androidcallrecorder.app.data.SettingsStore
import java.security.MessageDigest
import java.security.SecureRandom

object AppLock {
    fun hasPin(): Boolean = SettingsStore.current().pinHash.isNotBlank()

    fun hasUnlock(): Boolean = hasPin() || SettingsStore.current().patternHash.isNotBlank()

    fun setPattern(cells: List<Int>) {
        val salt = randomSalt()
        val hash = hashPin(cells.joinToString("-"), salt)
        SettingsStore.update { it.copy(patternHash = "$salt:$hash") }
    }

    fun verifyPattern(cells: List<Int>): Boolean {
        val stored = SettingsStore.current().patternHash
        val parts = stored.split(":")
        if (parts.size != 2) return false
        return hashPin(cells.joinToString("-"), parts[0]) == parts[1]
    }

    fun setPin(pin: String) {
        val salt = randomSalt()
        val hash = hashPin(pin, salt)
        SettingsStore.update { it.copy(pinHash = "$salt:$hash") }
    }

    fun clearPin() {
        SettingsStore.update {
            it.copy(
                pinHash = "",
                patternHash = "",
                lockOnOpen = false,
                lockList = false,
                lockSettings = false,
                lockDelete = false,
                lockShare = false,
                useBiometric = false,
            )
        }
    }

    fun verifyPin(pin: String): Boolean {
        val stored = SettingsStore.current().pinHash
        val parts = stored.split(":")
        if (parts.size != 2) return false
        return hashPin(pin, parts[0]) == parts[1]
    }

    fun canUseBiometric(context: Context): Boolean {
        val mgr = BiometricManager.from(context)
        val flags = if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
        return mgr.canAuthenticate(flags) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun randomSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPin(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest("$salt:$pin".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
