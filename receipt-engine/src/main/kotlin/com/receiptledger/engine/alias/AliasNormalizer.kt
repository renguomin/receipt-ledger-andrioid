package com.receiptledger.engine.alias

object AliasNormalizer {
    private val junk = Regex("[^A-Z0-9]+")

    fun key(raw: String): String =
        junk.replace(raw.uppercase(), " ").trim()
}
