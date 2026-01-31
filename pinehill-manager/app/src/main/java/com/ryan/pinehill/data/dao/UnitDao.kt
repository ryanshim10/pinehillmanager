package com.ryan.pinehill.data.dao

import androidx.room.*
import com.ryan.pinehill.data.model.Unit
import kotlinx.coroutines.flow.Flow

@Dao
interface UnitDao {
    @Query("SELECT * FROM units ORDER BY unitId")
    fun getAllUnits(): Flow<List<Unit>>
    
    @Query("SELECT * FROM units WHERE unitId = :unitId")
    suspend fun getUnitById(unitId: String): Unit?
    
    @Query("SELECT * FROM units WHERE status = :status")
    fun getUnitsByStatus(status: String): Flow<List<Unit>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnit(unit: Unit)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnits(units: List<Unit>)
    
    @Update
    suspend fun updateUnit(unit: Unit)
    
    @Delete
    suspend fun deleteUnit(unit: Unit)
    
    @Query("SELECT COUNT(*) FROM units")
    suspend fun getUnitCount(): Int
}
