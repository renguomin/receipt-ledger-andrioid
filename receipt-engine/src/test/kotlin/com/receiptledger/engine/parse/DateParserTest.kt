package com.receiptledger.engine.parse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DateParserTest {
    private val today = LocalDate.of(2026, 8, 25)

    private val tntOcr = """
        NOTAMEMBERYET?DOWNLOAD&JOINNOW!
        T&T Supermarket
        Langley Store
        #100-19705 Fraser Highway, Langley, BC, U3A7E9
        08/23/26 8:59:25 PM
        GROCERY
        TOTAL
        ${'$'}29.83
        Visa
        08/23/268:59:25 PM
        20:59:51
        08/23/26
        Thank You/Merci!
        CODE:082326 205901 1822 10031
    """.trimIndent()

    @Test
    fun twoDigitYearOnTntReceiptIsAugust2026() {
        assertEquals("2026-08-23", DateParser.findIsoDate(tntOcr, today))
        assertEquals("2026-08-23", DateParser.parse("08/23/26", today)?.toString())
    }

    @Test
    fun gluedHourDoesNotSwallowTheYear() {
        assertEquals("2026-08-23", DateParser.findIsoDate("08/23/268:59:25 PM", today))
    }

    @Test
    fun fourDigitSlashDatesStillWork() {
        assertEquals("2026-08-23", DateParser.parse("08/23/2026", today)?.toString())
        assertEquals("2025-07-02", DateParser.parse("02/07/2025", today)?.toString())
    }

    @Test
    fun ambiguousTwoDigitYearPrefersDayMonthWhenBothAreRecent() {
        assertEquals("2026-07-02", DateParser.parse("02/07/26", today)?.toString())
        assertEquals("2026-02-13", DateParser.parse("02/13/26", today)?.toString())
    }

    @Test
    fun twoDigitYearMonthDayBeatsAnOldDayMonthRead() {
        assertEquals("2025-11-02", DateParser.parse("25/11/02", today)?.toString())
        assertEquals("2025-11-02", DateParser.findIsoDate("25/11/02 19:19:11", today))
    }

    @Test
    fun isoDatesStillWin() {
        assertEquals("2026-03-12", DateParser.findIsoDate("CDN TIRE 2026-03-12 TOTAL 22.59", today))
    }

    @Test
    fun garbageIsNotADate() {
        assertNull(DateParser.findIsoDate("Item count:5 Terminal:050108013", today))
        assertNull(DateParser.parse("08/23/268", today))
        assertNull(DateParser.findIsoDate("Call 1-800-661-1600", today))
    }

    @Test
    fun everyNumericSeparatorAndOrderParses() {
        for (sep in DateFormatCatalog.numericSeparators) {
            assertEquals("2026-08-23", DateParser.parse("23${sep}08${sep}2026", today)?.toString(), "dMy 4 $sep")
            assertEquals("2026-08-23", DateParser.parse("08${sep}23${sep}2026", today)?.toString(), "Mdy 4 $sep")
            assertEquals("2026-08-23", DateParser.parse("2026${sep}08${sep}23", today)?.toString(), "yMd 4 $sep")
            assertEquals("2026-08-23", DateParser.parse("23${sep}08${sep}26", today)?.toString(), "dMy 2 $sep")
            assertEquals("2026-08-23", DateParser.parse("08${sep}23${sep}26", today)?.toString(), "Mdy 2 $sep")
        }
        assertEquals(3, DateFormatCatalog.numericOrders.size)
    }

    @Test
    fun namedMonthsInEnglishAndFrenchParse() {
        val august = listOf(
            "23 Aug 2026",
            "23 August 2026",
            "Aug 23, 2026",
            "August 23, 2026",
            "23-Aug-2026",
            "23-AUG-26",
            "Aug. 23, 2026",
            "23rd August 2026",
            "Sat, Aug 23, 2026",
            "23 août 2026",
            "23 aout 2026",
        )
        for (raw in august) {
            assertEquals("2026-08-23", DateParser.parse(raw, today)?.toString(), raw)
        }
        assertEquals("2026-03-08", DateParser.parse("8 Mar 2026", today)?.toString())
        assertEquals("2026-03-08", DateParser.parse("Mar 8, 2026", today)?.toString())
    }

    @Test
    fun chineseAndCompactDatesParse() {
        assertEquals("2026-08-23", DateParser.parse("2026年8月23日", today)?.toString())
        assertEquals("2026-08-23", DateParser.parse("2026年08月23号", today)?.toString())
        assertEquals("2026-08-23", DateParser.parse("20260823", today)?.toString())
        assertEquals("2026-08-23", DateParser.findIsoDate("DATE: 20260823 TOTAL 9.99", today))
    }

    @Test
    fun eachMonthNameMapsToAMonth() {
        val months = DateFormatCatalog.monthSpellings.map { it.second }.toSet()
        assertEquals((1..12).toSet(), months)
        assertEquals(8, DateFormatCatalog.monthNumber("août"))
        assertEquals(8, DateFormatCatalog.monthNumber("AUG"))
    }
}
