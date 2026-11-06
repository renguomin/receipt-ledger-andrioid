package com.receiptledger.engine.export

import com.receiptledger.engine.model.ReceiptRecordLogic

data class ImportedPdfLine(
    val name: String,
    val quantity: String,
    val amount: String,
)

data class ImportedPdfReceipt(
    val heading: String,
    val date: String? = null,
    val merchant: String? = null,
    val total: String? = null,
    val subtotal: String? = null,
    val tax: String? = null,
    val purpose: String? = null,
    val category: String? = null,
    val gstHstNumber: String? = null,
    val payment: String? = null,
    val receiptNumber: String? = null,
    val photoRef: String? = null,
    val lineItems: List<ImportedPdfLine> = emptyList(),
    val jpegBytes: ByteArray? = null,
    val rawText: String = "",
)

sealed class LedgerPdfParseResult {
    data class Success(
        val receipts: List<ImportedPdfReceipt>,
        val fromLedgerExport: Boolean = false,
    ) : LedgerPdfParseResult()
    data class Failed(val message: String) : LedgerPdfParseResult()
}

object LedgerPdfParser {
    const val NOT_LEDGER = "This PDF isn't a Receipt Ledger export."
    const val NO_RECEIPTS = "No receipts found in this PDF."
    const val UNREADABLE = "Could not read this PDF."

    private const val RECEIPT_DATE = "receiptDate"

    /**
     * Read only so the printed row cannot leak into the receipt date. Importing a PDF adds the
     * receipt to this ledger now, so it is stamped fresh on save rather than carried over.
     */
    private const val DATE_ADDED = "dateAdded"

    private val receiptHeading = Regex("""^Receipt \d+$""")
    private val caption = Regex("""^A-\d+\s+""")
    private val appendixCaption = Regex("""^A-(\d+)\b""")
    private val pageNumber = Regex("""^\d+$""")
    private val amountToken = Regex("""^\d+\.\d{2}$""")
    private val qtyToken = Regex("""^\d+(?:\.\d+)?$""")
    /**
     * A slot reads one printed row. It carries every spelling that label has ever been exported
     * under, so a PDF written by an older build still imports: back then the receipt's own date
     * was printed as a bare "Date" and there was no date added row at all.
     */
    private data class FieldSlot(val key: String, val spellings: List<String>)

    private val fieldSlots = listOf(
        FieldSlot(
            RECEIPT_DATE,
            listOf(ReceiptRecordLogic.PURCHASED_DATE_LABEL, "Receipt date", "Date"),
        ),
        FieldSlot(DATE_ADDED, listOf(ReceiptRecordLogic.ADDED_DATE_LABEL, "Date added")),
        FieldSlot("Merchant", listOf("Merchant")),
        FieldSlot("Receipt number", listOf("Receipt number")),
        FieldSlot("Total", listOf("Total")),
        FieldSlot("Subtotal", listOf("Subtotal")),
        FieldSlot("Tax", listOf("Tax")),
        FieldSlot("Purpose", listOf("Purpose")),
        FieldSlot("Category", listOf("Category")),
        FieldSlot("GST/HST number", listOf("GST/HST number")),
        FieldSlot("Payment", listOf("Payment")),
        FieldSlot("Photo", listOf("Photo")),
    )
    private val skipExact = setOf(
        ReceiptPdfLayout.DISCLAIMER,
        ReceiptPdfLayout.APPENDIX_TITLE,
        ReceiptPdfLayout.LINE_ITEMS_HEADING,
        ReceiptPdfWriter.PHOTO_MISSING,
        "Item",
        "Qty",
        "Amount",
    )

    fun parse(pdf: ByteArray): LedgerPdfParseResult {
        if (pdf.size < 8) return LedgerPdfParseResult.Failed(UNREADABLE)
        val header = pdf.copyOfRange(0, minOf(8, pdf.size)).toString(Charsets.ISO_8859_1)
        if (!header.startsWith("%PDF")) return LedgerPdfParseResult.Failed(UNREADABLE)
        val contents = PdfContentExtractor.extract(pdf)
        return if (looksLikeLedger(contents.textTokens)) {
            val receipts = parseLedgerReceipts(contents)
            if (receipts.isEmpty()) LedgerPdfParseResult.Failed(NO_RECEIPTS)
            else LedgerPdfParseResult.Success(receipts, fromLedgerExport = true)
        } else {
            val receipts = parseGenericReceipts(contents)
            if (receipts.isEmpty()) LedgerPdfParseResult.Failed(NO_RECEIPTS)
            else LedgerPdfParseResult.Success(receipts)
        }
    }

    internal fun looksLikeLedger(tokens: List<String>): Boolean {
        if (tokens.contains(ReceiptPdfLayout.DISCLAIMER)) return true
        if (tokens.any { isAppendixMarker(it) }) return true
        val datedRow = fieldSlots.first { it.key == RECEIPT_DATE }.spellings
        return tokens.any { receiptHeading.matches(it) } &&
            tokens.any { it in datedRow } &&
            tokens.contains("Merchant")
    }

    internal fun unescape(text: String): String = PdfContentExtractor.unescape(text)

    internal fun parseGenericReceipts(contents: PdfContents): List<ImportedPdfReceipt> {
        val photos = PdfContentExtractor.keepReceiptPhotos(contents.images)
        val text = contents.text.trim()
        if (photos.isNotEmpty()) {
            return photos.mapIndexed { index, image ->
                ImportedPdfReceipt(
                    heading = "Receipt ${index + 1}",
                    jpegBytes = image,
                    rawText = if (photos.size == 1) text else "",
                )
            }
        }
        if (text.isNotEmpty()) {
            return listOf(
                ImportedPdfReceipt(
                    heading = "Receipt 1",
                    rawText = text,
                ),
            )
        }
        return emptyList()
    }

