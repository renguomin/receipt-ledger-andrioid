package com.receiptledger.engine.parse

object PaymentMethodParser {
    const val GEMINI_RULE =
        """paymentMethod: lowercase. Cards are "brand *1234" with one asterisk and the last 4 digits printed on the slip (visa *7403, mastercard *2322, amex *1009). Not a card: paypal, wechat, alipay, apple pay, google pay, cash, debit, interac. Brand only if last 4 are unreadable. null if unknown."""

    fun find(text: String): String? {
        val brand = findBrand(text) ?: return null
        val last4 = if (brand.card) last4(text) else null
        return format(brand.label, last4)
    }

    fun normalize(raw: String): String? = find(raw)

    fun prefer(geometry: String?, gemini: String?): String? {
        val geo = geometry?.let { normalize(it) }
        val gem = gemini?.let { normalize(it) }
        if (geo == null) return gem
        if (gem == null) return geo
        if (hasLast4(gem) && brandKey(geo) == brandKey(gem)) return gem
        return geo
    }

    fun brandKey(formatted: String): String = formatted.substringBefore(" *")

    fun format(brand: String, last4: String?): String {
        val digits = last4?.filter { it.isDigit() }?.takeLast(4)
        return if (digits != null && digits.length == 4) "$brand *$digits" else brand
    }

    fun last4(text: String): String? {
        val masked = MASKED_PAN.findAll(text).map { it.groupValues[1] }.toList()
        if (masked.isNotEmpty()) return masked.last()
        SINGLE_STAR.find(text)?.groupValues?.get(1)?.let { return it }
        ENDING_IN.find(text)?.groupValues?.get(1)?.let { return it }
        return null
    }

    fun hasLast4(formatted: String): Boolean = SINGLE_STAR.containsMatchIn(formatted)

    private fun findBrand(text: String): Brand? {
        for (brand in BRANDS) {
            if (brand.pattern.containsMatchIn(text)) return brand
        }
        return null
    }

    private data class Brand(
        val label: String,
        val card: Boolean,
        val pattern: Regex,
    )

    private val BRANDS = listOf(
        Brand("amex", true, Regex("""\b(AMERICAN\s+EXPRESS|AMEX)\b""", RegexOption.IGNORE_CASE)),
        Brand("mastercard", true, Regex("""\bMASTER\s*CARD\b|\bMASTERCARD\b|\bMASTER\b|\bMC\b""", RegexOption.IGNORE_CASE)),
        Brand("visa", true, Regex("""\b[VU]ISA\b""", RegexOption.IGNORE_CASE)),
        Brand("discover", true, Regex("""\bDISCOVER\b""", RegexOption.IGNORE_CASE)),
        Brand("unionpay", true, Regex("""\bUNION\s*PAY\b""", RegexOption.IGNORE_CASE)),
        Brand("paypal", false, Regex("""\bPAY\s*PAL\b""", RegexOption.IGNORE_CASE)),
        Brand("wechat", false, Regex("""\bWE\s*CHAT(?:\s*PAY)?\b|\bWEIXIN\b""", RegexOption.IGNORE_CASE)),
        Brand("alipay", false, Regex("""\bALI\s*PAY\b""", RegexOption.IGNORE_CASE)),
        Brand("apple pay", false, Regex("""\bAPPLE\s*PAY\b""", RegexOption.IGNORE_CASE)),
        Brand("google pay", false, Regex("""\bGOOGLE\s*PAY\b|\bG\s*PAY\b""", RegexOption.IGNORE_CASE)),
        Brand("interac", true, Regex("""\bINTERAC\b""", RegexOption.IGNORE_CASE)),
        Brand("debit", true, Regex("""\bDEBIT\b""", RegexOption.IGNORE_CASE)),
        Brand("cash", false, Regex("""\bCASH\b""", RegexOption.IGNORE_CASE)),
    )

    private val MASKED_PAN = Regex("""(?:\*{4,}|X{4,}|x{4,})(\d{4})(?!\d)""")
    private val SINGLE_STAR = Regex("""\*(\d{4})(?!\d)""")
    private val ENDING_IN = Regex("""\bending\s+(?:in\s+)?(\d{4})\b""", RegexOption.IGNORE_CASE)
}
