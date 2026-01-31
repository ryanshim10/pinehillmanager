package com.ryan.pinehill.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UnitStatus {
    RENTED,      // 임대중
    VACANT,      // 공실
    MAINTENANCE, // 정비중
    LAWSUIT,     // 소송
    OTHER        // 기타
}

@Entity(tableName = "units")
data class Unit(
    @PrimaryKey
    val unitId: String,        // 예: PINE-201
    val roomNo: Int,           // 예: 201
    val floor: Int,            // 예: 2
    val status: UnitStatus = UnitStatus.RENTED,
    val roomType: String? = null,      // 예: "1.5룸", "투룸"
    val targetPrice: String? = null,   // 예: "500-50" (보증금-월세)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getStatusText(): String = when (status) {
        UnitStatus.RENTED -> "임대중"
        UnitStatus.VACANT -> "공실"
        UnitStatus.MAINTENANCE -> "정비중"
        UnitStatus.LAWSUIT -> "소송"
        UnitStatus.OTHER -> "기타"
    }
}
