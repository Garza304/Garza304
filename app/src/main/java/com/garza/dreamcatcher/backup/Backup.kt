package com.garza.dreamcatcher.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.garza.dreamcatcher.Settings
import com.garza.dreamcatcher.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

/** Saving the journal to a file and restoring it from one. */
object Backup {

    /** Far more than any journal needs; stops a wrong pick (a video, say) from being read into memory. */
    private const val MAX_FILE_BYTES = 50L * 1024 * 1024

    suspend fun snapshot(context: Context): BackupFile = withContext(Dispatchers.IO) {
        val db = context.app.database
        BackupFormat.create(
            dreams = db.dreamDao().getAll(),
            alarms = db.alarmDao().getAll(),
            settings = Settings.toBackup(context),
            now = System.currentTimeMillis(),
        )
    }

    /** Writes a backup to a file the user picked and returns how many dreams it holds. */
    suspend fun writeTo(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val backup = snapshot(context)
        val stream = context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("Couldn't open $uri")
        stream.use { write(backup, it) }
        backup.dreams.size
    }

    fun write(backup: BackupFile, stream: OutputStream) {
        stream.write(BackupFormat.encode(backup).toByteArray(Charsets.UTF_8))
    }

    /** Reads a backup file and works out what restoring it would add. */
    suspend fun plan(context: Context, uri: Uri): RestorePlan = withContext(Dispatchers.IO) {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = input.readNBytesCompat(MAX_FILE_BYTES + 1)
                if (buffer.size > MAX_FILE_BYTES) throw BackupException("This file is too big to be a Dreamcatcher backup.")
                buffer
            }
        } catch (e: IOException) {
            null
        } ?: throw BackupException("Couldn't open that file.")
        val backup = BackupFormat.decode(bytes.toString(Charsets.UTF_8))
        val db = context.app.database
        BackupFormat.plan(backup, db.dreamDao().getAll(), db.alarmDao().getAll())
    }

    /** Adds the plan's entries and alarms in one go, so a failure part way leaves nothing half restored. */
    suspend fun restore(context: Context, plan: RestorePlan) = withContext(Dispatchers.IO) {
        val app = context.app
        val db = app.database
        val alarms = db.withTransaction {
            plan.newDreams.forEach { db.dreamDao().insert(it) }
            plan.newAlarms.map { it.copy(id = db.alarmDao().insert(it)) }
        }
        app.scheduler.rescheduleAll(alarms)
        if (plan.restoreSettings) plan.backup.settings?.let { Settings.restore(context, it) }
    }

    /** InputStream.readNBytes needs API 33. */
    private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val read = read(chunk, 0, minOf(chunk.size.toLong(), limit - out.size()).toInt())
            if (read < 0) break
            out.write(chunk, 0, read)
        }
        return out.toByteArray()
    }
}
