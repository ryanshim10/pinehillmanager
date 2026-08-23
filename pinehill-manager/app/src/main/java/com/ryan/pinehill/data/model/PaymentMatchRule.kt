package com.ryan.pinehill.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payment_match_rules",
    indices = [Index(value = ["bankName", "senderName", "amount", "dayOfMonth"], unique = true), Index("unitId")]
)
data class PaymentMatchRule(
    @PrimaryKey(autoGenerate = true)
    val ruleId: Long = 0,
    val bankName: String,
    val senderName: String,
    val amount: Long,
    val dayOfMonth: Int,
    val unitId: String,
    val tenantName: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
