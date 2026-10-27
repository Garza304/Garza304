package com.garza.dreamcatcher.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DreamDao {
    @Query("SELECT * FROM dreams ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Dream>>

    @Query("SELECT * FROM dreams ORDER BY createdAt DESC")
    suspend fun getAll(): List<Dream>

    /** Each dream's encoded custom tags, for [CustomTags.rank]. */
    @Query("SELECT customTags FROM dreams WHERE customTags != ''")
    fun observeCustomTagRows(): Flow<List<String>>

    @Query("SELECT * FROM dreams WHERE id = :id")
    suspend fun get(id: Long): Dream?

    @Insert
    suspend fun insert(dream: Dream): Long

    @Update
    suspend fun update(dream: Dream)

    @Delete
    suspend fun delete(dream: Dream)
}
