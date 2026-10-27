package com.garza.dreamcatcher.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dreams")
data class Dream(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val tags: Set<DreamTag> = emptySet(),
    /** Tags the user made, such as feelings ("Anxious") or sleep notes ("Melatonin"), in the order added. */
    @ColumnInfo(defaultValue = "")
    val customTags: Set<String> = emptySet(),
    /** True when the entry was started from a ringing alarm rather than by hand. */
    val fromAlarm: Boolean = false,
)
