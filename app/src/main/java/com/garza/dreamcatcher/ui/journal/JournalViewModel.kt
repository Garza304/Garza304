package com.garza.dreamcatcher.ui.journal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garza.dreamcatcher.app
import com.garza.dreamcatcher.data.Dream
import com.garza.dreamcatcher.dream.DreamText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class JournalViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = application.app.database.dreamDao()

    val query = MutableStateFlow("")

    /** Null until the first load finishes, so the screen can tell "loading" from "empty". */
    val dreams: StateFlow<List<Dream>?> = combine(dao.observeAll(), query) { all, q ->
        all.filter { DreamText.matches(it, q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun exportText(): String = DreamText.export(dao.getAll())
}
