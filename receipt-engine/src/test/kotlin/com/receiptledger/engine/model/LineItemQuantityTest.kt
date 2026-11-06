package com.receiptledger.engine.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LineItemQuantityTest {
    @Test
    fun aLineWithNoCountPrintedIsOneUnit() {
        val resolved = LineItemQuantity.resolve(LineItem("Muffin", quantity = null, amount = 3.00))
        assertEquals(1.0, resolved.quantity)
        assertEquals("Muffin", resolved.name)
    }

    @Test
    fun blankQuantityNeverSurvives() {
        val resolved = LineItemQuantity.resolveAll(
            listOf(
                LineItem("Muffin", quantity = null, amount = null),
                LineItem("Coffee", quantity = null, amount = 7.98),
                LineItem("Bread", quantity = 3.0, amount = 6.00),
            ),
        )
        assertEquals(listOf(1.0, 1.0, 3.0), resolved.map { it.quantity })
    }

    @Test
    fun zeroAndNegativeQuantitiesFallBackToOne() {
        assertEquals(1.0, LineItemQuantity.resolve(LineItem("Muffin", quantity = 0.0)).quantity)
        assertEquals(1.0, LineItemQuantity.resolve(LineItem("Muffin", quantity = -2.0)).quantity)
    }

    @Test
    fun trailingStoreMarkersAreReadAndRemovedFromTheName() {
        val cases = mapOf(
            "Coffee x2" to 2.0,
            "Coffee X 3" to 3.0,
            "Coffee ×4" to 4.0,
            "Batteries AA 2x" to 2.0,
            "Coffee QTY 5" to 5.0,
            "Coffee qty: 6" to 6.0,
            "Coffee 2 EA" to 2.0,
            "Coffee 3 each" to 3.0,
        )
        cases.forEach { (name, expected) ->
            val resolved = LineItemQuantity.resolve(LineItem(name, quantity = null, amount = null))
            assertEquals(expected, resolved.quantity, name)
        }
        assertEquals("Coffee", LineItemQuantity.resolve(LineItem("Coffee x2")).name)
        assertEquals("Batteries AA", LineItemQuantity.resolve(LineItem("Batteries AA 2x")).name)
    }

    @Test
    fun leadingStoreMarkersAreReadAndRemovedFromTheName() {
        val resolved = LineItemQuantity.resolve(LineItem("2x Coffee", quantity = null))
        assertEquals(2.0, resolved.quantity)
        assertEquals("Coffee", resolved.name)
        assertEquals(3.0, LineItemQuantity.resolve(LineItem("3 X Coffee")).quantity)
        assertEquals(4.0, LineItemQuantity.resolve(LineItem("x4 Coffee")).quantity)
        assertEquals(5.0, LineItemQuantity.resolve(LineItem("QTY 5 Coffee")).quantity)
    }

    @Test
    fun aTrailingUnitPriceMarkerGivesBothTheCountAndACleanName() {
        val resolved = LineItemQuantity.resolve(
            LineItem("Coffee 2 @ 3.99", quantity = null, amount = 7.98),
        )
        assertEquals(2.0, resolved.quantity)
        assertEquals("Coffee", resolved.name)
    }

    @Test
    fun bundlePricingCountsTheItemsNotTheDollars() {
        val resolved = LineItemQuantity.resolve(
            LineItem("Buns 2 FOR 5.00", quantity = null, amount = 5.00),
        )
        assertEquals(2.0, resolved.quantity)
        assertEquals("Buns", resolved.name)
    }

    @Test
    fun markersInsideAProductNameAreLeftAlone() {
        // These read as counts only if the marker is allowed to float mid-name.
        val untouched = listOf(
            "WD-40 x2 Pack" to "WD-40 x2 Pack",
            "2X4 SPRUCE STUD" to "2X4 SPRUCE STUD",
            "XBOX2" to "XBOX2",
            "MILK 2L" to "MILK 2L",
            "PEPSI 12X355ML" to "PEPSI 12X355ML",
            "Paper Towels 12 Pack" to "Paper Towels 12 Pack",
        )
        untouched.forEach { (name, expected) ->
            val resolved = LineItemQuantity.resolve(LineItem(name, quantity = null, amount = 5.00))
            assertEquals(expected, resolved.name, name)
            assertEquals(1.0, resolved.quantity, name)
        }
    }

    @Test
    fun strippingAMarkerNeverLeavesTheLineWithoutAName() {
        // A blank name would make compactLineItems drop the line, so a marker is only read when
        // there is product text around it to keep.
        listOf("2 x", "x 2", "x2", "2x", "QTY 2", "2 EA").forEach { name ->
            val resolved = LineItemQuantity.resolve(LineItem(name, quantity = null))
            assertEquals(name, resolved.name, name)
            assertEquals(1.0, resolved.quantity, name)
        }
    }

    @Test
    fun anExplicitQuantityBeatsAMarkerButTheNameIsStillCleaned() {
        val resolved = LineItemQuantity.resolve(
            LineItem("Coffee x2", quantity = 3.0, amount = 11.97),
        )
        assertEquals(3.0, resolved.quantity)
        assertEquals("Coffee", resolved.name)
    }

    @Test
    fun aPrintedUnitPriceDividesIntoTheLineTotal() {
        assertEquals(2.0, LineItemQuantity.fromUnitPrice(unitPrice = 3.99, amount = 7.98))
        assertEquals(3.0, LineItemQuantity.fromUnitPrice(unitPrice = 1.25, amount = 3.75))
        // Rounding on the receipt can leave a cent behind.
        assertEquals(3.0, LineItemQuantity.fromUnitPrice(unitPrice = 3.33, amount = 10.00))
    }

    @Test
    fun unitPriceInferenceStaysOutOfTheWayWhenItCannotBeSure() {
        // Does not divide cleanly, so the amount was probably the unit price itself.
        assertNull(LineItemQuantity.fromUnitPrice(unitPrice = 3.99, amount = 9.00))
        // A count of one is the default anyway.
        assertNull(LineItemQuantity.fromUnitPrice(unitPrice = 3.99, amount = 3.99))
        assertNull(LineItemQuantity.fromUnitPrice(unitPrice = null, amount = 7.98))
        assertNull(LineItemQuantity.fromUnitPrice(unitPrice = 0.0, amount = 7.98))
        assertNull(LineItemQuantity.fromUnitPrice(unitPrice = 3.99, amount = null))
        assertNull(LineItemQuantity.fromUnitPrice(unitPrice = 3.99, amount = 0.0))
    }

    @Test
    fun unitPriceIsReadFromTheLineTextInEitherForm() {
        assertEquals(3.99, LineItemQuantity.unitPrice("COFFEE @ 3.99"))
        assertEquals(3.99, LineItemQuantity.unitPrice("COFFEE @ $3.99"))
        assertEquals(3.99, LineItemQuantity.unitPrice("COFFEE 3.99 EA"))
        assertEquals(3.99, LineItemQuantity.unitPrice("COFFEE 3.99/EA"))
        assertNull(LineItemQuantity.unitPrice("COFFEE"))
    }

    @Test
    fun aUnitPriceWithNoMarkerStillCountsTheLine() {
        val resolved = LineItemQuantity.resolve(
            LineItem("COFFEE @ 3.99", quantity = null, amount = 11.97),
        )
        assertEquals(3.0, resolved.quantity)
    }

    @Test
    fun impossibleCountsAreIgnored() {
        assertEquals(1.0, LineItemQuantity.resolve(LineItem("Coffee x0")).quantity)
        assertNull(LineItemQuantity.readMarker("Coffee x0"))
    }
}
