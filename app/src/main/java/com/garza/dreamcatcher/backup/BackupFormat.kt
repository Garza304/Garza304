package com.garza.dreamcatcher.backup

import com.garza.dreamcatcher.data.Alarm
import com.garza.dreamcatcher.data.Dream
import com.garza.dreamcatcher.data.DreamTag
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * A backup file: plain JSON, so it stays readable outside the app. Fields only ever get added;
 * [version] goes up when an older app could no longer restore the file correctly.
 */
@Serializable
data class BackupFile(
    val app: String = BackupFormat.APP_ID,
    val version: Int = BackupFormat.VERSION,
    val exportedAt: Long,
    val dreams: List<DreamRecord> = emptyList(),
    val alarms: List<AlarmRecord> = emptyList(),
    val settings: SettingsRecord? = null,
)

@Serializable
data class DreamRecord(
    val text: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    /** [DreamTag.key]s; keys this version doesn't know are dropped on restore. */
    val tags: List<String> = emptyList(),
    val customTags: List<String> = emptyList(),
    val fromAlarm: Boolean = false,
)

@Serializable
data class AlarmRecord(
    val hour: Int,
    val minute: Int,
    val repeatDays: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    val vibrate: Boolean = true,
)

@Serializable
data class SettingsRecord(val palette: String? = null, val entryStyle: String? = null, val insightsStyle: String? = null)

/** A file that can't be restored, with a message fit to show as is. */
class BackupException(message: String) : Exception(message)

/** What restoring [backup] would add. Nothing already in the app is changed or removed. */
data class RestorePlan(
    val backup: BackupFile,
    val newDreams: List<Dream>,
    val newAlarms: List<Alarm>,
    /** Settings are only restored into an empty journal, i.e. a new phone or a reinstall. */
    val restoreSettings: Boolean,
)

/** Turns the journal into a backup file and back, kept free of Android classes so it can be unit tested. */
object BackupFormat {
    const val APP_ID = "dreamcatcher"
    const val VERSION = 1
    const val MIME_TYPE = "application/json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun fileName(date: LocalDate): String = "dreamcatcher-backup-$date.json"

    fun create(dreams: List<Dream>, alarms: List<Alarm>, settings: SettingsRecord?, now: Long): BackupFile =
        BackupFile(
            exportedAt = now,
            dreams = dreams.sortedBy { it.createdAt }.map {
                DreamRecord(
                    text = it.text,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    tags = DreamTag.entries.filter { tag -> tag in it.tags }.map { tag -> tag.key },
                    customTags = it.customTags.toList(),
                    fromAlarm = it.fromAlarm,
                )
            },
            alarms = alarms.map { AlarmRecord(it.hour, it.minute, it.repeatDays, it.enabled, it.label, it.vibrate) },
            settings = settings,
        )

    fun encode(backup: BackupFile): String = json.encodeToString(BackupFile.serializer(), backup)

    /** Reads a backup, or throws [BackupException] explaining why it can't be used. */
    fun decode(text: String): BackupFile {
        val backup = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException("This file isn't a Dreamcatcher backup.")
        } catch (e: IllegalArgumentException) {
            throw BackupException("This file isn't a Dreamcatcher backup.")
        }
        if (backup.app != APP_ID) throw BackupException("This file isn't a Dreamcatcher backup.")
        if (backup.version > VERSION) {
            throw BackupException("This backup was made by a newer version of Dreamcatcher. Update the app, then try again.")
        }
        return backup
    }

    /**
     * Works out which entries and alarms in [backup] are new. An entry already here (same time and
     * words) or an identical alarm is skipped, so restoring the same file twice adds nothing.
     */
    fun plan(backup: BackupFile, existingDreams: List<Dream>, existingAlarms: List<Alarm>): RestorePlan {
        val seenDreams = existingDreams.mapTo(HashSet()) { it.createdAt to it.text.trim() }
        val newDreams = backup.dreams
            .filter { seenDreams.add(it.createdAt to it.text.trim()) }
            .map { record ->
                Dream(
                    text = record.text,
                    createdAt = record.createdAt,
                    updatedAt = maxOf(record.updatedAt, record.createdAt),
                    tags = DreamTag.entries.filterTo(LinkedHashSet()) { it.key in record.tags },
                    // Custom tags are stored one per line, so a line break would split one in two.
                    customTags = record.customTags
                        .map { it.replace(Regex("\\s+"), " ").trim() }
                        .filterTo(LinkedHashSet()) { it.isNotEmpty() },
                    fromAlarm = record.fromAlarm,
                )
            }

        fun AlarmRecord.key() = listOf(hour, minute, repeatDays and 0x7F, label.trim())
        val seenAlarms = existingAlarms.mapTo(HashSet()) { AlarmRecord(it.hour, it.minute, it.repeatDays, label = it.label).key() }
        val newAlarms = backup.alarms
            .filter { it.hour in 0..23 && it.minute in 0..59 && seenAlarms.add(it.key()) }
            .map { Alarm(hour = it.hour, minute = it.minute, repeatDays = it.repeatDays and 0x7F, enabled = it.enabled, label = it.label.trim(), vibrate = it.vibrate) }

        return RestorePlan(
            backup = backup,
            newDreams = newDreams,
            newAlarms = newAlarms,
            restoreSettings = existingDreams.isEmpty() && backup.settings != null,
        )
    }
}
