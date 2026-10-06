package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkingDao {
    @Query("SELECT * FROM parking_config WHERE id = 1 LIMIT 1")
    fun getConfigFlow(): Flow<ParkingConfig?>

    @Query("SELECT * FROM parking_config WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): ParkingConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: ParkingConfig)

    @Query("SELECT * FROM parking_history ORDER BY endTimestamp DESC")
    fun getAllHistory(): Flow<List<ParkingHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: ParkingHistoryItem)

    @Query("DELETE FROM parking_history")
    suspend fun clearAllHistory()
}
