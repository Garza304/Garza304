package com.garza.dreamcatcher.ui.alarms

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garza.dreamcatcher.app
import com.garza.dreamcatcher.data.Alarm
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmsViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = application.app.database.alarmDao()
    private val scheduler = application.app.scheduler

    val alarms: StateFlow<List<Alarm>?> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Inserts or updates [alarm] and arms or disarms it to match. */
    fun save(alarm: Alarm) {
        viewModelScope.launch {
            val id = if (alarm.id == 0L) dao.insert(alarm) else alarm.id.also { dao.update(alarm) }
            if (alarm.enabled) scheduler.schedule(alarm.copy(id = id)) else scheduler.cancel(id)
        }
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean) = save(alarm.copy(enabled = enabled))

    fun delete(alarm: Alarm) {
        viewModelScope.launch {
            scheduler.cancel(alarm.id)
            dao.delete(alarm)
        }
    }
}
