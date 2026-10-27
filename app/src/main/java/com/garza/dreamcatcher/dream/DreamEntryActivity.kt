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
 * Write down a dream by voice or keyboard. Opened from the journal for a new entry or to edit one.
 * New entries started on waking (alarm, Quick Settings tile, lock screen shortcut) open in
 * [LockScreenDreamActivity] instead, which shows over the lock screen and starts listening on its own.
 */
open class DreamEntryActivity : ComponentActivity() {

    /** Whether this screen may appear over the lock screen. Only new entries ever do. */
    protected open val overLockScreen = false

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
        // Only new entries show over the lock screen; the rest of the journal stays private.
        if (overLockScreen) showOverLockScreen(turnScreenOn = false)
        if (viewModel.fromAlarm) AlarmService.dismiss(this)
        enableEdgeToEdge()

        dictation = SpeechDictation(this, object : SpeechDictation.Listener {
            override fun onPartial(text: String) { partial = text }
            override fun onPhrase(text: String) = viewModel.appendPhrase(text)
            override fun onListeningChanged(listening: Boolean) { this@DreamEntryActivity.listening = listening }
            override fun onError(message: String) { this@DreamEntryActivity.message = message }
        })

        if (intent.getBooleanExtra(EXTRA_AUTO_LISTEN, false) && savedInstanceState == null) {
            // Give a ringing alarm a moment to stop so the microphone doesn't hear it.
            lifecycleScope.launch {
                delay(600)
                startDictation()
            }
        }

        setContent {
            DreamcatcherTheme {
                val text by viewModel.text.collectAsStateWithLifecycle()
                val tags by viewModel.tags.collectAsStateWithLifecycle()
                val createdAt by viewModel.createdAt.collectAsStateWithLifecycle()
                BackHandler(onBack = ::saveAndClose)
                DreamEntryScreen(
                    isNew = viewModel.isNew,
                    createdAt = createdAt,
                    text = text,
                    onTextChange = viewModel::setText,
                    partial = partial,
                    tags = tags,
                    onToggleTag = viewModel::toggleTag,
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
        private const val EXTRA_AUTO_LISTEN = "com.garza.dreamcatcher.AUTO_LISTEN"

        fun newIntent(context: Context) = Intent(context, DreamEntryActivity::class.java)

        fun editIntent(context: Context, dreamId: Long) =
            newIntent(context).putExtra(EXTRA_DREAM_ID, dreamId)

        /** A new entry right after an alarm, over the lock screen, already listening. */
        fun fromAlarmIntent(context: Context) =
            wakeUpIntent(context).putExtra(EXTRA_FROM_ALARM, true)

        /** A new entry on waking without an alarm (tile or lock screen shortcut), already listening. */
        fun wakeUpIntent(context: Context) =
            Intent(context, LockScreenDreamActivity::class.java)
                .putExtra(EXTRA_AUTO_LISTEN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

/**
 * The new-entry screen as shown on waking. It is declared `showWhenLocked` in the manifest so the
 * system opens it from the lock screen (notification, Quick Settings tile) without asking to unlock;
 * it can only create entries, never show existing ones.
 */
class LockScreenDreamActivity : DreamEntryActivity() {
    override val overLockScreen = true
}
