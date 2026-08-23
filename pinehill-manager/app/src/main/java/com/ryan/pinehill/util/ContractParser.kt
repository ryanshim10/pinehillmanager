package com.ryan.pinehill.util

data class ContractDraft(
    val tenantName: String = "",
    val tenantPhone: String = "",
    val roomNo: Int? = null,
    val deposit: Long = 0,
    val monthlyRent: Long = 0,
    val paymentDay: Int = 0,
    val startDate: String = "",
    val endDate: String = "",
    val rawText: String = ""
)

object ContractParser {
    fun parse(text: String): ContractDraft {
        val clean = text.replace('\u00A0', ' ').replace(Regex("[ \\t]+"), " ")
        val tenantName = listOf(
            "임차인\\s*(?:성명|이름)?\\s*[:：]?\\s*([가-힣]{2,5})".toRegex(),
            "성명\\s*[:：]?\\s*([가-힣]{2,5})".toRegex()
        ).firstNotNullOfOrNull { it.find(clean)?.groupValues?.getOrNull(1) }.orEmpty()

        val phone = "(01[016789][- ]?\\d{3,4}[- ]?\\d{4})".toRegex().find(clean)
            ?.groupValues?.getOrNull(1)?.replace(" ", "").orEmpty()

        val roomNo = listOf(
            "(\\d{2,4})\\s*호".toRegex(),
            "호실\\s*[:：]?\\s*(\\d{2,4})".toRegex()
        ).firstNotNullOfOrNull { it.find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull() }

        val deposit = findMoney(clean, listOf("보증금", "임대보증금"))
        val rent = findMoney(clean, listOf("월세", "차임", "월 차임", "월 임대료"))
        val paymentDay = listOf(
            "매월\\s*(\\d{1,2})\\s*일".toRegex(),
            "지급일\\s*[:：]?\\s*(\\d{1,2})\\s*일".toRegex(),
            "납부일\\s*[:：]?\\s*(\\d{1,2})\\s*일".toRegex()
        ).firstNotNullOfOrNull { it.find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull() } ?: 0

        val dates = "(20\\d{2})[.\\-/년 ]+\\s*(\\d{1,2})[.\\-/월 ]+\\s*(\\d{1,2})\\s*일?".toRegex()
            .findAll(clean).map { m ->
                "%04d-%02d-%02d".format(
                    m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()
                )
            }.distinct().toList()

        return ContractDraft(
            tenantName = tenantName,
            tenantPhone = phone,
            roomNo = roomNo,
            deposit = deposit,
            monthlyRent = rent,
            paymentDay = paymentDay.coerceIn(0, 31),
            startDate = dates.getOrNull(0).orEmpty(),
            endDate = dates.getOrNull(1).orEmpty(),
            rawText = text
        )
    }

    private fun findMoney(text: String, labels: List<String>): Long {
        for (label in labels) {
            val patterns = listOf(
                "$label\\s*(?:금)?\\s*[:：]?\\s*([\\d,]+)\\s*원".toRegex(),
                "$label[^\\d]{0,12}([\\d,]+)".toRegex()
            )
            for (pattern in patterns) {
                val raw = pattern.find(text)?.groupValues?.getOrNull(1) ?: continue
                raw.replace(",", "").toLongOrNull()?.let { return it }
            }
        }
        return 0
    }
}
