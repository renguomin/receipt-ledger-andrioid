package com.receiptledger.engine.export

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PdfContentExtractorTest {
    @Test
    fun keepReceiptPhotosDropsTinyLogosBesideARealPhoto() {
        val logo = jpegOf(800)
        val photo = jpegOf(12_000)
        val kept = PdfContentExtractor.keepReceiptPhotos(listOf(logo, photo))
        assertEquals(1, kept.size)
        assertEquals(12_000, kept.single().size)
    }

    @Test
    fun keepReceiptPhotosKeepsSeveralSimilarPhotos() {
        val first = jpegOf(10_000)
        val second = jpegOf(9_500)
        assertEquals(2, PdfContentExtractor.keepReceiptPhotos(listOf(first, second)).size)
    }

    @Test
    fun keepReceiptPhotosKeepsASmallReceiptBesideAMuchLargerOne() {
        val short = jpegOf(42_000)
        val tall = jpegOf(204_000)
        val kept = PdfContentExtractor.keepReceiptPhotos(listOf(short, tall, jpegOf(45_000)))
        assertEquals(3, kept.size)
    }

    @Test
    fun anEmbeddedFontDoesNotBecomePageText() {
        val contents = PdfContentExtractor.extract(pdfWith("(Costco) Tj", " /Length1 900"))
        assertEquals(emptyList<String>(), contents.textTokens)
    }

    @Test
    fun aLongRunOfOpenParensIsReadWithoutBlowingTheStack() {
        val wide = "(".repeat(60_000) + "a".repeat(60_000)
        assertEquals(emptyList<String>(), PdfContentExtractor.extract(pdfWith(wide)).textTokens)
    }

    @Test
    fun escapedAndNestedParensStayInsideTheirString() {
        val contents = PdfContentExtractor.extract(pdfWith("""(Bob\'s (Deli)) Tj (Total) Tj"""))
        assertEquals(listOf("Bob's (Deli)", "Total"), contents.textTokens)
    }

    private fun pdfWith(body: String, dictExtras: String = ""): ByteArray {
        val payload = body.toByteArray(Charsets.ISO_8859_1)
        val head = "%PDF-1.4\n1 0 obj\n<< /Length ${payload.size}$dictExtras >>\nstream\n"
        return head.toByteArray(Charsets.ISO_8859_1) +
            payload +
            "\nendstream\nendobj\n".toByteArray(Charsets.ISO_8859_1)
    }

    @Test
    fun extraTinyImagesDoNotShiftReceiptPhotos() {
        val logo = jpegOf(800)
        val first = jpegOf(10_000)
        val second = jpegOf(9_500)
        val photos = PdfContentExtractor.photosForReceipts(listOf(logo, first, second), 2)
        assertEquals(2, photos.size)
        assertArrayEquals(first, photos[0])
        assertArrayEquals(second, photos[1])
        assertEquals(2, PdfContentExtractor.photosForReceipts(listOf(first, second), 2).size)
    }

    @Test
    fun magicScanKeepsTheFullJpegWhenAThumbnailIsNestedInside() {
        val jpeg = jpegWithThumbnail(128)
        val blob = byteArrayOf(1, 2, 3) + jpeg + byteArrayOf(4, 5)
        val found = PdfContentExtractor.extractJpegsByMagic(blob)
        assertEquals(1, found.size)
        assertTrue(found.single().contentEquals(jpeg))
    }

    @Test
    fun magicScanFindsAJpegBuriedInArbitraryBytes() {
        val jpeg = jpegOf(64)
        val blob = byteArrayOf(1, 2, 3) + jpeg + byteArrayOf(4, 5)
        val found = PdfContentExtractor.extractJpegsByMagic(blob)
        assertEquals(1, found.size)
        assertTrue(found.single().contentEquals(jpeg))
    }

    private fun jpegOf(size: Int): ByteArray {
        val bytes = ByteArray(size) { 0 }
        bytes[0] = 0xFF.toByte()
        bytes[1] = 0xD8.toByte()
        bytes[size - 2] = 0xFF.toByte()
        bytes[size - 1] = 0xD9.toByte()
        return bytes
    }

    private fun jpegWithThumbnail(size: Int): ByteArray {
        val bytes = jpegOf(size)
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
        return bytes
    }
}
