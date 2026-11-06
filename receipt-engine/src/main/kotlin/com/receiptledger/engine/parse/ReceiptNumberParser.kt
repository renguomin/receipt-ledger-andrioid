package com.receiptledger.engine.parse

import com.receiptledger.engine.model.BoundingBox
import com.receiptledger.engine.model.OcrLine
import java.text.Normalizer

/**
 * Canonical document id for a receipt, matching expense-parser taxonomy
 * (Textract INVOICE_RECEIPT_ID / Document AI invoice_id): one field for
 * whatever unique id the slip printed, found as a nearby key/value pair.
 * Surface labels are classified by role after normalization, not listed.
 */
object ReceiptNumberParser {
    const val GEMINI_RULE =
        """receiptNumber: the unique id printed for this document itself. Not a tax ID, phone, card, store number, SKU, auth code, or line-item code. null if none is printed."""

    fun find(lines: List<OcrLine>): String? {
        val strong = mutableListOf<String>()
        val weak = mutableListOf<String>()
        for (index in lines.indices) {
            val hit = extract(lines[index].text, lines.getOrNull(index + 1)?.text) ?: continue
            if (hit.strong) strong += hit.value else weak += hit.value
        }
        return strong.firstOrNull() ?: weak.firstOrNull()
    }

    fun find(text: String): String? {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return extract(text, null)?.value
        return find(
            lines.mapIndexed { index, line ->
                OcrLine(
                    text = line,
                    box = BoundingBox(0f, index * 20f, 300f, index * 20f + 16f),
                )
            },
        )
    }

    fun normalizeForDedup(value: String): String =
        value.filter { it.isLetterOrDigit() }.uppercase()

    fun sameDocument(a: String?, b: String?): Boolean {
        val left = a?.let { normalizeForDedup(it) }.orEmpty()
        val right = b?.let { normalizeForDedup(it) }.orEmpty()
        if (left.isEmpty() || right.isEmpty()) return false
        return left == right
    }

    private data class Hit(val value: String, val strong: Boolean)

    private data class Token(val raw: String, val norm: String)

    private fun extract(line: String, nextLine: String?): Hit? {
        val tokens = tokenize(line)
        if (tokens.isEmpty()) return null
        for (len in minOf(4, tokens.size) downTo 1) {
            for (start in 0..tokens.size - len) {
                val window = tokens.subList(start, start + len)
                val norms = window.map { it.norm }
                if (norms.any { it in REJECT }) continue
                val hasRole = norms.any { it in DOCUMENT_ROLES }
                val hasCue = norms.any { it in IDENTIFIER_CUES }
                val strong = hasRole && hasCue
                val weakPrefix = start == 0 && isBareIdPrefix(norms)
                val weakRole = hasRole && !hasCue
                if (!strong && !weakPrefix && !weakRole) continue
                val remainder = tokens.drop(start + len)
                val rawValue = remainder.joinToString("") { it.raw }
                    .ifBlank { firstToken(nextLine).orEmpty() }
                val value = cleanValue(rawValue) ?: continue
                if (!isValidValue(value)) continue
                return Hit(value, strong)
            }
        }
        return null
    }

    private fun tokenize(line: String): List<Token> {
        val padded = line
            .replace("#", " # ")
            .replace("№", " # ")
            .replace("n°", " # ", ignoreCase = true)
            .replace("nº", " # ", ignoreCase = true)
            .replace(":", " ")
            .replace("=", " ")
        return padded.split(Regex("\\s+"))
            .map { it.trim(',', ';', '.', '|', '(', ')') }
            .filter { it.isNotEmpty() }
            .map { Token(raw = it, norm = normalizeToken(it)) }
            .filter { it.norm.isNotEmpty() }
    }

    private fun normalizeToken(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed == "#" || trimmed == "№") return "number"
        var s = stripAccents(trimmed.lowercase())
        s = s.replace(Regex("[^a-z0-9]+"), "")
        if (s == "no" || s == "n" || s == "num" || s == "nr") return "number"
        return STEMS[s] ?: s
    }

    private fun stripAccents(raw: String): String {
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
    }

    private fun isBareIdPrefix(norms: List<String>): Boolean =
        norms.any { it in IDENTIFIER_CUES } &&
            norms.none { it in DOCUMENT_ROLES } &&
            norms.none { it in REJECT } &&
            norms.all { it in IDENTIFIER_CUES || it == "de" || it == "du" || it == "of" }

    private fun firstToken(line: String?): String? {
        if (line.isNullOrBlank()) return null
        return tokenize(line).firstOrNull()?.raw
    }

    private fun cleanValue(raw: String): String? {
        val trimmed = raw.trim().trim(',', ';', '.', ':', '#')
        if (trimmed.isEmpty()) return null
        return trimmed
    }

    private fun isValidValue(value: String): Boolean {
        if (value.length !in 2..32) return false
        if (!value.any { it.isDigit() }) return false
        if (!VALUE_CHARS.matches(value)) return false
        if (GST_NUMBER.containsMatchIn(value.replace(" ", ""))) return false
        if (PHONE.matches(value.filter { it.isDigit() })) return false
        if (TIME.matches(value)) return false
        if (DateParser.parse(value) != null) return false
        if (MoneyParser.findAll(value).any { it.raw.filter { ch -> !ch.isWhitespace() } == value }) {
            return false
        }
        return true
    }

    private val DOCUMENT_ROLES = setOf(
        "sequence",
        "reference",
        "receipt",
        "invoice",
        "order",
        "transaction",
        "ticket",
        "confirmation",
        "bill",
        "document",
        "check",
        "cheque",
        "facture",
    )

    private val IDENTIFIER_CUES = setOf("number", "id")

    private val REJECT = setOf(
        "gst",
        "hst",
        "pst",
        "qst",
        "tps",
        "tvq",
        "tax",
        "phone",
        "tel",
        "fax",
        "card",
        "visa",
        "account",
        "store",
        "sku",
        "auth",
        "approval",
        "member",
        "customer",
        "loyalty",
        "register",
        "terminal",
        "batch",
        "clerk",
        "item",
        "po",
        "purchase",
    )

    private val STEMS = mapOf(
        "seq" to "sequence",
        "ref" to "reference",
        "inv" to "invoice",
        "txn" to "transaction",
        "trans" to "transaction",
        "rcpt" to "receipt",
        "recu" to "receipt",
        "conf" to "confirmation",
        "cmd" to "order",
        "commande" to "order",
        "numero" to "number",
    )

    private val VALUE_CHARS = Regex("""^[A-Za-z0-9][A-Za-z0-9\-_/]*$""")
    private val GST_NUMBER = Regex("""(?<!\d)(\d{9}RT\d{4})(?!\d)""", RegexOption.IGNORE_CASE)
    private val PHONE = Regex("""^\d{10,11}$""")
    private val TIME = Regex("""^\d{1,2}:\d{2}(?::\d{2})?$""")
}
