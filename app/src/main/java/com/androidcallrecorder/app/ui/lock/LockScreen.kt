package com.androidcallrecorder.app.ui.lock

import androidx.biometric.BiometricPrompt
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.androidcallrecorder.app.data.SettingsStore
import com.androidcallrecorder.app.security.AppLock
import com.androidcallrecorder.app.ui.i18n.L
import com.androidcallrecorder.app.ui.theme.AppScreenBackground

@Composable
fun LockScreen(title: String = L.t("Unlock Call Recorder", "আনলক"), onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val settings = SettingsStore.current()

    fun tryPin() {
        if (AppLock.verifyPin(pin)) onUnlocked() else error = L.t("Wrong PIN", "ভুল PIN")
    }

    LaunchedEffect(settings.useBiometric) {
        if (settings.useBiometric && AppLock.canUseBiometric(context)) {
            val activity = context as? FragmentActivity ?: return@LaunchedEffect
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onUnlocked()
                    }
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setNegativeButtonText("PIN")
                    .build(),
            )
        }
    }

    AppScreenBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            if (AppLock.hasPin()) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8) pin = it.filter { c -> c.isDigit() } },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error.isNotBlank(),
                    supportingText = { if (error.isNotBlank()) Text(error) },
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = ::tryPin, modifier = Modifier.fillMaxWidth()) { Text(L.t("Unlock", "আনলক")) }
            }
            if (settings.patternHash.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(L.t("Or draw your pattern", "অথবা প্যাটার্ন আঁকুন"))
                PatternPad(onComplete = { cells ->
                    if (AppLock.verifyPattern(cells)) onUnlocked() else error = L.t("Wrong pattern", "ভুল প্যাটার্ন")
                })
            }
        }
    }
}

@Composable
fun PatternPad(onComplete: (List<Int>) -> Unit) {
    val selected = remember { mutableStateListOf<Int>() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
        for (row in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(6.dp)) {
                for (col in 0 until 3) {
                    val idx = row * 3 + col
                    val on = idx in selected
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (on) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape,
                            )
                            .clickable {
                                if (idx !in selected) selected += idx
                                if (selected.size >= 4) {
                                    onComplete(selected.toList())
                                }
                            },
                    )
                }
            }
        }
        Text(
            L.t("Tap Reset to redraw", "আবার আঁকতে রিসেট"),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable { selected.clear() },
        )
    }
}
