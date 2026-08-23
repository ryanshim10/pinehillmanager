package com.ryan.pinehill.data.dao

import androidx.room.*
import com.ryan.pinehill.data.model.ContractRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ContractDao {
    @Query("SELECT * FROM contract_records ORDER BY tenantName, startDate DESC, createdAt DESC")
    fun getAllContracts(): Flow<List<ContractRecord>>

    @Query("SELECT * FROM contract_records WHERE tenantName = :tenantName ORDER BY startDate DESC, createdAt DESC")
    fun getContractsByTenant(tenantName: String): Flow<List<ContractRecord>>

    @Query("SELECT * FROM contract_records WHERE unitId = :unitId ORDER BY startDate DESC, createdAt DESC")
    suspend fun getContractsByUnitNow(unitId: String): List<ContractRecord>

    @Query("SELECT * FROM contract_records ORDER BY contractId")
    suspend fun getAllContractsNow(): List<ContractRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContract(contract: ContractRecord): Long

    @Update
    suspend fun updateContract(contract: ContractRecord)

    @Query("DELETE FROM contract_records WHERE contractId = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM contract_records")
    suspend fun deleteAll()
}
