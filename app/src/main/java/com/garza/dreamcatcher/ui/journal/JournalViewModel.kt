package com.garza.dreamcatcher.ui.journal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garza.dreamcatcher.app
import com.garza.dreamcatcher.data.CustomTags
import com.garza.dreamcatcher.data.Dream
import com.garza.dreamcatcher.data.DreamTag
import com.garza.dreamcatcher.dream.DreamText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A tag to narrow the journal to: one of the built-in tags or one the user made. */
sealed interface TagFilter {
    fun matches(dream: Dream): Boolean

    data class BuiltIn(val tag: DreamTag) : TagFilter {
        override fun matches(dream: Dream) = tag in dream.tags
    }

    data class Custom(val name: String) : TagFilter {
        override fun matches(dream: Dream) = dream.customTags.any { it.equals(name, ignoreCase = true) }
    }
}

class JournalViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = application.app.database.dreamDao()

    val query = MutableStateFlow("")

    /** Show only entries with this tag, or everything when null. */
    val tagFilter = MutableStateFlow<TagFilter?>(null)

    /** Custom tags in use, most used first, for the filter row. */
    val customTags: StateFlow<List<String>> = dao.observeCustomTagRows()
        .map(CustomTags::rank)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null until the first load finishes, so the screen can tell "loading" from "empty". */
    val dreams: StateFlow<List<Dream>?> = combine(dao.observeAll(), query, tagFilter) { all, q, filter ->
        all.filter { (filter == null || filter.matches(it)) && DreamText.matches(it, q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun exportText(): String = DreamText.export(dao.getAll())
}
