package com.garza.dreamcatcher.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * A daily backup into a folder the user picked. Each day gets its own file and the newest
 * [KEEP] are kept, so one bad day can't overwrite every good copy.
 */
object AutoBackup {

    /** Where backups go and how the last one went. [folder] is null when automatic backup is off. */
    data class Status(val folder: Uri?, val folderName: String?, val lastBackupAt: Long?, val lastError: String?)

    private const val PREFS = "auto_backup"
    private const val KEY_FOLDER = "folder"
    private const val KEY_FOLDER_NAME = "folder_name"
    private const val KEY_LAST_AT = "last_at"
    private const val KEY_LAST_ERROR = "last_error"
    private const val WORK_DAILY = "auto_backup_daily"
    private const val WORK_NOW = "auto_backup_now"
    private const val KEEP = 7
    private const val FILE_PREFIX = "dreamcatcher-backup-"

    private val state = MutableStateFlow(Status(null, null, null, null))
    val status: StateFlow<Status> = state.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reads the saved status and makes sure the daily job is queued; called when the app starts. */
    fun load(context: Context) {
        val p = prefs(context)
        state.value = Status(
            folder = p.getString(KEY_FOLDER, null)?.let(Uri::parse),
            folderName = p.getString(KEY_FOLDER_NAME, null),
            lastBackupAt = p.getLong(KEY_LAST_AT, 0L).takeIf { it > 0 },
            lastError = p.getString(KEY_LAST_ERROR, null),
        )
        if (state.value.folder != null) scheduleDaily(context)
    }

    /** Turns automatic backup on for [folder], picked with ACTION_OPEN_DOCUMENT_TREE, and backs up right away. */
    suspend fun enable(context: Context, folder: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(folder, flags)
        val name = withContext(Dispatchers.IO) { DocumentFile.fromTreeUri(context, folder)?.name }
        prefs(context).edit()
            .putString(KEY_FOLDER, folder.toString())
            .putString(KEY_FOLDER_NAME, name)
            .remove(KEY_LAST_ERROR)
            .apply()
        state.value = state.value.copy(folder = folder, folderName = name, lastError = null)
        scheduleDaily(context)
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutoBackupWorker>().build(),
        )
    }

    /** Stops automatic backup. Files already saved stay where they are. */
    fun disable(context: Context) {
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(WORK_DAILY)
        work.cancelUniqueWork(WORK_NOW)
        state.value.folder?.let { folder ->
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.releasePersistableUriPermission(folder, flags) }
        }
        prefs(context).edit().remove(KEY_FOLDER).remove(KEY_FOLDER_NAME).remove(KEY_LAST_ERROR).apply()
        state.value = state.value.copy(folder = null, folderName = null, lastError = null)
    }

    private fun scheduleDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Saves today's backup into the chosen folder and prunes old ones. Records the outcome for Settings. */
    suspend fun run(context: Context) = withContext(Dispatchers.IO) {
        val folder = state.value.folder ?: return@withContext
        try {
            val dir = DocumentFile.fromTreeUri(context, folder)
            if (dir == null || !dir.canWrite()) {
                recordError(context, "Can't reach the backup folder. Turn automatic backup off and on to pick it again.")
                return@withContext
            }
            val backup = Backup.snapshot(context)
            // Nothing to protect yet, and an empty file must never push out a real one.
            if (backup.dreams.isEmpty() && backup.alarms.isEmpty()) return@withContext

            val name = BackupFormat.fileName(LocalDate.now())
            dir.findFile(name)?.delete()
            val file = dir.createFile(BackupFormat.MIME_TYPE, name)
                ?: return@withContext recordError(context, "Couldn't create a file in the backup folder.")
            val stream = context.contentResolver.openOutputStream(file.uri, "w")
                ?: return@withContext recordError(context, "Couldn't write to the backup folder.")
            stream.use { Backup.write(backup, it) }

            dir.listFiles()
                .filter { it.name?.startsWith(FILE_PREFIX) == true && it.name?.endsWith(".json") == true }
                .sortedByDescending { it.name }
                .drop(KEEP)
                .forEach { it.delete() }

            val now = System.currentTimeMillis()
            prefs(context).edit().putLong(KEY_LAST_AT, now).remove(KEY_LAST_ERROR).apply()
            state.value = state.value.copy(lastBackupAt = now, lastError = null)
        } catch (e: Exception) {
            recordError(context, "The last automatic backup failed. It will try again tomorrow.")
        }
    }

    private fun recordError(context: Context, message: String) {
        prefs(context).edit().putString(KEY_LAST_ERROR, message).apply()
        state.value = state.value.copy(lastError = message)
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Failures are shown in Settings rather than retried in a loop.
        AutoBackup.run(applicationContext)
        return Result.success()
    }
}
