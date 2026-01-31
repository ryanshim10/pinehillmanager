package com.ryan.pinehill.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tenants",
    foreignKeys = [
        ForeignKey(
            entity = Unit::class,
            parentColumns = ["unitId"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["unitId"])]
)
data class Tenant(
    @PrimaryKey
    val tenantKey: String,     // 이름_전화번호 (예: "민호택_01012345678")
    val name: String,
    val phone: String,
    val unitId: String,
    val createdAt: Long = System.currentTimeMillis()
)
