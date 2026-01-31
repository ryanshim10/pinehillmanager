package com.ryan.pinehill.data.dao

import androidx.room.*
import com.ryan.pinehill.data.model.Payment
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE unitId = :unitId AND month = :month")
    fun getPaymentsByUnitAndMonth(unitId: String, month: String): Flow<List<Payment>>
    
    @Query("SELECT * FROM payments WHERE month = :month ORDER BY unitId")
    fun getPaymentsByMonth(month: String): Flow<List<Payment>>
    
    @Query("SELECT * FROM payments WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingPayments(): Flow<List<Payment>>
    
    @Query("SELECT SUM(amount) FROM payments WHERE unitId = :unitId AND month = :month AND status IN ('PAID', 'PARTIAL')")
    suspend fun getTotalPaidByUnitAndMonth(unitId: String, month: String): Long?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long
    
    @Update
    suspend fun updatePayment(payment: Payment)
    
    @Delete
    suspend fun deletePayment(payment: Payment)
    
    @Query("SELECT DISTINCT month FROM payments ORDER BY month DESC")
    fun getAvailableMonths(): Flow<List<String>>
}
