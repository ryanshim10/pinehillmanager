package com.ryan.pinehill.util

import androidx.room.withTransaction
import com.ryan.pinehill.data.AppDatabase
import com.ryan.pinehill.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object MarkdownBackup {
    const val FORMAT_VERSION = 1
    private const val FENCE = "```pinehill-json"

    suspend fun export(db: AppDatabase): String {
        val units = db.unitDao().getAllUnitsNow()
        val tenants = db.tenantDao().getAllTenantsNow()
        val payments = db.paymentDao().getAllPaymentsNow()
        val expenses = db.expenseDao().getAllExpensesNow()
        val contracts = db.contractDao().getAllContractsNow()
        val rules = db.paymentMatchRuleDao().getAllRulesNow()

        val root = JSONObject().apply {
            put("format", "pinehill-manager")
            put("version", FORMAT_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("units", JSONArray().apply { units.forEach { put(unitToJson(it)) } })
            put("tenants", JSONArray().apply { tenants.forEach { put(tenantToJson(it)) } })
            put("payments", JSONArray().apply { payments.forEach { put(paymentToJson(it)) } })
            put("expenses", JSONArray().apply { expenses.forEach { put(expenseToJson(it)) } })
            put("contracts", JSONArray().apply { contracts.forEach { put(contractToJson(it)) } })
            put("rules", JSONArray().apply { rules.forEach { put(ruleToJson(it)) } })
        }

        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())
        return buildString {
            appendLine("# Pinehill Manager Backup")
            appendLine()
            appendLine("- Exported: $date")
            appendLine("- Units: ${units.size}")
            appendLine("- Tenants: ${tenants.size}")
            appendLine("- Payments: ${payments.size}")
            appendLine("- Contracts: ${contracts.size}")
            appendLine("- Matching rules: ${rules.size}")
            appendLine()
            appendLine("> 이 파일에는 계약/입금 등 개인정보가 포함될 수 있습니다. 안전한 곳에 보관하세요.")
            appendLine()
            appendLine("## Machine-readable backup")
            appendLine(FENCE)
            appendLine(root.toString())
            appendLine("```")
        }
    }

    suspend fun restore(db: AppDatabase, markdown: String): RestoreResult {
        val jsonText = markdown.substringAfter(FENCE, "").substringBefore("```", "").trim()
        if (jsonText.isBlank()) return RestoreResult(false, "Pinehill 백업 데이터가 없습니다.")
        val root = runCatching { JSONObject(jsonText) }.getOrElse {
            return RestoreResult(false, "백업 JSON을 읽을 수 없습니다: ${it.message}")
        }
        if (root.optString("format") != "pinehill-manager") return RestoreResult(false, "지원하지 않는 백업 형식입니다.")

        val units = parseArray(root.optJSONArray("units")) { unitFromJson(it) }
        val tenants = parseArray(root.optJSONArray("tenants")) { tenantFromJson(it) }
        val payments = parseArray(root.optJSONArray("payments")) { paymentFromJson(it) }
        val expenses = parseArray(root.optJSONArray("expenses")) { expenseFromJson(it) }
        val contracts = parseArray(root.optJSONArray("contracts")) { contractFromJson(it) }
        val rules = parseArray(root.optJSONArray("rules")) { ruleFromJson(it) }

        db.withTransaction {
            db.paymentDao().deleteAll()
            db.expenseDao().deleteAll()
            db.tenantDao().deleteAll()
            db.contractDao().deleteAll()
            db.paymentMatchRuleDao().deleteAll()
            db.unitDao().deleteAll()

            db.unitDao().insertUnits(units)
            db.tenantDao().insertTenants(tenants)
            db.paymentDao().insertPayments(payments)
            db.expenseDao().insertExpenses(expenses)
            contracts.forEach { db.contractDao().insertContract(it) }
            rules.forEach { db.paymentMatchRuleDao().insertRule(it) }
        }
        return RestoreResult(true, "복원 완료: 호실 ${units.size}, 입금 ${payments.size}, 계약 ${contracts.size}건")
    }

    data class RestoreResult(val success: Boolean, val message: String)

    private fun unitToJson(u: Unit) = JSONObject().apply {
        put("unitId", u.unitId); put("roomNo", u.roomNo); put("floor", u.floor); put("status", u.status.name)
        put("roomType", u.roomType ?: JSONObject.NULL); put("targetPrice", u.targetPrice ?: JSONObject.NULL)
        put("createdAt", u.createdAt); put("updatedAt", u.updatedAt)
    }
    private fun unitFromJson(j: JSONObject) = Unit(
        unitId=j.getString("unitId"), roomNo=j.getInt("roomNo"), floor=j.getInt("floor"),
        status=UnitStatus.valueOf(j.optString("status", UnitStatus.RENTED.name)),
        roomType=j.optNullableString("roomType"), targetPrice=j.optNullableString("targetPrice"),
        createdAt=j.optLong("createdAt", System.currentTimeMillis()), updatedAt=j.optLong("updatedAt", System.currentTimeMillis())
    )

    private fun tenantToJson(t: Tenant) = JSONObject().apply {
        put("tenantKey", t.tenantKey); put("name", t.name); put("phone", t.phone); put("unitId", t.unitId); put("createdAt", t.createdAt)
    }
    private fun tenantFromJson(j: JSONObject) = Tenant(
        tenantKey=j.getString("tenantKey"), name=j.optString("name"), phone=j.optString("phone"),
        unitId=j.getString("unitId"), createdAt=j.optLong("createdAt", System.currentTimeMillis())
    )

    private fun paymentToJson(p: Payment) = JSONObject().apply {
        put("paymentId",p.paymentId); put("tenantKey",p.tenantKey ?: JSONObject.NULL); put("unitId",p.unitId); put("month",p.month)
        put("paidAt",p.paidAt ?: JSONObject.NULL); put("amount",p.amount); put("senderName",p.senderName ?: JSONObject.NULL)
        put("source",p.source.name); put("status",p.status.name); put("statusOverride",p.statusOverride)
        put("rawSms",p.rawSms ?: JSONObject.NULL); put("createdAt",p.createdAt)
    }
    private fun paymentFromJson(j: JSONObject) = Payment(
        paymentId=j.optLong("paymentId"), tenantKey=j.optNullableString("tenantKey"), unitId=j.optString("unitId"),
        month=j.optString("month"), paidAt=j.optNullableLong("paidAt"), amount=j.optLong("amount"),
        senderName=j.optNullableString("senderName"), source=PaymentSource.valueOf(j.optString("source", PaymentSource.MANUAL.name)),
        status=PaymentStatus.valueOf(j.optString("status", PaymentStatus.PENDING.name)), statusOverride=j.optBoolean("statusOverride"),
        rawSms=j.optNullableString("rawSms"), createdAt=j.optLong("createdAt", System.currentTimeMillis())
    )

    private fun expenseToJson(e: Expense) = JSONObject().apply {
        put("expenseId",e.expenseId); put("spentAt",e.spentAt); put("amount",e.amount); put("category",e.category.name)
        put("memo",e.memo); put("unitId",e.unitId ?: JSONObject.NULL); put("month",e.month); put("source",e.source.name)
        put("rawSms",e.rawSms ?: JSONObject.NULL); put("createdAt",e.createdAt)
    }
    private fun expenseFromJson(j: JSONObject) = Expense(
        expenseId=j.optLong("expenseId"), spentAt=j.optLong("spentAt"), amount=j.optLong("amount"),
        category=ExpenseCategory.valueOf(j.optString("category", ExpenseCategory.OTHER.name)), memo=j.optString("memo"),
        unitId=j.optNullableString("unitId"), month=j.optString("month"), source=ExpenseSource.valueOf(j.optString("source", ExpenseSource.MANUAL.name)),
        rawSms=j.optNullableString("rawSms"), createdAt=j.optLong("createdAt", System.currentTimeMillis())
    )

    private fun contractToJson(c: ContractRecord) = JSONObject().apply {
        put("contractId",c.contractId); put("tenantName",c.tenantName); put("tenantPhone",c.tenantPhone); put("unitId",c.unitId)
        put("deposit",c.deposit); put("monthlyRent",c.monthlyRent); put("paymentDay",c.paymentDay); put("startDate",c.startDate); put("endDate",c.endDate)
        put("documentUri",c.documentUri); put("sourceName",c.sourceName); put("rawOcrText",c.rawOcrText); put("notes",c.notes)
        put("createdAt",c.createdAt); put("updatedAt",c.updatedAt)
    }
    private fun contractFromJson(j: JSONObject) = ContractRecord(
        contractId=j.optLong("contractId"), tenantName=j.optString("tenantName"), tenantPhone=j.optString("tenantPhone"), unitId=j.optString("unitId"),
        deposit=j.optLong("deposit"), monthlyRent=j.optLong("monthlyRent"), paymentDay=j.optInt("paymentDay"), startDate=j.optString("startDate"), endDate=j.optString("endDate"),
        documentUri=j.optString("documentUri"), sourceName=j.optString("sourceName"), rawOcrText=j.optString("rawOcrText"), notes=j.optString("notes"),
        createdAt=j.optLong("createdAt",System.currentTimeMillis()), updatedAt=j.optLong("updatedAt",System.currentTimeMillis())
    )

    private fun ruleToJson(r: PaymentMatchRule) = JSONObject().apply {
        put("ruleId",r.ruleId); put("bankName",r.bankName); put("senderName",r.senderName); put("amount",r.amount); put("dayOfMonth",r.dayOfMonth)
        put("unitId",r.unitId); put("tenantName",r.tenantName); put("enabled",r.enabled); put("createdAt",r.createdAt); put("updatedAt",r.updatedAt)
    }
    private fun ruleFromJson(j: JSONObject) = PaymentMatchRule(
        ruleId=j.optLong("ruleId"), bankName=j.optString("bankName"), senderName=j.optString("senderName"), amount=j.optLong("amount"), dayOfMonth=j.optInt("dayOfMonth"),
        unitId=j.optString("unitId"), tenantName=j.optString("tenantName"), enabled=j.optBoolean("enabled",true),
        createdAt=j.optLong("createdAt",System.currentTimeMillis()), updatedAt=j.optLong("updatedAt",System.currentTimeMillis())
    )

    private inline fun <T> parseArray(array: JSONArray?, mapper: (JSONObject) -> T): List<T> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i -> runCatching { mapper(array.getJSONObject(i)) }.getOrNull() }
    }
    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() }
    private fun JSONObject.optNullableLong(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
}
