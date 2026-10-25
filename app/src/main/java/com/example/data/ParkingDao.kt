package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkingDao {
    @Query("SELECT * FROM parking_config WHERE id = 1 LIMIT 1")
    fun getConfigFlow(): Flow<ParkingConfig?>

    @Query("SELECT * FROM parking_config WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): ParkingConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: ParkingConfig)

    @Query("SELECT * FROM parking_history ORDER BY endTimestamp DESC LIMIT 1")
    suspend fun getLastHistoryItem(): ParkingHistoryItem?

    @Query("SELECT * FROM parking_history ORDER BY endTimestamp DESC")
    fun getAllHistory(): Flow<List<ParkingHistoryItem>>

    @Query("SELECT * FROM parking_history ORDER BY endTimestamp DESC")
    suspend fun getAllHistoryList(): List<ParkingHistoryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: ParkingHistoryItem)

    @Query("DELETE FROM parking_history")
    suspend fun clearAllHistory()

    @Query("DELETE FROM parking_history WHERE id = :id")
    suspend fun deleteHistoryItem(id: Long)

    // Recharge History operations
    @Query("SELECT * FROM recharge_history ORDER BY timestamp DESC")
    fun getAllRecharges(): Flow<List<RechargeHistoryItem>>

    @Query("SELECT * FROM recharge_history ORDER BY timestamp DESC")
    suspend fun getAllRechargesList(): List<RechargeHistoryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecharge(item: RechargeHistoryItem): Long

    @Update
    suspend fun updateRecharge(item: RechargeHistoryItem)

    @Query("DELETE FROM recharge_history WHERE id = :id")
    suspend fun deleteRecharge(id: Long)

    @Query("DELETE FROM recharge_history")
    suspend fun clearAllRecharges()
}
