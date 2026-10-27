package com.garza.dreamcatcher.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garza.dreamcatcher.Settings
import com.garza.dreamcatcher.data.Alarm
import com.garza.dreamcatcher.dream.DreamEntryActivity
import com.garza.dreamcatcher.ui.alarms.AlarmEditDialog
import com.garza.dreamcatcher.ui.alarms.AlarmsScreen
import com.garza.dreamcatcher.ui.alarms.AlarmsViewModel
import com.garza.dreamcatcher.ui.insights.InsightsActions
import com.garza.dreamcatcher.ui.insights.InsightsScreen
import com.garza.dreamcatcher.ui.insights.InsightsViewModel
import com.garza.dreamcatcher.ui.journal.JournalScreen
import com.garza.dreamcatcher.ui.journal.JournalViewModel
import com.garza.dreamcatcher.ui.journal.TagFilter
import com.garza.dreamcatcher.ui.theme.DreamcatcherTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DreamcatcherTheme { MainScreen() }
        }
    }
}

private enum class Tab(val title: String) { Journal("Dream journal"), Insights("Insights"), Alarms("Alarms") }

/** The alarm being edited: [alarm] is null when creating a new one. */
private data class AlarmEditor(val alarm: Alarm?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val journal: JournalViewModel = viewModel()
    val alarmsModel: AlarmsViewModel = viewModel()
    val insights: InsightsViewModel = viewModel()

    var tab by rememberSaveable { mutableIntStateOf(Tab.Journal.ordinal) }
    var editor by remember { mutableStateOf<AlarmEditor?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val currentTab = Tab.entries[tab]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentTab.title) },
                actions = {
                    if (currentTab == Tab.Journal) {
                        IconButton(onClick = {
                            scope.launch {
                                val text = journal.exportText()
                                if (text.isBlank()) {
                                    Toast.makeText(context, "No dreams to share yet", Toast.LENGTH_SHORT).show()
                                } else {
                                    val send = Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(Intent.EXTRA_SUBJECT, "My dream journal")
                                        .putExtra(Intent.EXTRA_TEXT, text)
                                    context.startActivity(Intent.createChooser(send, "Export dream journal"))
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Export journal")
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == Tab.Journal,
                    onClick = { tab = Tab.Journal.ordinal },
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                    label = { Text("Journal") },
                )
                NavigationBarItem(
                    selected = currentTab == Tab.Insights,
                    onClick = { tab = Tab.Insights.ordinal },
                    icon = { Icon(Icons.Filled.Insights, contentDescription = null) },
                    label = { Text("Insights") },
                )
                NavigationBarItem(
                    selected = currentTab == Tab.Alarms,
                    onClick = { tab = Tab.Alarms.ordinal },
                    icon = { Icon(Icons.Filled.Alarm, contentDescription = null) },
                    label = { Text("Alarms") },
                )
            }
        },
        floatingActionButton = {
            when (currentTab) {
                Tab.Journal -> ExtendedFloatingActionButton(
                    onClick = { context.startActivity(DreamEntryActivity.newIntent(context)) },
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    text = { Text("New dream") },
                )
                Tab.Alarms -> ExtendedFloatingActionButton(
                    onClick = { editor = AlarmEditor(null) },
                    icon = { Icon(Icons.Filled.AlarmAdd, contentDescription = null) },
                    text = { Text("Add alarm") },
                )
                Tab.Insights -> Unit
            }
        },
    ) { padding ->
        when (currentTab) {
            Tab.Journal -> {
                val dreams by journal.dreams.collectAsStateWithLifecycle()
                val query by journal.query.collectAsStateWithLifecycle()
                val tagFilter by journal.tagFilter.collectAsStateWithLifecycle()
                val customTags by journal.customTags.collectAsStateWithLifecycle()
                JournalScreen(
                    dreams = dreams,
                    query = query,
                    onQueryChange = { journal.query.value = it },
                    customTags = customTags,
                    tagFilter = tagFilter,
                    onTagFilterChange = { journal.tagFilter.value = it },
                    onOpen = { context.startActivity(DreamEntryActivity.editIntent(context, it.id)) },
                    contentPadding = padding,
                )
            }
            Tab.Insights -> {
                val state by insights.state.collectAsStateWithLifecycle()
                val style by Settings.insightsStyle.collectAsStateWithLifecycle()
                InsightsScreen(
                    style = style,
                    state = state,
                    actions = InsightsActions(
                        onMonthChange = insights::showMonth,
                        onSelectDay = insights::selectDay,
                        onRangeChange = insights::setRange,
                        onOpen = { context.startActivity(DreamEntryActivity.editIntent(context, it.id)) },
                        onTag = { tag ->
                            journal.query.value = ""
                            journal.tagFilter.value = tag.builtIn?.let { TagFilter.BuiltIn(it) } ?: TagFilter.Custom(tag.label)
                            tab = Tab.Journal.ordinal
                        },
                        onDigestMonthly = insights::setDigestMonthly,
                        onDigestStep = insights::stepDigest,
                    ),
                    contentPadding = padding,
                )
            }
            Tab.Alarms -> {
                val alarms by alarmsModel.alarms.collectAsStateWithLifecycle()
                AlarmsScreen(
                    alarms = alarms,
                    onEdit = { editor = AlarmEditor(it) },
                    onToggle = alarmsModel::setEnabled,
                    contentPadding = padding,
                )
            }
        }
    }

    if (showSettings) SettingsDialog(onDismiss = { showSettings = false })

    editor?.let { current ->
        // Fresh dialog state for each alarm opened.
        key(current) {
            AlarmEditDialog(
                alarm = current.alarm,
                onDismiss = { editor = null },
                onSave = {
                    alarmsModel.save(it)
                    editor = null
                },
                onDelete = {
                    alarmsModel.delete(it)
                    editor = null
                },
            )
        }
    }
}
