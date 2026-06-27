package com.knightlsy.douyin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HistoryDao {
    @Query("SELECT * FROM download_history ORDER BY downloadTime DESC")
    fun getAll(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: HistoryEntity)

    @Query("DELETE FROM download_history WHERE id IN (:ids)")
    fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM download_history")
    fun deleteAll()
}
