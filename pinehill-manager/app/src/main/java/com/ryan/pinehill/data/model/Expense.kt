package com.ryan.pinehill.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ExpenseCategory {
    REPAIR,      // 수리
    CLEANING,    // 청소
    SUPPLIES,    // 소모품
    MANAGEMENT,  // 관리비
    TAX,         // 세금
    BROKERAGE,   // 중개
    OTHER        // 기타
}

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val expenseId: Long = 0,
    val spentAt: Long,         // 지출 시각
    val amount: Long,          // 금액 (원)
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val memo: String = "",     // 메모
    val unitId: String? = null, // 특정 호실 관련이면 (공용이면 null)
    val month: String,         // YYYY-MM
    val source: ExpenseSource = ExpenseSource.MANUAL,
    val rawSms: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ExpenseSource {
    SMS, MANUAL
}
