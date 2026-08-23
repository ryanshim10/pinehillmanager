package com.ryan.pinehill.data.dao

import androidx.room.*
import com.ryan.pinehill.data.model.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE month = :month ORDER BY spentAt DESC")
    fun getExpensesByMonth(month: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY expenseId")
    suspend fun getAllExpensesNow(): List<Expense>

    @Query("SELECT SUM(amount) FROM expenses WHERE month = :month")
    suspend fun getTotalExpenseByMonth(month: String): Long?

    @Query("SELECT COUNT(*) FROM expenses WHERE month = :month")
    suspend fun getExpenseCountByMonth(month: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<Expense>)

    @Update suspend fun updateExpense(expense: Expense)
    @Delete suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Query("SELECT DISTINCT month FROM expenses ORDER BY month DESC")
    fun getAvailableMonths(): Flow<List<String>>
}
