package com.smashams92.todayreward.data

import androidx.room.*

@Dao
interface DeedDao {

    @Query("SELECT * FROM deeds")
    suspend fun getAllDeeds(): List<GoodDeedEntity>

    @Query("SELECT * FROM deeds WHERE id = :id")
    suspend fun getById(id: Int): GoodDeedEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(deeds: List<GoodDeedEntity>)

    @Update
    suspend fun updateDeed(deed: GoodDeedEntity)

    @Delete
    suspend fun deleteDeed(deed: GoodDeedEntity)

    @Query("DELETE FROM deeds")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM deeds")
    suspend fun countDeeds(): Int
}
