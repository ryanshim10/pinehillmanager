package com.ryan.pinehill.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PaymentStatus {
    PAID,        // 완납
    PARTIAL,     // 부분납
    UNPAID,      // 미납
    PENDING      // 확인필요
}

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = Tenant::class,
            parentColumns = ["tenantKey"],
            childColumns = ["tenantKey"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["tenantKey"]), Index(value = ["month"])]
)
data class Payment(
    @PrimaryKey(autoGenerate = true)
    val paymentId: Long = 0,
    val tenantKey: String?,    // 미확정이면 null
    val unitId: String,
    val month: String,         // YYYY-MM
    val paidAt: Long?,         // 입금 시각
    val amount: Long,          // 입금액 (원)
    val senderName: String? = null,  // 별명 (문자에 있으면)
    val source: PaymentSource = PaymentSource.MANUAL,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val statusOverride: Boolean = false,  // 사용자가 수동 수정했는지
    val rawSms: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getStatusText(): String = when (status) {
        PaymentStatus.PAID -> "완납"
        PaymentStatus.PARTIAL -> "부분납"
        PaymentStatus.UNPAID -> "미납"
        PaymentStatus.PENDING -> "확인필요"
    }
}

enum class PaymentSource {
    SMS, MANUAL
}
