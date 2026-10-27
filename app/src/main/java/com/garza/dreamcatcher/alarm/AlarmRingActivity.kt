package com.garza.dreamcatcher.alarm

import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garza.dreamcatcher.dream.DreamEntryActivity
import com.garza.dreamcatcher.ui.theme.DreamcatcherTheme
import com.garza.dreamcatcher.ui.theme.Moon
import com.garza.dreamcatcher.ui.theme.NightSky
import java.time.LocalTime
import kotlinx.coroutines.delay

/** Full-screen alarm shown over the lock screen. Stopping it goes straight to recording the dream. */
class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen(turnScreenOn = true)
        enableEdgeToEdge()
        // Back must not silently leave the alarm ringing behind the lock screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        val label = RingInfo.from(intent).label
        setContent {
            DreamcatcherTheme {
                val ringing by AlarmService.ringing.collectAsStateWithLifecycle()
                // Dismissed or snoozed elsewhere (e.g. from the notification): nothing left to show.
                LaunchedEffect(ringing) { if (ringing == null) finish() }
                RingScreen(
                    label = ringing?.label ?: label,
                    is24Hour = DateFormat.is24HourFormat(this),
                    onRecord = ::stopAndRecord,
                    onSnooze = ::snooze,
                )
            }
        }
    }

    private fun stopAndRecord() {
        AlarmService.dismiss(this)
        startActivity(DreamEntryActivity.fromAlarmIntent(this))
        finish()
    }

    private fun snooze() {
        AlarmService.snooze(this)
        finish()
    }
}

@Composable
private fun RingScreen(label: String, is24Hour: Boolean, onRecord: () -> Unit, onSnooze: () -> Unit) {
    val now by produceState(LocalTime.now()) {
        while (true) {
            delay(1_000)
            value = LocalTime.now()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NightSky)
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Icon(Icons.Filled.Bedtime, contentDescription = null, tint = Moon, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                AlarmTime.formatTime(now.hour, now.minute, is24Hour),
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
                color = Moon,
            )
            Text(
                label.ifBlank { "Good morning" },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            "Keep your eyes closed for a moment.\nWhat were you just dreaming about?",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRecord, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                Icon(Icons.Filled.Mic, contentDescription = null)
                Spacer(Modifier.size(12.dp))
                Text("Stop alarm & record dream", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Snooze ${AlarmService.SNOOZE_MINUTES} min")
            }
        }
    }
}
