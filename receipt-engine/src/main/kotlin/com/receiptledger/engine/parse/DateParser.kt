package com.receiptledger.engine.parse

import com.receiptledger.engine.parse.DateFormatCatalog.NumericOrder
import java.time.DateTimeException
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object DateParser {
    fun findIsoDate(text: String, today: LocalDate = LocalDate.now()): String? {
        val normalized = normalize(text)
        for (candidate in structuredCandidates(normalized)) {
            parse(candidate, today)?.let { return it.toString() }
        }
        for (digits in COMPACT_8.findAll(normalized).map { it.value }) {
            parseCompact(digits, today)?.let { return it.toString() }
        }
        return null
    }

    fun parse(raw: String, today: LocalDate = LocalDate.now()): LocalDate? {
        val cleaned = stripWeekday(normalize(raw)).trim()
        if (cleaned.isEmpty()) return null
        parseChinese(cleaned, today)?.let { return it }
        parseNamedMonth(cleaned, today)?.let { return it }
        parseNumeric(cleaned, today)?.let { return it }
        val digits = cleaned.filter { it.isDigit() }
        if (digits.length == 8 && digits == cleaned) {
            parseCompact(digits, today)?.let { return it }
        }
        return null
    }

    private fun structuredCandidates(text: String): List<String> {
        val found = LinkedHashSet<String>()
        CHINESE.findAll(text).mapTo(found) { it.value }
        NAMED_DMY.findAll(text).mapTo(found) { it.value }
        NAMED_MDY.findAll(text).mapTo(found) { it.value }
        NAMED_YMD.findAll(text).mapTo(found) { it.value }
        GLUED_YEAR2.findAll(text).mapTo(found) { it.groupValues[1] }
        NUMERIC.findAll(text).mapTo(found) { it.groupValues[1] }
        return found.toList()
    }

    private fun parseNumeric(raw: String, today: LocalDate): LocalDate? {
        val match = NUMERIC_EXACT.matchEntire(raw) ?: return null
        val a = match.groupValues[1].toIntOrNull() ?: return null
        val b = match.groupValues[2].toIntOrNull() ?: return null
        val c = match.groupValues[3].toIntOrNull() ?: return null
        return pick(numericInterpretations(a, b, c, today), today)
    }

    private fun numericInterpretations(
        first: Int,
        second: Int,
        third: Int,
        today: LocalDate,
    ): List<LocalDate> {
        val dates = LinkedHashSet<LocalDate>()
        for (order in DateFormatCatalog.numericOrders) {
            val (yearRaw, month, day) = when (order) {
                NumericOrder.DAY_MONTH_YEAR -> Triple(third, second, first)
                NumericOrder.MONTH_DAY_YEAR -> Triple(third, first, second)
                NumericOrder.YEAR_MONTH_DAY -> Triple(first, second, third)
            }
            val year = expandYear(yearRaw, today) ?: continue
            dateOrNull(year, month, day)?.let { dates += it }
        }
        return dates.toList()
    }

    private fun parseNamedMonth(raw: String, today: LocalDate): LocalDate? {
        NAMED_DMY.matchEntire(raw)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@let
            val month = DateFormatCatalog.monthNumber(match.groupValues[2]) ?: return@let
            val year = expandYear(match.groupValues[3].toIntOrNull() ?: return@let, today) ?: return@let
            return dateOrNull(year, month, day)
        }
        NAMED_MDY.matchEntire(raw)?.let { match ->
            val month = DateFormatCatalog.monthNumber(match.groupValues[1]) ?: return@let
            val day = match.groupValues[2].toIntOrNull() ?: return@let
            val year = expandYear(match.groupValues[3].toIntOrNull() ?: return@let, today) ?: return@let
            return dateOrNull(year, month, day)
        }
        NAMED_YMD.matchEntire(raw)?.let { match ->
            val year = expandYear(match.groupValues[1].toIntOrNull() ?: return@let, today) ?: return@let
            val month = DateFormatCatalog.monthNumber(match.groupValues[2]) ?: return@let
            val day = match.groupValues[3].toIntOrNull() ?: return@let
            return dateOrNull(year, month, day)
        }
        return null
    }

    private fun parseChinese(raw: String, today: LocalDate): LocalDate? {
        val match = CHINESE_EXACT.matchEntire(raw) ?: return null
        val year = expandYear(match.groupValues[1].toIntOrNull() ?: return null, today) ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        return dateOrNull(year, month, day)
    }

    private fun parseCompact(digits: String, today: LocalDate): LocalDate? {
        if (digits.length != 8) return null
        val dates = LinkedHashSet<LocalDate>()
        dateOrNull(
            digits.substring(0, 4).toInt(),
            digits.substring(4, 6).toInt(),
            digits.substring(6, 8).toInt(),
        )?.let { dates += it }
        val year = expandYear(digits.substring(4, 8).toInt(), today)
        if (year != null) {
            dateOrNull(year, digits.substring(2, 4).toInt(), digits.substring(0, 2).toInt())
                ?.let { dates += it }
            dateOrNull(year, digits.substring(0, 2).toInt(), digits.substring(2, 4).toInt())
                ?.let { dates += it }
        }
        return pick(dates.toList(), today)
    }

    private fun pick(dates: List<LocalDate>, today: LocalDate): LocalDate? {
        if (dates.isEmpty()) return null
        val latest = today.plusDays(1)
        val earliest = today.minusYears(DateFormatCatalog.RECENT_YEARS)
        val recent = dates.filter { !it.isAfter(latest) && !it.isBefore(earliest) }
        if (recent.isNotEmpty()) return recent.first()
        return dates.minBy { abs(ChronoUnit.DAYS.between(today, it)) }
    }

    private fun expandYear(value: Int, today: LocalDate): Int? {
        if (value in DateFormatCatalog.MIN_YEAR..DateFormatCatalog.MAX_YEAR) return value
        if (value !in 0..99) return null
        val as2000 = 2000 + value
        return if (as2000 <= today.year + 1) as2000 else 1900 + value
    }

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? {
        if (year !in DateFormatCatalog.MIN_YEAR..DateFormatCatalog.MAX_YEAR) return null
        return try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun stripWeekday(raw: String): String {
        val match = WEEKDAY_PREFIX.find(raw) ?: return raw
        return raw.substring(match.range.last + 1)
    }

    private fun normalize(text: String): String =
        text
            .replace('／', '/')
            .replace('－', '-')
            .replace('–', '-')
            .replace('—', '-')
            .replace('．', '.')
            .replace(SPACES_AROUND_SEP, "$1")

    private val MONTH_ALT = DateFormatCatalog.monthSpellings
        .map { it.first }
        .distinct()
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }

    private val WEEKDAY_ALT = DateFormatCatalog.weekdays
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }

    private val NAMED_FLAGS = setOf(RegexOption.IGNORE_CASE)

    private val WEEKDAY_PREFIX = Regex(
        """^($WEEKDAY_ALT)\s*,\s*""",
        NAMED_FLAGS,
    )

    private val NAMED_DMY = Regex(
        """\b(\d{1,2})(?:st|nd|rd|th)?[.\-/\s]+($MONTH_ALT)[.]?[.\-/\s]+(\d{2,4})\b""",
        NAMED_FLAGS,
    )
    private val NAMED_MDY = Regex(
        """\b($MONTH_ALT)[.]?[.\-/\s]+(\d{1,2})(?:st|nd|rd|th)?,?[.\-/\s]+(\d{2,4})\b""",
        NAMED_FLAGS,
    )
    private val NAMED_YMD = Regex(
        """\b(\d{4})[.\-/\s]+($MONTH_ALT)[.]?[.\-/\s]+(\d{1,2})(?:st|nd|rd|th)?\b""",
        NAMED_FLAGS,
    )
    private val NUMERIC = Regex(
        """\b(\d{1,4}\s*[/.\-]\s*\d{1,2}\s*[/.\-]\s*\d{1,4})\b""",
    )
    private val NUMERIC_EXACT = Regex(
        """^(\d{1,4})[/.\-](\d{1,2})[/.\-](\d{1,4})$""",
    )
    private val GLUED_YEAR2 = Regex(
        """\b(\d{1,2}\s*[/.\-]\s*\d{1,2}\s*[/.\-]\s*\d{2})(?=\s*\d{0,2}:\d{2})""",
    )
    private val CHINESE = Regex("""(\d{4})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]?""")
    private val CHINESE_EXACT = Regex("""^(\d{4})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]?$""")
    private val COMPACT_8 = Regex("""\b(\d{8})\b""")
    private val SPACES_AROUND_SEP = Regex("""\s*([/.\-])\s*""")
}
