package com.speakerroom.tap2sound.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeakerDao {
    @Query("SELECT * FROM speakers ORDER BY createdAt DESC")
    fun getAllSpeakers(): Flow<List<Speaker>>

    @Query("SELECT * FROM speakers WHERE id = :id")
    suspend fun getSpeakerById(id: String): Speaker?

    @Query("SELECT * FROM speakers WHERE nfcUid = :nfcUid")
    suspend fun getSpeakerByNfcUid(nfcUid: String): Speaker?

    @Query("SELECT * FROM speakers WHERE btMac = :btMac")
    suspend fun getSpeakerByBtMac(btMac: String): Speaker?

    @Insert
    suspend fun insert(speaker: Speaker): Long

    @Update
    suspend fun update(speaker: Speaker)

    @Delete
    suspend fun delete(speaker: Speaker)

    @Query("DELETE FROM speakers")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM speakers")
    suspend fun count(): Int
}
