package com.receiptledger.engine.model

import kotlin.math.abs
import kotlin.math.round

/**
 * A blank quantity on a receipt line is a gap, not data: a line with no count printed next to it
 * is one unit. When a store sells more than one it says so in the line text, though every chain
 * words it differently ("x2", "2X", "QTY 2", "2 EA", "2 @ 3.99"). Failing that, a printed unit
 * price divides into the line total to give the count.
 */
object LineItemQuantity {
    const val DEFAULT = 1.0

    private const val MAX_QUANTITY = 999.0

    /** Unit price times count has to land on the printed total to the cent. */
    private const val CENT_TOLERANCE = 0.02

    private const val COUNT = """\d{1,3}(?:\.\d{1,3})?"""
    private const val MONEY = """\d{1,6}[.,]\d{2}"""

    private class MarkerPattern(
        val regex: Regex,
        val unitGroup: Int? = null,
    )

    /**
     * Only the very start or end of the line is read. A bare "x2" in the middle of a name belongs
     * to the product far more often than to the count — "WD-40 x2 Pack", "2X4 lumber", "XBOX2".
     */
    private val markers = listOf(
        // "Coffee 2 @ 3.99" — the unit price comes along for free.
        MarkerPattern(regex("""\s+($COUNT)\s*@\s*[$]?($MONEY)$"""), unitGroup = 2),
        // "Buns 2 FOR 5.00" — 5.00 is the bundle price, so it is not a unit price.
        MarkerPattern(regex("""\s+(\d{1,3})\s+for\s+[$]?$MONEY$""")),
        MarkerPattern(regex("""\s+[x×]\s*($COUNT)$""")),
        MarkerPattern(regex("""\s+($COUNT)\s*[x×]$""")),
        MarkerPattern(regex("""\s+(?:qty|quantity)\.?:?\s*($COUNT)$""")),
        MarkerPattern(regex("""\s+($COUNT)\s*(?:ea|each)$""")),
        MarkerPattern(regex("""^($COUNT)\s*@\s*[$]?($MONEY)\s+"""), unitGroup = 2),
        MarkerPattern(regex("""^($COUNT)\s*[x×]\s+""")),
        MarkerPattern(regex("""^[x×]\s*($COUNT)\s+""")),
        MarkerPattern(regex("""^(?:qty|quantity)\.?:?\s*($COUNT)\s+""")),
    )

    private val unitPrices = listOf(
        regex("""@\s*[$]?($MONEY)"""),
        regex("""($MONEY)\s*/?\s*(?:ea|each)\b"""),
    )

    data class Marker(
        val quantity: Double,
        val name: String,
        val unitPrice: Double? = null,
    )

    fun resolveAll(items: List<LineItem>): List<LineItem> = items.map { resolve(it) }

    /**
     * An explicit positive quantity always wins; markers and unit prices only fill a blank.
     */
    fun resolve(item: LineItem): LineItem {
        val marker = readMarker(item.name)
        val quantity = item.quantity?.takeIf { it > 0 }
            ?: marker?.quantity
            ?: fromUnitPrice(marker?.unitPrice ?: unitPrice(item.name), item.amount)
            ?: DEFAULT
        return item.copy(name = marker?.name ?: item.name, quantity = quantity)
    }

    internal fun readMarker(name: String): Marker? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        for (marker in markers) {
            val match = marker.regex.find(trimmed) ?: continue
            val quantity = match.groupValues[1].toDoubleOrNull() ?: continue
            if (quantity <= 0 || quantity > MAX_QUANTITY) continue
            val stripped = trimmed.removeRange(match.range).trim()
            return Marker(
                quantity = quantity,
                // Stripping the marker must never empty the name, or the line gets dropped later.
                name = stripped.ifEmpty { trimmed },
                unitPrice = marker.unitGroup?.let { money(match.groupValues[it]) },
            )
        }
        return null
    }

    internal fun unitPrice(name: String): Double? = unitPrices.firstNotNullOfOrNull { pattern ->
        pattern.find(name)?.let { money(it.groupValues[1]) }
    }

    internal fun fromUnitPrice(unitPrice: Double?, amount: Double?): Double? {
        if (unitPrice == null || unitPrice <= 0.0) return null
        if (amount == null || amount <= 0.0) return null
        val counted = round(amount / unitPrice)
        // A count of one is the default anyway, and anything that does not divide cleanly means
        // the amount was a unit price too, or the unit price was not really one.
        if (counted < 2.0 || counted > MAX_QUANTITY) return null
        if (abs(amount - counted * unitPrice) > CENT_TOLERANCE) return null
        return counted
    }

    private fun money(raw: String): Double? = raw.replace(',', '.').toDoubleOrNull()

    private fun regex(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)
}
