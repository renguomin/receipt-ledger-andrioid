package com.receiptledger.engine.alias

class MerchantAliasBook(
    learned: Map<String, String> = emptyMap(),
) {
    private val learnedKeys: Map<String, String> = learned.mapKeys { AliasNormalizer.key(it.key) }
    private val all: Map<String, String> = SeedAliases.entries + learnedKeys

    fun resolve(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val key = AliasNormalizer.key(raw)
        all[key]?.let { return it }
        all.entries.firstOrNull { key.contains(it.key) || it.key.contains(key) }?.let { return it.value }
        return raw.trim()
    }

    fun canonicalOrNull(raw: String?): String? = match(all, raw)

    fun learnedCanonicalOrNull(raw: String?): String? = match(learnedKeys, raw)

    /**
     * Tidies a name using only the built-in list. Names the AI read straight off the storefront
     * are already clean, so the corrections learned from this phone's own history have nothing to
     * add there and could only rename a store that was read correctly.
     */
    fun seedCanonicalOrNull(raw: String?): String? = match(SeedAliases.entries, raw)

    private fun match(aliases: Map<String, String>, raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val key = AliasNormalizer.key(raw)
        aliases[key]?.let { return it }
        return aliases.entries
            .filter { it.key.length >= MIN_PARTIAL && fits(key, it.key) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    /**
     * The longest candidate wins so a specific name is not lost to a short code that happens to
     * sit inside it, and so the answer does not ride on the order the aliases were added.
     */
    private fun fits(key: String, candidate: String): Boolean =
        if (candidate.length >= RUN_ON_MIN) key.contains(candidate) else wholeWord(key, candidate)

    /**
     * Short codes have to stand as their own word, so one learned name cannot rename every other
     * store that merely spells it somewhere inside a longer line.
     */
    private fun wholeWord(key: String, candidate: String): Boolean {
        var from = 0
        while (from <= key.length - candidate.length) {
            val at = key.indexOf(candidate, from)
            if (at < 0) return false
            val startsWord = at == 0 || key[at - 1] == ' '
            val end = at + candidate.length
            if (startsWord && (end == key.length || key[end] == ' ')) return true
            from = at + 1
        }
        return false
    }

    private companion object {
        /** Two letters name too many things to rewrite a store on. */
        const val MIN_PARTIAL = 3

        /** Long names stay matchable inside run-on text, which OCR produces often. */
        const val RUN_ON_MIN = 6
    }
}
