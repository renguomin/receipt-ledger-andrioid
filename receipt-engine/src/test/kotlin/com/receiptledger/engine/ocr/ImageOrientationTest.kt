package com.receiptledger.engine.ocr

import com.receiptledger.engine.model.BoundingBox
import com.receiptledger.engine.model.OcrLine
import com.receiptledger.engine.model.OcrPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageOrientationTest {
    @Test
    fun exifThreeMeansRotate180() {
        assertEquals(
            ImageOrientation.ExifAction.ROTATE_180,
            ImageOrientation.exifAction(ImageOrientation.EXIF_ROTATE_180),
        )
        assertEquals(
            ImageOrientation.ExifAction.IDENTITY,
            ImageOrientation.exifAction(ImageOrientation.EXIF_NORMAL),
        )
        assertEquals(
            ImageOrientation.ExifAction.ROTATE_90_CW,
            ImageOrientation.exifAction(ImageOrientation.EXIF_ROTATE_90),
        )
        assertEquals(
            ImageOrientation.ExifAction.ROTATE_270_CW,
            ImageOrientation.exifAction(ImageOrientation.EXIF_ROTATE_270),
        )
    }

    @Test
    fun uprightReceiptKeepsTotalNearTheBottom() {
        val page = receipt(
            line("STORE NAME", top = 10f),
            line("COFFEE 4.50", top = 180f),
            line("TOTAL 4.50", top = 320f),
            line("THANK YOU", top = 360f),
        )
        assertFalse(ImageOrientation.shouldRotate180(page))
        assertFalse(ImageOrientation.correctUpsideDown(page).rotated180)
    }

    @Test
    fun totalAtTheTopMeansThePhotoIsUpsideDown() {
        val page = receipt(
            line("THANK YOU", top = 20f),
            line("TOTAL 22.59", top = 60f),
            line("COFFEE 4.50", top = 200f),
            line("STORE NAME", top = 340f),
        )
        assertTrue(ImageOrientation.shouldRotate180(page))
        val corrected = ImageOrientation.correctUpsideDown(page)
        assertTrue(corrected.rotated180)
        assertEquals("STORE NAME", corrected.page.lines.first().text)
        assertEquals("THANK YOU", corrected.page.lines.last().text)
        assertFalse(ImageOrientation.shouldRotate180(corrected.page))
    }

    @Test
    fun totalSaleAtTheTopIsNotAFooter() {
        val page = receipt(
            line("SHELL CANADA", top = 10f),
            line("TOTAL SALE", top = 80f),
            line("VISA 60.00", top = 200f),
            line("THANK YOU", top = 360f),
        )
        assertFalse(ImageOrientation.shouldRotate180(page))
    }

    @Test
    fun subtotalAtTheTopDoesNotFlipAnUprightReceipt() {
        val page = receipt(
            line("OFFICE MART", top = 8f),
            line("SUBTOTAL 19.99", top = 40f),
            line("GST 1.00", top = 280f),
            line("TOTAL 22.59", top = 330f),
        )
        assertFalse(ImageOrientation.shouldRotate180(page))
    }

    @Test
    fun changeDueAtTheTopAlsoCountsAsUpsideDown() {
        val page = receipt(
            line("CHANGE DUE 0.00", top = 30f),
            line("MILK 3.49", top = 180f),
            line("STORE NAME", top = 350f),
        )
        assertTrue(ImageOrientation.shouldRotate180(page))
    }

    @Test
    fun noMarkersLeavesThePhotoAlone() {
        val page = receipt(
            line("STORE NAME", top = 10f),
            line("MILK 3.49", top = 180f),
        )
        assertFalse(ImageOrientation.shouldRotate180(page))
    }

    @Test
    fun rotate180MirrorsBoxesAroundThePageCenter() {
        val box = BoundingBox(left = 10f, top = 20f, right = 40f, bottom = 50f)
        val rotated = ImageOrientation.rotateBox180(box, pageWidth = 100f, pageHeight = 200f)
        assertEquals(60f, rotated.left)
        assertEquals(150f, rotated.top)
        assertEquals(90f, rotated.right)
        assertEquals(180f, rotated.bottom)
    }

    private fun receipt(vararg lines: OcrLine) = OcrPage(
        lines = lines.toList(),
        width = 200f,
        height = 400f,
    )

    private fun line(text: String, top: Float) = OcrLine(
        text = text,
        box = BoundingBox(left = 0f, top = top, right = 180f, bottom = top + 18f),
    )
}
