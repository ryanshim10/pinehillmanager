package com.ryan.pinehill.data.dao

import androidx.room.*
import com.ryan.pinehill.data.model.PaymentMatchRule
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentMatchRuleDao {
    @Query("SELECT * FROM payment_match_rules WHERE enabled = 1 ORDER BY bankName, senderName, amount, dayOfMonth")
    fun getRules(): Flow<List<PaymentMatchRule>>

    @Query("SELECT * FROM payment_match_rules WHERE enabled = 1")
    suspend fun getEnabledRulesNow(): List<PaymentMatchRule>

    @Query("SELECT * FROM payment_match_rules ORDER BY ruleId")
    suspend fun getAllRulesNow(): List<PaymentMatchRule>

    @Query("SELECT * FROM payment_match_rules WHERE bankName = :bankName AND senderName = :senderName AND amount = :amount AND dayOfMonth = :dayOfMonth LIMIT 1")
    suspend fun findSameKey(bankName: String, senderName: String, amount: Long, dayOfMonth: Int): PaymentMatchRule?

    @Query("DELETE FROM payment_match_rules WHERE bankName = :bankName AND senderName = :senderName AND amount = :amount AND dayOfMonth = :dayOfMonth")
    suspend fun deleteSameKey(bankName: String, senderName: String, amount: Long, dayOfMonth: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: PaymentMatchRule): Long

    @Update
    suspend fun updateRule(rule: PaymentMatchRule)

    @Delete
    suspend fun deleteRule(rule: PaymentMatchRule)

    @Query("DELETE FROM payment_match_rules")
    suspend fun deleteAll()
}
