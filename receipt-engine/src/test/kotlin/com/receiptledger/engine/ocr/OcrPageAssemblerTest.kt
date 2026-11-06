package com.receiptledger.engine.ocr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OcrPageAssemblerTest {
    @Test
    fun mapsAxisAlignedDetectionsToPageLines() {
        val page = OcrPageAssembler.assemble(
            width = 200f,
            height = 400f,
            regions = listOf(
                DetectedRegion("BUY", left = 10f, top = 40f, right = 80f, bottom = 70f, confidence = 0.9f),
                DetectedRegion("BEST", left = 12f, top = 8f, right = 90f, bottom = 36f, confidence = 0.95f),
                DetectedRegion("   ", left = 0f, top = 0f, right = 10f, bottom = 10f),
            ),
        )
        assertEquals(200f, page.width)
        assertEquals(400f, page.height)
        assertEquals(listOf("BEST", "BUY"), page.lines.map { it.text })
        assertEquals(8f, page.lines.first().box.top)
        assertEquals(0.95f, page.lines.first().confidence)
    }

    @Test
    fun polygonPointsBecomeAnAxisAlignedBox() {
        val box = OcrPageAssembler.boxFromPoints(
            listOf(20f to 10f, 80f to 12f, 78f to 40f, 18f to 38f),
        )
        assertTrue(box != null)
        assertEquals(18f, box!!.left)
        assertEquals(10f, box.top)
        assertEquals(80f, box.right)
        assertEquals(40f, box.bottom)
    }
}