    /**
     * Each appendix page repeats the receipt's own "A-N" caption next to its photo, so the
     * caption is what pairs a photo with its receipt. Position in the flat image list cannot:
     * receipts may share one photo file or have none at all.
     */
    internal fun appendixPhotos(pages: List<PdfPageContent>): Map<Int, ByteArray> {
        val byRef = HashMap<Int, ByteArray>()
        for (page in pages) {
            val image = page.images.firstOrNull() ?: continue
            val ref = page.textTokens.firstNotNullOfOrNull { token ->
                appendixCaption.find(token.trim())?.groupValues?.get(1)?.toIntOrNull()
            } ?: continue
            byRef.getOrPut(ref) { image }
        }
        return byRef
    }

    private fun parseLedgerReceipts(contents: PdfContents): List<ImportedPdfReceipt> {
        val tokens = contents.textTokens
        val starts = tokens.indices.filter { receiptHeading.matches(tokens[it]) }
        val byRef = appendixPhotos(contents.pages)
        val byOrder = if (byRef.isEmpty()) {
            PdfContentExtractor.photosForReceipts(contents.images, starts.size)
        } else {
            emptyList()
        }
        return starts.mapIndexed { index, start ->
            val end = starts.getOrElse(index + 1) { tokens.size }
            val slice = tokens.subList(start, end)
            val fields = parseFields(slice)
            ImportedPdfReceipt(
                heading = slice.first(),
                date = fields[RECEIPT_DATE],
                merchant = fields["Merchant"],
                total = fields["Total"],
                subtotal = fields["Subtotal"],
                tax = fields["Tax"],
                purpose = fields["Purpose"],
                category = fields["Category"],
                gstHstNumber = fields["GST/HST number"],
                payment = fields["Payment"],
                receiptNumber = fields["Receipt number"],
                photoRef = fields["Photo"],
                lineItems = parseLineItems(slice),
                jpegBytes = byRef[index + 1] ?: byOrder.getOrNull(index),
            )
        }
    }

    private fun parseFields(tokens: List<String>): Map<String, String?> {
        val values = HashMap<String, String?>()
        var i = 1
        for ((index, slot) in fieldSlots.withIndex()) {
            val found = (i until tokens.size).firstOrNull { tokens[it] in slot.spellings } ?: continue
            i = found + 1
            val stop = fieldSlots.drop(index + 1).flatMap { it.spellings }.toSet() + setOf(
                ReceiptPdfLayout.LINE_ITEMS_HEADING,
                "Item",
            )
            val parts = ArrayList<String>()
            while (i < tokens.size) {
                val token = tokens[i]
                val next = tokens.getOrNull(i + 1)
                if (token in stop ||
                    receiptHeading.matches(token) ||
                    isAppendixMarker(token)
                ) {
                    break
                }
                if (token in skipExact || caption.matches(token)) {
                    i += 1
                    continue
                }
                if (isPageNumberToken(token) && parts.isNotEmpty() &&
                    (next == null || next in stop || receiptHeading.matches(next) || isAppendixMarker(next))
                ) {
                    i += 1
                    continue
                }
                parts += token
                i += 1
            }
            values[slot.key] = missingToNull(parts.joinToString(" ").trim())
        }
        return values
    }

    private fun parseLineItems(tokens: List<String>): List<ImportedPdfLine> {
        val heading = tokens.indexOf(ReceiptPdfLayout.LINE_ITEMS_HEADING)
        if (heading < 0) return emptyList()
        var i = heading + 1
        while (i < tokens.size && tokens[i] in setOf("Item", "Qty", "Amount")) i += 1
        val remaining = ArrayList<String>()
        while (i < tokens.size) {
            val token = tokens[i]
            if (receiptHeading.matches(token) || isAppendixMarker(token)) break
            if (token in skipExact || caption.matches(token)) {
                i += 1
                continue
            }
            remaining += token
            i += 1
        }
        val rows = ArrayList<ImportedPdfLine>()
        var cursor = 0
        while (cursor < remaining.size) {
            while (cursor < remaining.size &&
                isPageNumberToken(remaining[cursor]) &&
                remaining.getOrNull(cursor + 1)?.let { isAmountToken(it) } != true
            ) {
                cursor += 1
            }
            if (cursor >= remaining.size) break
            val amountAt = (cursor + 2 until remaining.size).firstOrNull { idx ->
                isAmountToken(remaining[idx]) && isQtyToken(remaining[idx - 1])
            } ?: break
            val name = remaining.subList(cursor, amountAt - 1).joinToString(" ").trim()
            if (name.isNotEmpty()) {
                rows += ImportedPdfLine(
                    name = name,
                    quantity = remaining[amountAt - 1],
                    amount = remaining[amountAt],
                )
            }
            cursor = amountAt + 1
        }
        return rows
    }

    private fun isAppendixMarker(token: String): Boolean =
        ReceiptPdfLayout.labelsMatch(token, ReceiptPdfLayout.APPENDIX_TITLE)

    private fun isPageNumberToken(token: String): Boolean = pageNumber.matches(token)

    private fun isAmountToken(token: String): Boolean =
        ReceiptPdfLayout.isMissing(token) || amountToken.matches(token)

    private fun isQtyToken(token: String): Boolean =
        ReceiptPdfLayout.isMissing(token) || qtyToken.matches(token)

    private fun missingToNull(value: String): String? =
        value.takeIf { it.isNotEmpty() && !ReceiptPdfLayout.isMissing(it) }
}
