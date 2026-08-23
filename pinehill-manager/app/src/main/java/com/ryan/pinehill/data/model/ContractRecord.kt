package com.ryan.pinehill.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contract_records",
    indices = [Index("tenantName"), Index("unitId"), Index("startDate"), Index("endDate")]
)
data class ContractRecord(
    @PrimaryKey(autoGenerate = true)
    val contractId: Long = 0,
    val tenantName: String,
    val tenantPhone: String = "",
    val unitId: String,
    val deposit: Long = 0,
    val monthlyRent: Long = 0,
    val paymentDay: Int = 0,
    val startDate: String = "",
    val endDate: String = "",
    val documentUri: String = "",
    val sourceName: String = "",
    val rawOcrText: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
