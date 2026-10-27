package com.garza.dreamcatcher.dream

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.garza.dreamcatcher.app
import com.garza.dreamcatcher.data.Dream
import com.garza.dreamcatcher.data.DreamTag
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Holds the dream being written or edited. Entries are saved on demand and whenever the screen
 * goes away, so a dream is never lost to a locked phone or a dismissed screen.
 */
class DreamEntryViewModel(
    application: Application,
    private val state: SavedStateHandle,
) : AndroidViewModel(application) {

    private val dao = application.app.database.dreamDao()
    private val saveLock = Mutex()

    /** Id of an existing dream opened from the journal, or 0 for a new entry. */
    private val editingId: Long = state[DreamEntryActivity.EXTRA_DREAM_ID] ?: 0L
    val isNew: Boolean = editingId == 0L
    val fromAlarm: Boolean = state[DreamEntryActivity.EXTRA_FROM_ALARM] ?: false

    val text: StateFlow<String> = state.getStateFlow(KEY_TEXT, "")
    // Saved as text so the selection survives the process being killed.
    private val encodedTags: StateFlow<String> = state.getStateFlow(KEY_TAGS, "")
    val tags: StateFlow<Set<DreamTag>> = encodedTags.map(DreamTag::decode)
        .stateIn(viewModelScope, SharingStarted.Eagerly, DreamTag.decode(encodedTags.value))
    val createdAt: StateFlow<Long> = state.getStateFlow(KEY_CREATED_AT, System.currentTimeMillis())

    /** Database row for this entry once it exists (new entries get one on first save). */
    private var rowId: Long
        get() = state[KEY_ROW_ID] ?: editingId
        set(value) { state[KEY_ROW_ID] = value }

    init {
        if (!isNew && state.get<Boolean>(KEY_LOADED) != true) {
            viewModelScope.launch {
                dao.get(editingId)?.let {
                    state[KEY_TEXT] = it.text
                    state[KEY_TAGS] = DreamTag.encode(it.tags)
                    state[KEY_CREATED_AT] = it.createdAt
                }
                state[KEY_LOADED] = true
            }
        }
    }

    fun setText(value: String) { state[KEY_TEXT] = value }

    fun appendPhrase(phrase: String) = setText(DreamText.appendPhrase(text.value, phrase))

    fun toggleTag(tag: DreamTag) { state[KEY_TAGS] = DreamTag.encode(DreamTag.toggle(currentTags(), tag)) }

    // Read straight from the saved state so a save right after a tap never sees a stale value.
    private fun currentTags(): Set<DreamTag> = DreamTag.decode(encodedTags.value)

    /** Saves in the background (used when the screen is hidden). */
    fun saveDraft() {
        viewModelScope.launch { save() }
    }

    /** Saves, then calls [onDone] with whether anything was stored. */
    fun saveAndThen(onDone: (saved: Boolean) -> Unit) {
        viewModelScope.launch { onDone(save()) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                saveLock.withLock { dao.get(rowId)?.let { dao.delete(it) } }
            }
            onDone()
        }
    }

    private suspend fun save(): Boolean = withContext(NonCancellable) {
        saveLock.withLock {
            val body = text.value.trim()
            val tags = currentTags()
            val existing = if (rowId != 0L) dao.get(rowId) else null
            when {
                // Don't keep empty entries (a "No dream" night is worth keeping even without words);
                // drop a draft that was auto-saved and then cleared.
                body.isEmpty() && DreamTag.NO_DREAM !in tags -> {
                    if (isNew && existing != null) dao.delete(existing)
                    false
                }
                existing != null -> {
                    if (existing.text != body || existing.tags != tags) {
                        dao.update(existing.copy(text = body, tags = tags, updatedAt = System.currentTimeMillis()))
                    }
                    true
                }
                else -> {
                    rowId = dao.insert(
                        Dream(text = body, createdAt = createdAt.value, tags = tags, fromAlarm = fromAlarm)
                    )
                    true
                }
            }
        }
    }

    private companion object {
        const val KEY_TEXT = "text"
        const val KEY_TAGS = "tags"
        const val KEY_CREATED_AT = "createdAt"
        const val KEY_ROW_ID = "rowId"
        const val KEY_LOADED = "loaded"
    }
}
