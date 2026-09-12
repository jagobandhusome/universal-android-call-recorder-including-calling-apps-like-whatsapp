package com.androidcallrecorder.app.ui.player

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.androidcallrecorder.app.data.FileNamer
import com.androidcallrecorder.app.data.RecordingItem
import com.androidcallrecorder.app.record.SilenceTrimmer
import com.androidcallrecorder.app.ui.i18n.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun WaveformPlayerDialog(item: RecordingItem, onDismiss: () -> Unit) {
    var amps by remember { mutableStateOf(listOf<Float>()) }
    var progress by remember { mutableFloatStateOf(0f) }
    val player = remember { MediaPlayer() }
    LaunchedEffect(item.filePath) {
        amps = withContext(Dispatchers.IO) { SilenceTrimmer.amplitudes(item.filePath) }
        try {
            player.setDataSource(item.filePath)
            player.prepare()
            player.start()
        } catch (_: Exception) {
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(200)
            val dur = player.duration.takeIf { it > 0 } ?: 1
            progress = player.currentPosition.toFloat() / dur
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try {
                player.stop()
            } catch (_: Exception) {
            }
            player.release()
        }
    }
    val color = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outline
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .pointerInput(amps) {
                            detectTapGestures { offset ->
                                val dur = player.duration
                                if (dur > 0) {
                                    player.seekTo(((offset.x / size.width) * dur).toInt())
                                }
                            }
                        },
                ) {
                    if (amps.isEmpty()) return@Canvas
                    val w = size.width / amps.size
                    amps.forEachIndexed { i, a ->
                        val h = (a * size.height).coerceAtLeast(2f)
                        drawLine(
                            color = if (i.toFloat() / amps.size <= progress) color else muted,
                            start = Offset(i * w, size.height / 2 - h / 2),
                            end = Offset(i * w, size.height / 2 + h / 2),
                            strokeWidth = (w * 0.7f).coerceAtLeast(1f),
                        )
                    }
                }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    FileNamer.formatDuration((progress * (player.duration.takeIf { it > 0 } ?: 0)).toLong()),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        if (player.isPlaying) player.pause() else player.start()
                    }) { Text(if (player.isPlaying) L.t("Pause", "পজ") else L.t("Play", "প্লে")) }
                    TextButton(onClick = {
                        val dur = player.duration.toLong().coerceAtLeast(1)
                        val next = SilenceTrimmer.nextLoudOffset(item.filePath, player.currentPosition.toLong(), dur)
                        player.seekTo(next.toInt())
                    }) { Text(L.t("Skip silence", "নীরবতা এড়ান")) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L.t("Close", "বন্ধ")) } },
    )
}
