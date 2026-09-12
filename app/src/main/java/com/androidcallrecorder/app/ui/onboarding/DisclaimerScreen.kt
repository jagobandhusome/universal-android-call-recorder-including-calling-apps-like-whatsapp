package com.androidcallrecorder.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.androidcallrecorder.app.data.SettingsStore
import com.androidcallrecorder.app.ui.components.SectionCard
import com.androidcallrecorder.app.ui.i18n.L
import com.androidcallrecorder.app.ui.theme.CallRecorderTheme
import com.androidcallrecorder.app.ui.theme.IvacInk
import com.androidcallrecorder.app.ui.theme.IvacMuted
import com.androidcallrecorder.app.ui.theme.AppScreenBackground

@Composable
fun DisclaimerScreen(onAccept: () -> Unit) {
    CallRecorderTheme(darkTheme = false) {
        AppScreenBackground {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    L.t("Call Recorder", "কল রেকর্ডার"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = IvacInk,
                )
                Text(
                    L.t("Welcome — please read this once.", "স্বাগতম — একবার পড়ে নিন।"),
                    style = MaterialTheme.typography.titleMedium,
                    color = IvacMuted,
                )
                SectionCard(
                    L.t("Before you record", "রেকর্ড করার আগে"),
                    L.t(
                        "This is a legal notice, not an empty screen. Tap I understand to open the app.",
                        "এটি আইনি নোটিশ। অ্যাপ খুলতে I understand চাপুন।",
                    ),
                ) {
                    Text(
                        L.t(
                            "You are responsible for following the recording laws where you live. Some places require every person on the call to consent. A recording notice may still play on some phones — this app will not hide or disable that.",
                            "আপনি যেখানে আছেন সেখানকার আইন মেনে রেকর্ড করবেন। কিছু জায়গায় সবার সম্মতি লাগে। কিছু ফোনে নোটিশ বাজতে পারে — এই অ্যাপ সেটি লুকাবে না।",
                        ),
                        color = IvacInk,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        L.t(
                            "Turn on Consent beep in Settings if your region requires an audible tone. A notification stays on screen while recording.",
                            "প্রয়োজন হলে Settings-এ Consent beep চালু করুন। রেকর্ড চলাকালে নোটিফিকেশন দেখা যাবে।",
                        ),
                        color = IvacInk,
                    )
                }
                Button(
                    onClick = {
                        SettingsStore.update { it.copy(disclaimerAccepted = true) }
                        onAccept()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(L.t("I understand", "আমি বুঝেছি"))
                }
            }
        }
    }
}
