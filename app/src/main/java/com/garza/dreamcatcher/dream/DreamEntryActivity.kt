package com.garza.dreamcatcher.dream

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.garza.dreamcatcher.alarm.AlarmService
import com.garza.dreamcatcher.alarm.showOverLockScreen
import com.garza.dreamcatcher.ui.theme.DreamcatcherTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Write down a dream by voice or keyboard. Opened right after an alarm is stopped (listening
 * starts on its own, even over the lock screen), from the journal for a new entry, or to edit one.
 */
class DreamEntryActivity : ComponentActivity() {

    private val viewModel: DreamEntryViewModel by viewModels()
    private lateinit var dictation: SpeechDictation

    private var listening by mutableStateOf(false)
    private var partial by mutableStateOf("")
    private var message by mutableStateOf<String?>(null)

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) dictation.start() else message = "Microphone access was denied. You can type your dream instead."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (viewModel.fromAlarm) {
            // Only this screen shows over the lock screen; the rest of the journal stays private.
            showOverLockScreen(turnScreenOn = false)
            AlarmService.dismiss(this)
        }
        enableEdgeToEdge()

        dictation = SpeechDictation(this, object : SpeechDictation.Listener {
            override fun onPartial(text: String) { partial = text }
            override fun onPhrase(text: String) = viewModel.appendPhrase(text)
            override fun onListeningChanged(listening: Boolean) { this@DreamEntryActivity.listening = listening }
            override fun onError(message: String) { this@DreamEntryActivity.message = message }
        })

        if (viewModel.fromAlarm && savedInstanceState == null) {
            // Give the alarm sound a moment to stop so the microphone doesn't hear it.
            lifecycleScope.launch {
                delay(600)
                startDictation()
            }
        }

        setContent {
            DreamcatcherTheme {
                val text by viewModel.text.collectAsStateWithLifecycle()
                val lucid by viewModel.lucid.collectAsStateWithLifecycle()
                val createdAt by viewModel.createdAt.collectAsStateWithLifecycle()
                BackHandler(onBack = ::saveAndClose)
                DreamEntryScreen(
                    isNew = viewModel.isNew,
                    createdAt = createdAt,
                    text = text,
                    onTextChange = viewModel::setText,
                    partial = partial,
                    lucid = lucid,
                    onLucidChange = viewModel::setLucid,
                    listening = listening,
                    voiceAvailable = dictation.isAvailable,
                    message = message,
                    onToggleMic = { if (listening) dictation.stop() else startDictation() },
                    onSave = ::saveAndClose,
                    onDelete = { viewModel.delete(::finish) },
                )
            }
        }
    }

    private fun startDictation() {
        message = null
        when {
            !dictation.isAvailable ->
                message = "Voice input isn't available on this device. Type your dream instead."
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
                dictation.start()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun saveAndClose() {
        dictation.stop()
        viewModel.saveAndThen { saved ->
            if (saved && viewModel.fromAlarm) {
                Toast.makeText(this, "Dream saved", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            dictation.stop()
            viewModel.saveDraft()
        }
    }

    override fun onDestroy() {
        dictation.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_DREAM_ID = "com.garza.dreamcatcher.DREAM_ID"
        const val EXTRA_FROM_ALARM = "com.garza.dreamcatcher.FROM_ALARM"

        fun newIntent(context: Context) = Intent(context, DreamEntryActivity::class.java)

        fun editIntent(context: Context, dreamId: Long) =
            newIntent(context).putExtra(EXTRA_DREAM_ID, dreamId)

        fun fromAlarmIntent(context: Context) =
            newIntent(context)
                .putExtra(EXTRA_FROM_ALARM, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
