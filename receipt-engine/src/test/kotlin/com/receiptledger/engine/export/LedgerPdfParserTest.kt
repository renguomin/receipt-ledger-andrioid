package com.receiptledger.engine.export

import com.receiptledger.engine.model.LineItem
import com.receiptledger.engine.model.Purpose
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class LedgerPdfParserTest {
    @Test
    fun roundTripsOwnExportFieldsAndJpegBytes() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val spec = ReceiptPdfLayout.build(
            title = "Receipts 2026",
            rows = listOf(
                ReceiptPdfRowInput(
                    date = "2026-03-12",
                    merchant = "Canadian Tire",
                    total = 22.59,
                    subtotal = 20.00,
                    tax = 2.59,
                    currency = "CAD",
                    purpose = "BUSINESS",
                    category = "Office expenses",
                    gstHstNumber = "123456789RT0001",
                    paymentMethod = "visa *7403",
                    receiptNumber = "004821",
                    lineItems = listOf(LineItem("Hammer", 1.0, 19.99)),
                    imagePath = "/tmp/ct.jpg",
                ),
                ReceiptPdfRowInput(
                    date = "2025-11-02",
                    merchant = "Shoppers (Main)",
                    total = 8.40,
                    subtotal = 8.40,
                    tax = null,
                    currency = "CAD",
                    purpose = "PERSONAL",
                    category = "Personal — groceries",
                    gstHstNumber = "123",
                    paymentMethod = "cash",
                    imagePath = "/tmp/sd.jpg",
                ),
            ),
        )
        val jpeg2 = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x12, 0xD9.toByte())
        val pdf = ReceiptPdfDocuments.write(
            spec,
            listOf(
                PackedPdfImage(8, 8, jpeg, PdfImageEncoding.JPEG),
                PackedPdfImage(8, 8, jpeg2, PdfImageEncoding.JPEG),
            ),
        )

        val parsed = LedgerPdfParser.parse(pdf)
        assertTrue(parsed is LedgerPdfParseResult.Success)
        val receipts = (parsed as LedgerPdfParseResult.Success).receipts
        assertEquals(2, receipts.size)

        val first = receipts[0]
        assertEquals("2026-03-12", first.date)
        assertEquals("Canadian Tire", first.merchant)
        assertEquals("22.59 CAD", first.total)
        assertEquals("20.00", first.subtotal)
        assertEquals("2.59", first.tax)
        assertEquals("business", first.purpose)
        assertEquals("Office expenses", first.category)
        assertEquals("123456789RT0001", first.gstHstNumber)
        assertEquals("visa *7403", first.payment)
        assertEquals("004821", first.receiptNumber)
        assertEquals("Appendix A-1", first.photoRef)
        assertEquals(listOf(ImportedPdfLine("Hammer", "1", "19.99")), first.lineItems)
        assertArrayEquals(jpeg, first.jpegBytes)
        assertTrue((parsed as LedgerPdfParseResult.Success).fromLedgerExport)

        val mapped = ReceiptPdfImport.toParsed(first)
        assertEquals("Canadian Tire", mapped.merchant.value)
        assertEquals("2026-03-12", mapped.date.value)
        assertEquals(22.59, mapped.total.value)
        assertEquals("CAD", mapped.currency.value)
        assertEquals(20.00, mapped.subtotal.value)
        assertEquals(2.59, mapped.taxTotal)
        assertEquals("visa *7403", mapped.paymentMethod.value)
        assertEquals("123456789RT0001", mapped.gstHstNumber.value)
        assertEquals("004821", mapped.receiptNumber.value)
        assertEquals("Hammer", mapped.lineItems.single().name)
        assertEquals(Purpose.BUSINESS, ReceiptPdfImport.purposeOf(first.purpose))

        val second = receipts[1]
        assertEquals("Shoppers (Main)", second.merchant)
        assertEquals("personal", second.purpose)
        assertEquals("123", second.gstHstNumber)
        assertTrue(second.lineItems.isEmpty())
        assertArrayEquals(jpeg2, second.jpegBytes)
    }

    @Test
    fun sharedAndMissingPhotosStayWithTheirOwnReceipt() {
        val homeDepot = jpegOf(9_000, 1)
        val tt = jpegOf(9_000, 2)
        val costco = jpegOf(9_000, 3)
        val merchants = listOf(
            "The Home Depot",
            "T&T Supermarket",
            "T&T Supermarket",
            "T&T Supermarket",
            "Shell",
            "Costco",
        )
        val spec = ReceiptPdfLayout.build(
            title = "Receipts",
            rows = merchants.mapIndexed { index, name ->
                ReceiptPdfRowInput(
                    date = "2026-09-0${index + 1}",
                    merchant = name,
                    total = (index + 1).toDouble(),
                    subtotal = (index + 1).toDouble(),
                    tax = null,
                    currency = "CAD",
                    purpose = "PERSONAL",
                    category = null,
                    gstHstNumber = null,
                    paymentMethod = null,
                    imagePath = "/tmp/$index.jpg",
                )
            },
        )
        // The three T&T rows share one photo file, and Shell's photo is missing.
        val pdf = ReceiptPdfDocuments.write(
            spec,
            listOf(
                PackedPdfImage(8, 8, homeDepot, PdfImageEncoding.JPEG),
                PackedPdfImage(8, 8, tt, PdfImageEncoding.JPEG),
                PackedPdfImage(8, 8, tt, PdfImageEncoding.JPEG),
                PackedPdfImage(8, 8, tt, PdfImageEncoding.JPEG),
                null,
                PackedPdfImage(8, 8, costco, PdfImageEncoding.JPEG),
            ),
        )
        val receipts = (LedgerPdfParser.parse(pdf) as LedgerPdfParseResult.Success).receipts
        assertEquals(merchants, receipts.map { it.merchant })
        assertArrayEquals(homeDepot, receipts[0].jpegBytes)
        assertArrayEquals(tt, receipts[1].jpegBytes)
        assertArrayEquals(tt, receipts[2].jpegBytes)
        assertArrayEquals(tt, receipts[3].jpegBytes)
        assertNull(receipts[4].jpegBytes)
        assertArrayEquals(costco, receipts[5].jpegBytes)
    }

    @Test
    fun nestedThumbnailInsideAPhotoDoesNotStealTheNextReceiptsImage() {
        val jpeg1 = jpegWithThumbnail(200, 1)
        val jpeg2 = jpegWithThumbnail(220, 2)
        val spec = ReceiptPdfLayout.build(
            title = "Receipts",
            rows = listOf(
                ReceiptPdfRowInput(
                    date = "2026-03-12",
                    merchant = "Canadian Tire",
                    total = 1.0,
                    subtotal = 1.0,
                    tax = null,
                    currency = "CAD",
                    purpose = "PERSONAL",
                    category = null,
                    gstHstNumber = null,
                    paymentMethod = null,
                    imagePath = "/tmp/a.jpg",
                ),
                ReceiptPdfRowInput(
                    date = "2026-04-01",
                    merchant = "Shoppers",
                    total = 2.0,
                    subtotal = 2.0,
                    tax = null,
                    currency = "CAD",
                    purpose = "PERSONAL",
                    category = null,
                    gstHstNumber = null,
                    paymentMethod = null,
                    imagePath = "/tmp/b.jpg",
                ),
            ),
        )
        val pdf = ReceiptPdfDocuments.write(
            spec,
            listOf(
                PackedPdfImage(8, 8, jpeg1, PdfImageEncoding.JPEG),
                PackedPdfImage(8, 8, jpeg2, PdfImageEncoding.JPEG),
            ),
        )
        val receipts = (LedgerPdfParser.parse(pdf) as LedgerPdfParseResult.Success).receipts
        assertEquals("Canadian Tire", receipts[0].merchant)
        assertEquals("Shoppers", receipts[1].merchant)
        assertArrayEquals(jpeg1, receipts[0].jpegBytes)
        assertArrayEquals(jpeg2, receipts[1].jpegBytes)
    }

    @Test
    fun missingValuesStayEmptyAndPhotoCanBeAbsent() {
        val spec = ReceiptPdfLayout.build(
            title = "Receipts",
            rows = listOf(
                ReceiptPdfRowInput(
                    date = null,
                    merchant = "  ",
                    total = null,
                    subtotal = null,
                    tax = null,
                    currency = null,
                    purpose = "PERSONAL",
                    category = null,
                    gstHstNumber = "",
                    paymentMethod = null,
                    imagePath = "/tmp/missing.jpg",
                ),
            ),
        )
        val pdf = ReceiptPdfDocuments.write(spec, listOf(null))
        val receipts = (LedgerPdfParser.parse(pdf) as LedgerPdfParseResult.Success).receipts
        val row = receipts.single()
        assertNull(row.date)
        assertNull(row.merchant)
        assertNull(row.total)
        assertNull(row.category)
        assertNull(row.gstHstNumber)
        assertNull(row.payment)
        assertNull(row.receiptNumber)
        assertEquals("personal", row.purpose)
        assertNull(row.jpegBytes)
    }

    @Test
    fun wrappedMerchantAndLongCategoryRoundTrip() {
        val merchant = "Very Long Merchant Name That Should Wrap Across The Value Column"
        val category = "Meals and entertainment (50%)"
        val spec = ReceiptPdfLayout.build(
            title = "Receipts",
            rows = listOf(
                ReceiptPdfRowInput(
                    date = "2026-03-12",
                    merchant = merchant,
                    total = 10.0,
                    subtotal = 9.0,
                    tax = 1.0,
                    currency = "CAD",
                    purpose = "SPLIT",
                    category = category,
                    gstHstNumber = null,
                    paymentMethod = null,
                    lineItems = listOf(
                        LineItem("A generously named line item that also wraps in the table", 2.0, 7.50),
                    ),
                    imagePath = "/tmp/wrap.jpg",
                ),
            ),
        )
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val pdf = ReceiptPdfDocuments.write(
            spec,
            listOf(PackedPdfImage(8, 8, jpeg, PdfImageEncoding.JPEG)),
        )
        val row = (LedgerPdfParser.parse(pdf) as LedgerPdfParseResult.Success).receipts.single()
        assertEquals(merchant, row.merchant)
        assertEquals(category, row.category)
        assertEquals("split", row.purpose)
        assertEquals("A generously named line item that also wraps in the table", row.lineItems.single().name)
        assertEquals("2", row.lineItems.single().quantity)
        assertEquals("7.50", row.lineItems.single().amount)
    }

    @Test
    fun importsAGenericTextPdfAsAReceipt() {
        val pdf = SimplePdf().apply {
            text(40f, 56f, 16f, "Canadian Tire", bold = true)
            text(40f, 80f, 12f, "TOTAL 22.59")
        }.toByteArray()
        val parsed = LedgerPdfParser.parse(pdf)
        assertTrue(parsed is LedgerPdfParseResult.Success)
        val row = (parsed as LedgerPdfParseResult.Success).receipts.single()
        assertFalse(parsed.fromLedgerExport)
        assertTrue(row.rawText.contains("Canadian Tire"))
        assertTrue(row.rawText.contains("TOTAL 22.59"))
        assertNull(row.jpegBytes)
        val draft = ReceiptPdfImport.toParsed(row)
        assertTrue(draft.merchant.value?.contains("Tire", ignoreCase = true) == true)
        assertEquals(22.59, draft.total.value)
    }

    @Test
    fun importsEmbeddedJpegFromANonLedgerPdf() {
        val jpeg = ByteArray(3_000) { 0 }
        jpeg[0] = 0xFF.toByte()
        jpeg[1] = 0xD8.toByte()
        jpeg[jpeg.lastIndex] = 0xD9.toByte()
        jpeg[jpeg.lastIndex - 1] = 0xFF.toByte()
        val pdf = SimplePdf().apply {
            text(40f, 56f, 16f, "Scanned receipt")
            image(40f, 80f, 200f, 200f, PackedPdfImage(8, 8, jpeg, PdfImageEncoding.JPEG))
        }.toByteArray()
        val parsed = LedgerPdfParser.parse(pdf)
        val row = (parsed as LedgerPdfParseResult.Success).receipts.single()
        assertArrayEquals(jpeg, row.jpegBytes)
    }

    @Test
    fun emptyExportReportsNoReceipts() {
        val spec = ReceiptPdfLayout.build("Receipts", emptyList())
        val parsed = LedgerPdfParser.parse(ReceiptPdfDocuments.write(spec, emptyList()))
        assertEquals(LedgerPdfParseResult.Failed(LedgerPdfParser.NO_RECEIPTS), parsed)
    }

    @Test
    fun appendixPhotosPairByCaptionAndIgnoreThePhotoField() {
        val first = jpegOf(1_000, 1)
        val second = jpegOf(1_000, 2)
        val pages = listOf(
            // A table page: "Appendix A-1" is a field value, not a caption.
            PdfPageContent(listOf("Receipt 1", "Merchant", "Costco", "Photo", "Appendix A-1"), listOf(first)),
            PdfPageContent(listOf("A-1  Costco  ·  2026-09-02"), listOf(first)),
            PdfPageContent(listOf("A-2  T&T Supermarket  ·  2026-08-23"), emptyList()),
            PdfPageContent(listOf("A-3  T&T Supermarket  ·  2026-08-23"), listOf(second)),
        )
        val byRef = LedgerPdfParser.appendixPhotos(pages)
        assertEquals(setOf(1, 3), byRef.keys)
        assertArrayEquals(first, byRef[1])
        assertArrayEquals(second, byRef[3])
    }

    @Test
    fun anOutsidePdfGivesEachPhotoItsOwnReceiptEvenWhenSizesDiffer() {
        val short = jpegOf(42_000, 1)
        val tall = jpegOf(204_000, 2)
        val middling = jpegOf(60_000, 3)
        val contents = PdfContents(
            textTokens = listOf("Expense Report"),
            text = "Expense Report",
            images = listOf(short, tall, middling),
        )
        val receipts = LedgerPdfParser.parseGenericReceipts(contents)
        assertEquals(3, receipts.size)
        assertArrayEquals(short, receipts[0].jpegBytes)
        assertArrayEquals(tall, receipts[1].jpegBytes)
        assertArrayEquals(middling, receipts[2].jpegBytes)
        // Text from a PDF the app did not write belongs to no single receipt, so none carries it.
        assertTrue(receipts.all { it.rawText.isEmpty() })
    }

    @Test
    fun theTwoDatePrintedRowsDoNotBleedIntoEachOtherOnImport() {
        val spec = ReceiptPdfLayout.build(
            title = "Receipts",
            rows = listOf(
                ReceiptPdfRowInput(
                    date = "2024-01-04",
                    merchant = "Canadian Tire",
                    total = 22.59,
                    subtotal = 20.00,
                    tax = 2.59,
                    currency = "CAD",
                    purpose = "BUSINESS",
                    category = "Office expenses",
                    gstHstNumber = null,
                    paymentMethod = null,
                    imagePath = "/tmp/ct.jpg",
                    dateAdded = "Sep 4, 2026 at 3:59 PM",
                ),
            ),
        )
        val pdf = ReceiptPdfDocuments.write(spec, listOf(null))
        val row = (LedgerPdfParser.parse(pdf) as LedgerPdfParseResult.Success).receipts.single()
        assertEquals("2024-01-04", row.date)
        assertEquals("Canadian Tire", row.merchant)
        assertEquals("22.59 CAD", row.total)
        assertEquals("2024-01-04", ReceiptPdfImport.toParsed(row).date.value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Date", "Receipt date", "Purchased date"])
    fun everyNameThePurchaseDateHasBeenExportedUnderStillImports(label: String) {
        val pdf = SimplePdf().apply {
            text(40f, 40f, 12f, "Receipt 1", bold = true)
            text(40f, 60f, 12f, label)
            text(140f, 60f, 12f, "2026-03-12")
            text(40f, 80f, 12f, "Merchant")
            text(140f, 80f, 12f, "Canadian Tire")
            text(40f, 100f, 12f, "Total")
            text(140f, 100f, 12f, "22.59 CAD")
        }.toByteArray()
        val parsed = LedgerPdfParser.parse(pdf) as LedgerPdfParseResult.Success
        assertTrue(parsed.fromLedgerExport)
        val row = parsed.receipts.single()
        assertEquals("2026-03-12", row.date)
        assertEquals("Canadian Tire", row.merchant)
        assertEquals("22.59 CAD", row.total)
    }

    @Test
    fun anAddedDateRowFromAnEarlierNameDoesNotLandInThePurchaseDate() {
        val pdf = SimplePdf().apply {
            text(40f, 40f, 12f, "Receipt 1", bold = true)
            text(40f, 60f, 12f, "Receipt date")
            text(140f, 60f, 12f, "2024-01-04")
            text(40f, 80f, 12f, "Date added")
            text(140f, 80f, 12f, "Sep 4, 2026 at 3:59 PM")
            text(40f, 100f, 12f, "Merchant")
            text(140f, 100f, 12f, "Canadian Tire")
        }.toByteArray()
        val row = (LedgerPdfParser.parse(pdf) as LedgerPdfParseResult.Success).receipts.single()
        assertEquals("2024-01-04", row.date)
        assertEquals("Canadian Tire", row.merchant)
    }

    @Test
    fun unescapeRestoresParentheses() {
        assertEquals("Shop (Main)", LedgerPdfParser.unescape("Shop \\(Main\\)"))
        assertEquals("a\\b", LedgerPdfParser.unescape("a\\\\b"))
    }

    private fun jpegOf(size: Int, fill: Byte): ByteArray {
        val bytes = ByteArray(size) { fill }
        bytes[0] = 0xFF.toByte()
        bytes[1] = 0xD8.toByte()
        bytes[size - 2] = 0xFF.toByte()
        bytes[size - 1] = 0xD9.toByte()
        return bytes
    }

    private fun jpegWithThumbnail(size: Int, fill: Byte): ByteArray {
        val bytes = ByteArray(size) { fill }
        bytes[0] = 0xFF.toByte()
        bytes[1] = 0xD8.toByte()
        bytes[2] = 0xFF.toByte()
        bytes[3] = 0xE1.toByte()
        bytes[4] = 0x00.toByte()
        bytes[5] = 0x08.toByte()
        bytes[6] = 0xFF.toByte()
        bytes[7] = 0xD8.toByte()
        bytes[8] = 0x00.toByte()
        bytes[9] = 0x00.toByte()
        bytes[10] = 0xFF.toByte()
        bytes[11] = 0xD9.toByte()
        bytes[12] = 0xFF.toByte()
        bytes[13] = 0xDA.toByte()
        bytes[14] = 0x00.toByte()
        bytes[15] = 0x02.toByte()
        bytes[size - 2] = 0xFF.toByte()
        bytes[size - 1] = 0xD9.toByte()
        return bytes
    }
}
