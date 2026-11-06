package com.receiptledger.engine.parse

import com.receiptledger.engine.alias.MerchantAliasBook
import com.receiptledger.engine.model.Country
import com.receiptledger.engine.model.Field
import com.receiptledger.engine.model.FieldSource
import com.receiptledger.engine.model.OcrLine
import com.receiptledger.engine.model.OcrPage
import com.receiptledger.engine.model.ParsedReceipt
import com.receiptledger.engine.model.TaxAmount
import com.receiptledger.engine.model.TaxKind
import com.receiptledger.engine.tax.CategorySuggester

class GeometryReceiptParser(
    private val aliases: MerchantAliasBook = MerchantAliasBook(),
    private val country: Country = Country.CA,
) {
    fun parse(page: OcrPage): ParsedReceipt {
        val lines = page.lines.filter { it.text.isNotBlank() }
        val raw = page.fullText
        val taxes = findTaxes(lines)
        val total = findLabeledAmount(lines, TOTAL_LABEL) ?: inferTotal(lines, taxes)
        val subtotal = findLabeledAmount(lines, SUBTOTAL_LABEL)
            ?: inferSubtotal(total, taxes)
        val merchantRaw = GeometryMerchant.guess(lines, page.height)
        val aliasHit = aliases.learnedCanonicalOrNull(merchantRaw)
        val merchant = when {
            aliasHit != null -> Field(aliasHit, 0.92f, FieldSource.ALIAS)
            !merchantRaw.isNullOrBlank() -> Field(merchantRaw, 0.55f, FieldSource.GEOMETRY)
            else -> Field.missing()
        }
        val date = DateParser.findIsoDate(raw)?.let {
            Field(it, 0.8f, FieldSource.GEOMETRY)
        } ?: Field.missing()
        val currency = guessCurrency(raw, taxes)
        val payment = findPayment(raw)
        val gstNumber = findGstHstNumber(raw)
        val receiptNumber = ReceiptNumberParser.find(lines)?.let {
            Field(it, 0.82f, FieldSource.GEOMETRY)
        } ?: Field.missing()
        val category = CategorySuggester.suggest(
            country = country,
            merchant = merchant.value,
            rawText = raw,
        )
        return ParsedReceipt(
            merchant = merchant,
            date = date,
            currency = currency,
            subtotal = subtotal ?: Field.missing(),
            taxes = taxes,
            total = total ?: Field.missing(),
            paymentMethod = payment,
            gstHstNumber = gstNumber,
            receiptNumber = receiptNumber,
            suggestedCategoryId = category,
            rawText = raw,
        )
    }

    private fun findTaxes(lines: List<OcrLine>): List<TaxAmount> {
        val found = mutableListOf<TaxAmount>()
        for (line in lines) {
            val kind = taxKindIn(line.text) ?: continue
            val amount = MoneyParser.lastAmount(line.text) ?: continue
            val rate = PERCENT.find(line.text)?.groupValues?.get(1)?.toDoubleOrNull()?.div(100.0)
            found += TaxAmount(
                kind = kind,
                amount = amount,
                rate = rate,
                confidence = 0.85f,
                source = FieldSource.GEOMETRY,
            )
        }
        return found.distinctBy { it.kind to it.amount }
    }

    private fun findLabeledAmount(
        lines: List<OcrLine>,
        labels: Regex,
    ): Field<Double>? {
        val matches = lines.mapNotNull { line ->
            if (!labels.containsMatchIn(line.text)) return@mapNotNull null
            if (taxKindIn(line.text) != null && !labels.pattern.contains("tax", ignoreCase = true)) {
                return@mapNotNull null
            }
            val amount = MoneyParser.lastAmount(line.text) ?: return@mapNotNull null
            line to amount
        }
        if (matches.isEmpty()) return null
        val chosen = matches.maxBy { it.first.box.centerY }
        return Field(chosen.second, 0.88f, FieldSource.GEOMETRY)
    }

    private fun inferTotal(lines: List<OcrLine>, taxes: List<TaxAmount>): Field<Double>? {
        val amounts = lines.mapNotNull { line ->
            val value = MoneyParser.lastAmount(line.text) ?: return@mapNotNull null
            line to value
        }
        if (amounts.isEmpty()) return null
        val nearBottom = amounts.filter { it.first.box.centerY >= amounts.maxOf { a -> a.first.box.centerY } * 0.6 }
        val candidate = nearBottom.maxByOrNull { it.second } ?: amounts.maxBy { it.second }
        val confidence = if (taxes.isNotEmpty()) 0.55f else 0.4f
        return Field(candidate.second, confidence, FieldSource.GEOMETRY)
    }

    private fun inferSubtotal(total: Field<Double>?, taxes: List<TaxAmount>): Field<Double>? {
        val t = total?.value ?: return null
        if (taxes.isEmpty()) return null
        val sub = t - taxes.sumOf { it.amount }
        if (sub <= 0) return null
        return Field(roundMoney(sub), 0.7f, FieldSource.GEOMETRY)
    }

    private fun guessCurrency(raw: String, taxes: List<TaxAmount>): Field<String> {
        val canadianTax = taxes.any {
            it.kind in setOf(TaxKind.GST, TaxKind.HST, TaxKind.PST, TaxKind.QST, TaxKind.TPS, TaxKind.TVQ)
        }
        return when {
            canadianTax || CAD.containsMatchIn(raw) -> Field("CAD", 0.9f, FieldSource.GEOMETRY)
            USD.containsMatchIn(raw) -> Field("USD", 0.85f, FieldSource.GEOMETRY)
            country == Country.CA -> Field("CAD", 0.5f, FieldSource.GEOMETRY)
            country == Country.US -> Field("USD", 0.5f, FieldSource.GEOMETRY)
            else -> Field.missing()
        }
    }

    private fun findPayment(raw: String): Field<String> {
        val value = PaymentMethodParser.find(raw) ?: return Field.missing()
        val confidence = if (PaymentMethodParser.hasLast4(value)) 0.82f else 0.75f
        return Field(value, confidence, FieldSource.GEOMETRY)
    }

    private fun findGstHstNumber(raw: String): Field<String> {
        val compact = raw.replace(" ", "")
        val match = GST_NUMBER.find(compact) ?: return Field.missing()
        return Field(match.groupValues[1].uppercase(), 0.8f, FieldSource.GEOMETRY)
    }

    private fun taxKindIn(text: String): TaxKind? {
        val upper = text.uppercase()
        return when {
            upper.contains("HST") -> TaxKind.HST
            upper.contains("GST") || Regex("\\bTPS\\b").containsMatchIn(upper) ->
                if (upper.contains("TPS")) TaxKind.TPS else TaxKind.GST
            upper.contains("PST") -> TaxKind.PST
            upper.contains("QST") || Regex("\\bTVQ\\b").containsMatchIn(upper) ->
                if (upper.contains("TVQ")) TaxKind.TVQ else TaxKind.QST
            Regex("\\bSALES\\s*TAX\\b").containsMatchIn(upper) -> TaxKind.SALES_TAX
            Regex("\\bTAX\\b").containsMatchIn(upper) &&
                !SUBTOTAL_LABEL.containsMatchIn(text) &&
                !TOTAL_LABEL.containsMatchIn(text) -> TaxKind.UNKNOWN
            else -> null
        }
    }

    private fun roundMoney(value: Double): Double = Math.round(value * 100.0) / 100.0

    companion object {
        private val TOTAL_LABEL = Regex(
            """\b(grand\s*)?total\b|\bamount\s*due\b|\bbalance\s*due\b|\btotal\s*a\s*payer\b""",
            RegexOption.IGNORE_CASE,
        )
        private val SUBTOTAL_LABEL = Regex(
            """\bsub[\s-]*total\b|\bsous[\s-]*total\b""",
            RegexOption.IGNORE_CASE,
        )
        private val PERCENT = Regex("""(\d{1,2}(?:\.\d{1,2})?)\s*%""")
        private val CAD = Regex("""\bCAD\b|C\$""", RegexOption.IGNORE_CASE)
        private val USD = Regex("""\bUSD\b|US\$""", RegexOption.IGNORE_CASE)
        private val GST_NUMBER = Regex("""(?<!\d)(\d{9}RT\d{4})(?!\d)""", RegexOption.IGNORE_CASE)
    }
}
