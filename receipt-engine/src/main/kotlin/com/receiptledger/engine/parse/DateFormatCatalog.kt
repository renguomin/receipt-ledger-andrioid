package com.receiptledger.engine.parse

import java.text.Normalizer
import java.util.Locale

/**
 * Receipt dates only come in a few shapes: three numbers (DMY / MDY / YMD),
 * a month name, compact digits, or Chinese numerals. This catalog lists them.
 */
object DateFormatCatalog {
    const val MIN_YEAR = 1990
    const val MAX_YEAR = 2099
    const val RECENT_YEARS = 5L

    val numericSeparators = listOf('/', '-', '.')

    private val ACCENTS = Regex("\\p{M}+")

    enum class NumericOrder { DAY_MONTH_YEAR, MONTH_DAY_YEAR, YEAR_MONTH_DAY }

    val numericOrders = listOf(
        NumericOrder.DAY_MONTH_YEAR,
        NumericOrder.MONTH_DAY_YEAR,
        NumericOrder.YEAR_MONTH_DAY,
    )

    val monthSpellings: List<Pair<String, Int>> = listOf(
        "Jan" to 1, "January" to 1, "Janv" to 1, "Janvier" to 1,
        "Feb" to 2, "February" to 2, "Fevr" to 2, "Fevrier" to 2, "Févr" to 2, "Février" to 2,
        "Mar" to 3, "March" to 3, "Mars" to 3,
        "Apr" to 4, "April" to 4, "Avr" to 4, "Avril" to 4,
        "May" to 5, "Mai" to 5,
        "Jun" to 6, "June" to 6, "Juin" to 6,
        "Jul" to 7, "July" to 7, "Juil" to 7, "Juillet" to 7,
        "Aug" to 8, "August" to 8, "Aou" to 8, "Aout" to 8, "Août" to 8,
        "Sep" to 9, "Sept" to 9, "September" to 9, "Septembre" to 9,
        "Oct" to 10, "October" to 10, "Octobre" to 10,
        "Nov" to 11, "November" to 11, "Novembre" to 11,
        "Dec" to 12, "December" to 12, "Déc" to 12, "Decembre" to 12, "Décembre" to 12,
    )

    val monthNames: Map<String, Int> =
        monthSpellings.associate { fold(it.first) to it.second }

    val weekdays: Set<String> = listOf(
        "Mon", "Monday", "Tue", "Tues", "Tuesday",
        "Wed", "Wednesday", "Thu", "Thur", "Thurs", "Thursday",
        "Fri", "Friday", "Sat", "Saturday", "Sun", "Sunday",
        "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche",
    ).map { fold(it) }.toSet()

    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(ACCENTS, "")
            .uppercase(Locale.ROOT)
            .trim()
            .trimEnd('.')

    fun monthNumber(token: String): Int? = monthNames[fold(token)]

    fun isWeekday(token: String): Boolean = fold(token) in weekdays
}
