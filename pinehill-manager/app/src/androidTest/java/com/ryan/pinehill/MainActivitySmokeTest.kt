package com.ryan.pinehill

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ryan.pinehill.data.AppDatabase
import com.ryan.pinehill.data.model.ContractRecord
import com.ryan.pinehill.data.model.Payment
import com.ryan.pinehill.data.model.PaymentSource
import com.ryan.pinehill.data.model.PaymentStatus
import com.ryan.pinehill.data.model.Unit as RentalUnit
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainTabs_launchWithoutCrash() {
        composeRule.onNodeWithText("월세 입금 확인").fetchSemanticsNode()

        composeRule.onNodeWithText("계약").performClick()
        composeRule.onNodeWithText("세입자 · 계약 이력").fetchSemanticsNode()

        composeRule.onNodeWithText("백업").performClick()
        composeRule.onNodeWithText("백업 · 복원").fetchSemanticsNode()

        composeRule.onNodeWithText("월세").performClick()
        composeRule.onNodeWithText("월세 입금 확인").fetchSemanticsNode()
    }

    @Test
    fun roomTap_opens24MonthHistoryWithMatchedSmsPayment() {
        val db = AppDatabase.getDatabase(composeRule.activity.application)
        val now = System.currentTimeMillis()
        val month = SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date(now))

        runBlocking {
            db.unitDao().insertUnit(
                RentalUnit(
                    unitId = "PINE-990",
                    roomNo = 990,
                    floor = 9
                )
            )
            db.contractDao().insertContract(
                ContractRecord(
                    tenantName = "테스트세입자",
                    unitId = "PINE-990",
                    monthlyRent = 500_000,
                    paymentDay = 5,
                    startDate = "2026-01-01",
                    endDate = "2027-12-31"
                )
            )
            db.paymentDao().insertPayment(
                Payment(
                    tenantKey = null,
                    unitId = "PINE-990",
                    month = month,
                    paidAt = now,
                    amount = 500_000,
                    senderName = "테스트세입자",
                    source = PaymentSource.SMS,
                    status = PaymentStatus.PAID,
                    statusOverride = true,
                    rawSms = "[카카오뱅크] 입금 500,000원 테스트세입자"
                )
            )
        }

        composeRule.onNodeWithText("호실").performClick()
        composeRule.onNodeWithText("호실별 월세 현황").fetchSemanticsNode()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("990호").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("990호")[0].performClick()

        composeRule.onNodeWithText("990호 월세 히스토리").fetchSemanticsNode()
        composeRule.onNodeWithText("완납").fetchSemanticsNode()
        composeRule.onNodeWithText("테스트세입자", substring = true).fetchSemanticsNode()
        composeRule.onNodeWithText("500,000원", substring = true).fetchSemanticsNode()
    }
}
