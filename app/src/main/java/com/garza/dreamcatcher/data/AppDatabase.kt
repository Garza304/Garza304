package com.garza.dreamcatcher.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Dream::class, Alarm::class], version = 2)
@TypeConverters(DreamTagConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dreamDao(): DreamDao
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** Version 2 replaced the `lucid` flag with a `tags` list; lucid dreams keep their tag. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE dreams_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "tags TEXT NOT NULL, " +
                        "fromAlarm INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO dreams_new (id, text, createdAt, updatedAt, tags, fromAlarm) " +
                        "SELECT id, text, createdAt, updatedAt, " +
                        "CASE WHEN lucid != 0 THEN '${DreamTag.LUCID.key}' ELSE '' END, fromAlarm FROM dreams"
                )
                db.execSQL("DROP TABLE dreams")
                db.execSQL("ALTER TABLE dreams_new RENAME TO dreams")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dreamcatcher.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
