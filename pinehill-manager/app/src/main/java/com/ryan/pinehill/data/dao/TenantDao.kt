package com.ryan.pinehill.data.dao

import androidx.room.*
import com.ryan.pinehill.data.model.Tenant
import kotlinx.coroutines.flow.Flow

@Dao
interface TenantDao {
    @Query("SELECT * FROM tenants WHERE unitId = :unitId")
    fun getTenantsByUnit(unitId: String): Flow<List<Tenant>>
    
    @Query("SELECT * FROM tenants WHERE tenantKey = :tenantKey")
    suspend fun getTenantByKey(tenantKey: String): Tenant?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTenant(tenant: Tenant)
    
    @Update
    suspend fun updateTenant(tenant: Tenant)
    
    @Delete
    suspend fun deleteTenant(tenant: Tenant)
}
