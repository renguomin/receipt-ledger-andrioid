package com.receiptledger.engine.catalog

import java.text.Normalizer

object BrandTokens {
    private val splitter = Regex("[^A-Z0-9]+")
    private val marks = Regex("\\p{Mn}+")
    private val stop = setOf(
        "A", "AN", "THE", "AND", "OF", "AT", "FOR", "BY", "WITH",
    )

    fun fold(text: String): String {
        val mapped = text.replace('ß', 'B').replace('ẞ', 'B')
        return marks.replace(Normalizer.normalize(mapped, Normalizer.Form.NFD), "")
            .uppercase()
            .replace("'", "")
            .replace("&", " ")
    }

    fun compact(text: String): String = fold(text).filter { it.isLetterOrDigit() }

    fun tokenize(text: String): List<String> =
        splitter.split(fold(text))
            .filter { useful(it) }
            .distinct()

    fun tokenizeName(text: String): List<String> =
        tokenize(text).filter { it !in stop }

    private fun useful(token: String): Boolean {
        if (token.isEmpty()) return false
        if (token.length == 1 && !token[0].isLetter()) return false
        if (token.all { it.isDigit() } && token.length >= 3) return false
        return true
    }
}
