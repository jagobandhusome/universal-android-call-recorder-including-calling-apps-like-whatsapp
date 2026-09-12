package com.androidcallrecorder.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object Vault {
    private const val ALIAS = "call_recorder_vault"
    private const val TRANS = "AES/GCM/NoPadding"

    fun encrypt(file: File): File {
        require(file.exists()) { "File missing" }
        val dest = File(file.parentFile, file.name + ".enc")
        val cipher = Cipher.getInstance(TRANS)
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val iv = cipher.iv
        dest.outputStream().use { out ->
            out.write(iv.size)
            out.write(iv)
            out.write(cipher.doFinal(file.readBytes()))
        }
        file.delete()
        return dest
    }

    fun decryptToTemp(enc: File, temp: File): File {
        val raw = enc.readBytes()
        val ivLen = raw[0].toInt() and 0xff
        val iv = raw.copyOfRange(1, 1 + ivLen)
        val body = raw.copyOfRange(1 + ivLen, raw.size)
        val cipher = Cipher.getInstance(TRANS)
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, iv))
        temp.writeBytes(cipher.doFinal(body))
        return temp
    }

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }
}
