package com.receiptledger.engine.parse

data class MoneyMatch(
    val value: Double,
    val start: Int,
    val end: Int,
    val raw: String,
)

object MoneyParser {
    private val token = Regex(
        """(?:CAD|USD|C\$|US\$|\$)?\s*(\d{1,3}(?:[ ,]\d{3})+|\d+)([.,])(\d{2})(?!\d)""",
        RegexOption.IGNORE_CASE,
    )

    fun findAll(text: String): List<MoneyMatch> {
        return token.findAll(text).mapNotNull { match ->
            val whole = match.groupValues[1].replace(" ", "").replace(",", "")
            val decimal = match.groupValues[3]
            val joined = "$whole.$decimal"
            val value = joined.toDoubleOrNull() ?: return@mapNotNull null
            MoneyMatch(value, match.range.first, match.range.last + 1, match.value.trim())
        }.toList()
    }

    fun lastAmount(text: String): Double? = findAll(text).lastOrNull()?.value
}
