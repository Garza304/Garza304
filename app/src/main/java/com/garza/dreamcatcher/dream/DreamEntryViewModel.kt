package com.garza.dreamcatcher.dream

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.garza.dreamcatcher.EntryStyle
import com.garza.dreamcatcher.Settings
import com.garza.dreamcatcher.app
import com.garza.dreamcatcher.data.CustomTags
import com.garza.dreamcatcher.data.Dream
import com.garza.dreamcatcher.data.DreamTag
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The parts of the entry flow: talk freely, answer guided questions, then review and tag. */
enum class EntryStage { LISTEN, GUIDED, REVIEW }

/** The guided question that dictation currently fills in. */
enum class GuidedField { WHAT, WHO, WHERE }

/**
 * Holds the dream being written or edited. Entries are saved on demand and whenever the screen
 * goes away, so a dream is never lost to a locked phone or a dismissed screen.
 * Everything lives in [SavedStateHandle] so it also survives the process being killed.
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

    private val initialStage = when {
        !isNew -> EntryStage.REVIEW
        Settings.entryStyle(application) == EntryStyle.GUIDED -> EntryStage.GUIDED
        else -> EntryStage.LISTEN
    }
    val stage: StateFlow<EntryStage> = state.getStateFlow(KEY_STAGE, initialStage.name)
        .mapState(EntryStage::valueOf)
    val activeField: StateFlow<GuidedField> = state.getStateFlow(KEY_ACTIVE_FIELD, GuidedField.WHAT.name)
        .mapState(GuidedField::valueOf)

    /** The dream itself; in the guided stage this is the answer to "What happened?". */
    val text: StateFlow<String> = state.getStateFlow(KEY_TEXT, "")
    val who: StateFlow<String> = state.getStateFlow(KEY_WHO, "")
    val where: StateFlow<String> = state.getStateFlow(KEY_WHERE, "")
    val createdAt: StateFlow<Long> = state.getStateFlow(KEY_CREATED_AT, System.currentTimeMillis())

    // Tag sets are saved as text so they fit in SavedStateHandle.
    private val encodedTags: StateFlow<String> = state.getStateFlow(KEY_TAGS, "")
    private val encodedCustomTags: StateFlow<String> = state.getStateFlow(KEY_CUSTOM_TAGS, "")
    val tags: StateFlow<Set<DreamTag>> = encodedTags.mapState(DreamTag::decode)
    val customTags: StateFlow<Set<String>> = encodedCustomTags.mapState(CustomTags::decode)

    /** Custom tags to offer: every one used before (most used first), plus any just made for this entry. */
    val availableCustomTags: StateFlow<List<String>> =
        combine(dao.observeCustomTagRows(), customTags) { rows, selected ->
            val known = CustomTags.rank(rows)
            known + selected.filter { name -> known.none { it.equals(name, ignoreCase = true) } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                    state[KEY_CUSTOM_TAGS] = CustomTags.encode(it.customTags)
                    state[KEY_CREATED_AT] = it.createdAt
                }
                state[KEY_LOADED] = true
            }
        }
    }

    fun setText(value: String) { state[KEY_TEXT] = value }
    fun setWho(value: String) { state[KEY_WHO] = value }
    fun setWhere(value: String) { state[KEY_WHERE] = value }

    fun setActiveField(field: GuidedField) { state[KEY_ACTIVE_FIELD] = field.name }

    /** Adds dictated words to the guided question being answered, or to the dream text. */
    fun appendPhrase(phrase: String) {
        val key = if (currentStage() == EntryStage.GUIDED) {
            when (GuidedField.valueOf(state[KEY_ACTIVE_FIELD] ?: GuidedField.WHAT.name)) {
                GuidedField.WHAT -> KEY_TEXT
                GuidedField.WHO -> KEY_WHO
                GuidedField.WHERE -> KEY_WHERE
            }
        } else {
            KEY_TEXT
        }
        state[key] = DreamText.appendPhrase(state[key] ?: "", phrase)
    }

    /** Moves on to reviewing and tagging; guided answers are folded into the dream text here. */
    fun goToReview() {
        if (currentStage() == EntryStage.GUIDED) {
            state[KEY_TEXT] = composedText()
            state[KEY_WHO] = ""
            state[KEY_WHERE] = ""
        }
        state[KEY_STAGE] = EntryStage.REVIEW.name
    }

    /** "I don't remember" from the guided questions: mark the night and move on. */
    fun rememberedNothing() {
        if (DreamTag.NO_DREAM !in currentTags()) toggleTag(DreamTag.NO_DREAM)
        goToReview()
    }

    /** Back to talking freely, adding to the text already there. */
    fun goToListen() { state[KEY_STAGE] = EntryStage.LISTEN.name }

    fun toggleTag(tag: DreamTag) { state[KEY_TAGS] = DreamTag.encode(DreamTag.toggle(currentTags(), tag)) }

    fun toggleCustomTag(name: String) {
        state[KEY_CUSTOM_TAGS] = CustomTags.encode(CustomTags.toggle(currentCustomTags(), name))
    }

    /** Creates a tag from typed text and selects it. Returns an error to show, or null on success. */
    fun addCustomTag(raw: String): String? {
        val name = CustomTags.normalize(raw)
            ?: return if (raw.isBlank()) "Type a name for the tag." else "That name is already a built-in tag."
        val selected = currentCustomTags()
        if (selected.none { it.equals(name, ignoreCase = true) }) {
            // Reuse the spelling of an existing tag so "flying" joins "Flying".
            val existing = availableCustomTags.value.firstOrNull { it.equals(name, ignoreCase = true) }
            state[KEY_CUSTOM_TAGS] = CustomTags.encode(selected + (existing ?: name))
        }
        return null
    }

    // Read straight from the saved state so a save right after a tap never sees a stale value.
    private fun currentStage(): EntryStage = EntryStage.valueOf(state[KEY_STAGE] ?: initialStage.name)
    private fun currentTags(): Set<DreamTag> = DreamTag.decode(encodedTags.value)
    private fun currentCustomTags(): Set<String> = CustomTags.decode(encodedCustomTags.value)

    private fun composedText(): String =
        if (currentStage() == EntryStage.GUIDED) DreamText.composeGuided(text.value, who.value, where.value) else text.value

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
            val body = composedText().trim()
            val tags = currentTags()
            val customTags = currentCustomTags()
            val existing = if (rowId != 0L) dao.get(rowId) else null
            when {
                // Don't keep empty entries (a "No dream" night is worth keeping even without words);
                // drop a draft that was auto-saved and then cleared.
                body.isEmpty() && DreamTag.NO_DREAM !in tags -> {
                    if (isNew && existing != null) dao.delete(existing)
                    false
                }
                existing != null -> {
                    if (existing.text != body || existing.tags != tags || existing.customTags != customTags) {
                        dao.update(
                            existing.copy(
                                text = body,
                                tags = tags,
                                customTags = customTags,
                                updatedAt = System.currentTimeMillis(),
                            )
                        )
                    }
                    true
                }
                else -> {
                    rowId = dao.insert(
                        Dream(
                            text = body,
                            createdAt = createdAt.value,
                            tags = tags,
                            customTags = customTags,
                            fromAlarm = fromAlarm,
                        )
                    )
                    true
                }
            }
        }
    }

    private fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> =
        map(transform).stateIn(viewModelScope, SharingStarted.Eagerly, transform(value))

    private companion object {
        const val KEY_STAGE = "stage"
        const val KEY_ACTIVE_FIELD = "activeField"
        const val KEY_TEXT = "text"
        const val KEY_WHO = "who"
        const val KEY_WHERE = "where"
        const val KEY_TAGS = "tags"
        const val KEY_CUSTOM_TAGS = "customTags"
        const val KEY_CREATED_AT = "createdAt"
        const val KEY_ROW_ID = "rowId"
        const val KEY_LOADED = "loaded"
    }
}
