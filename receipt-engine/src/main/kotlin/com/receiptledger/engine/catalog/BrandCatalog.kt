package com.receiptledger.engine.catalog

import kotlin.math.ln

data class BrandEntry(
    val canonical: String,
    val tokens: List<String>,
) {
    val glued: String = tokens.joinToString("")
}

class BrandCatalog(
    entries: List<BrandEntry>,
) {
    private val entries: List<BrandEntry> = entries
        .filter { it.canonical.isNotBlank() && it.tokens.isNotEmpty() }
        .distinctBy { it.canonical.uppercase() to it.tokens }

    private val idf: Map<String, Double> = documentFrequencies(this.entries)

    fun match(
        ocrTokens: Set<String>,
        logoTokens: Set<String> = ocrTokens,
        compactBlob: String = "",
    ): String? {
        val ocr = ocrTokens.map { it.uppercase() }.toSet()
        val logo = logoTokens.map { it.uppercase() }.toSet()
        val blob = compactBlob.uppercase()
        if (ocr.isEmpty() && blob.isEmpty()) return null
        var best: BrandEntry? = null
        var bestTokens = -1
        var bestGlued = -1
        var bestIdf = Double.NEGATIVE_INFINITY
        for (entry in entries) {
            val gluedHit = gluedMatchLength(entry, ocr, blob)
            if (!tokenMatch(entry, ocr, logo) && gluedHit == 0) continue
            val matchedIdf = entry.tokens.sumOf { idfOf(it) }
            val tokenCount = entry.tokens.size
            if (tokenCount > bestTokens ||
                (tokenCount == bestTokens && gluedHit > bestGlued) ||
                (tokenCount == bestTokens && gluedHit == bestGlued && matchedIdf > bestIdf) ||
                (tokenCount == bestTokens && gluedHit == bestGlued && matchedIdf == bestIdf &&
                    entry.canonical.length < (best?.canonical?.length ?: Int.MAX_VALUE))
            ) {
                best = entry
                bestTokens = tokenCount
                bestGlued = gluedHit
                bestIdf = matchedIdf
            }
        }
        return best?.canonical
    }

    fun matchText(text: String): String? {
        val tokens = BrandTokens.tokenize(text).toSet()
        return match(tokens, tokens, BrandTokens.compact(text))
    }

    private fun tokenMatch(entry: BrandEntry, ocr: Set<String>, logo: Set<String>): Boolean {
        if (!entry.tokens.all { it in ocr }) return false
        if (entry.tokens.size == 1) {
            val token = entry.tokens.single()
            if (token.length < 3) return false
            if (token !in logo) return false
        }
        return true
    }

    private fun gluedMatchLength(entry: BrandEntry, ocr: Set<String>, blob: String): Int {
        if (entry.tokens.size < 2 || entry.glued.length < MIN_GLUED_LENGTH) return 0
        if (entry.glued in ocr) return entry.glued.length
        if (blob.contains(entry.glued)) return entry.glued.length
        return 0
    }

    private fun idfOf(token: String): Double = idf[token] ?: 1.0

    companion object {
        const val MIN_GLUED_LENGTH = 6

        val nsi: BrandCatalog by lazy { loadNsi() }

        fun fromPhrases(phrases: List<Pair<String, String>>): BrandCatalog =
            BrandCatalog(
                phrases.mapNotNull { (canonical, phrase) ->
                    val tokens = BrandTokens.tokenizeName(phrase)
                    val canonicalTokens = BrandTokens.tokenizeName(canonical)
                    if (tokens.isEmpty()) return@mapNotNull null
                    if (isCollapsedAlias(tokens, canonicalTokens)) return@mapNotNull null
                    BrandEntry(canonical, tokens)
                },
            )

        fun parse(text: String): BrandCatalog = fromPhrases(
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { line ->
                    val tab = line.indexOf('\t')
                    if (tab < 0) line to line
                    else line.substring(0, tab) to line.substring(tab + 1)
                }
                .toList(),
        )

        private fun isCollapsedAlias(
            phraseTokens: List<String>,
            canonicalTokens: List<String>,
        ): Boolean {
            if (phraseTokens.size != 1) return false
            val alias = phraseTokens.single()
            if (canonicalTokens.size == 1 && alias == canonicalTokens.single()) return false
            if (alias == canonicalTokens.joinToString("")) return false
            return true
        }

        private fun loadNsi(): BrandCatalog {
            val stream = BrandCatalog::class.java.getResourceAsStream("/nsi-brands.txt")
                ?: error("Missing nsi-brands.txt")
            return stream.bufferedReader().use { parse(it.readText()) }
        }

        private fun documentFrequencies(entries: List<BrandEntry>): Map<String, Double> {
            val df = HashMap<String, Int>()
            for (entry in entries) {
                for (token in entry.tokens.toSet()) {
                    df[token] = (df[token] ?: 0) + 1
                }
            }
            val n = entries.size.coerceAtLeast(1)
            return df.mapValues { (_, count) -> ln((n + 1.0) / (count + 1.0)) + 1.0 }
        }
    }
}
