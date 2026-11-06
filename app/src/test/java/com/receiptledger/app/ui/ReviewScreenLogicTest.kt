package com.receiptledger.app.ui

import com.receiptledger.app.data.CategoryCatalog
import com.receiptledger.app.data.CategoryChoice
import com.receiptledger.engine.model.Purpose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReviewScreenLogicTest {
    @Test
    fun verifyCopyAsksTheUserToCheckFieldsWithoutGuessWording() {
        assertEquals("Check the fields", ReviewScreenLogic.TITLE)
        assertTrue(ReviewScreenLogic.VERIFY_COPY.startsWith("Please verify the fields."))
        assertFalse(ReviewScreenLogic.VERIFY_COPY.contains("guess", ignoreCase = true))
        assertFalse(ReviewScreenLogic.VERIFY_COPY.contains("geometry", ignoreCase = true))
        assertEquals("Tap the photo to zoom", ReviewScreenLogic.PHOTO_HINT)
    }

    @Test
    fun theAiIsSentCategoryLabelsAndNotJustIds() {
        val options = listOf(
            CategoryChoice(id = "ca_t2125_9281", label = "Motor vehicle expenses"),
            CategoryChoice(id = "custom_1", label = "Studio rent", custom = true),
            CategoryChoice(id = "pending_new", label = "Tools (will be created)", pending = true),
        )
        assertEquals(
            listOf(
                "ca_t2125_9281" to "Motor vehicle expenses",
                "custom_1" to "Studio rent",
            ),
            ReviewScreenLogic.catalogForAi(options),
        )
        assertEquals(
            listOf("ca_t2125_9281", "custom_1"),
            ReviewScreenLogic.catalogIdsForAi(options),
        )
    }

    @Test
    fun ocrDumpActionIsOnlyForDeveloperMode() {
        assertTrue(ReviewScreenLogic.showOcrDumpAction(developerMode = true))
        assertFalse(ReviewScreenLogic.showOcrDumpAction(developerMode = false))
        assertEquals("Save OCR + scan", ReviewScreenLogic.SAVE_OCR_DUMP_LABEL)
    }

    @Test
    fun skipButtonOnlyAppearsDuringAnImportBatch() {
        assertTrue(ReviewScreenLogic.showSkipButton(importTotal = 1))
        assertTrue(ReviewScreenLogic.showSkipButton(importTotal = 41))
        assertFalse(ReviewScreenLogic.showSkipButton(importTotal = 0))
        assertEquals("Skip this receipt", ReviewScreenLogic.SKIP_LABEL)
    }

    @Test
    fun itemHeaderOnlyShowsWhenThereAreItemsToLabel() {
        assertFalse(ReviewScreenLogic.showItemsHeader(itemCount = 0))
        assertTrue(ReviewScreenLogic.showItemsHeader(itemCount = 1))
        assertEquals("Item", ReviewScreenLogic.ITEM_HEADER)
        assertEquals("Qty", ReviewScreenLogic.QTY_HEADER)
        assertEquals("Amount", ReviewScreenLogic.AMOUNT_HEADER)
        assertEquals("None found", ReviewScreenLogic.ITEMS_EMPTY)
        assertEquals("Add item", ReviewScreenLogic.ADD_ITEM_LABEL)
    }

    @Test
    fun itemRowsAreNumberedFromOneForPlaceholderAndRemoveAction() {
        assertEquals("Item 1", ReviewScreenLogic.itemNamePlaceholder(0))
        assertEquals("Item 4", ReviewScreenLogic.itemNamePlaceholder(3))
        assertEquals("Remove item 1", ReviewScreenLogic.removeItemDescription(0))
        assertEquals("Remove item 4", ReviewScreenLogic.removeItemDescription(3))
    }

    @Test
    fun categoryRowFallsBackToAPromptWhenNothingIsChosen() {
        val options = listOf("groceries" to "Groceries", "fuel" to "Fuel")
        assertEquals("Groceries", ReviewScreenLogic.categoryLabel("groceries", options))
        assertEquals("Choose a category", ReviewScreenLogic.categoryLabel(null, options))
        assertEquals("Choose a category", ReviewScreenLogic.categoryLabel("gone", options))
        assertEquals("Choose a category", ReviewScreenLogic.categoryLabel("groceries", emptyList()))
    }

    @Test
    fun purposeChipsUsePlainLanguage() {
        assertEquals("Personal", ReviewScreenLogic.purposeLabel(Purpose.PERSONAL))
        assertEquals("Business", ReviewScreenLogic.purposeLabel(Purpose.BUSINESS))
        assertEquals("Split", ReviewScreenLogic.purposeLabel(Purpose.SPLIT))
    }

    @Test
    fun importProgressShowsDetectedCountAndPlaceInTheBatch() {
        assertFalse(ReviewScreenLogic.showImportProgress(0))
        assertTrue(ReviewScreenLogic.showImportProgress(1))
        assertEquals("1 receipt detected", ReviewScreenLogic.importDetectedLabel(1))
        assertEquals("5 receipts detected", ReviewScreenLogic.importDetectedLabel(5))
        assertEquals("Receipt 2 of 5", ReviewScreenLogic.importProgressLabel(2, 5))
        assertEquals(0f, ReviewScreenLogic.importProgressFraction(1, 0))
        assertEquals(1f / 5f, ReviewScreenLogic.importProgressFraction(1, 5))
        assertEquals(1f, ReviewScreenLogic.importProgressFraction(5, 5))
    }

    @Test
    fun unknownPdfCategoryBecomesAPendingChoiceUntilSave() {
        val office = CategoryChoice("ca_t2125_8810", "Office expenses")
        val pending = ReviewScreenLogic.categorySelection(
            draftCategoryId = null,
            draftNewLabel = "Client lunch",
            suggestedId = "ca_t2125_8810",
            defaultId = "ca_personal_other",
            options = listOf(office),
        )
        assertEquals(CategoryCatalog.PENDING_ID, pending.categoryId)
        assertEquals("Client lunch", pending.pendingNewLabel)
        assertEquals(
            "Client lunch",
            ReviewScreenLogic.labelToCreateOnSave(pending.categoryId, pending.pendingNewLabel),
        )
        assertEquals(
            null,
            ReviewScreenLogic.labelToCreateOnSave(office.id, pending.pendingNewLabel),
        )
    }

    @Test
    fun matchingPdfCategoryIsUsedAndDoesNotCreate() {
        val office = CategoryChoice("ca_t2125_8810", "Office expenses")
        val selected = ReviewScreenLogic.categorySelection(
            draftCategoryId = "ca_t2125_8810",
            draftNewLabel = null,
            suggestedId = null,
            defaultId = "ca_personal_other",
            options = listOf(office),
        )
        assertEquals("ca_t2125_8810", selected.categoryId)
        assertEquals(null, selected.pendingNewLabel)
        assertEquals(
            null,
            ReviewScreenLogic.labelToCreateOnSave(selected.categoryId, selected.pendingNewLabel),
        )
    }

    @Test
    fun pendingLabelThatAlreadyExistsSelectsTheExistingCategory() {
        val lunch = CategoryChoice("custom_1", "Client lunch", custom = true)
        val selected = ReviewScreenLogic.categorySelection(
            draftCategoryId = null,
            draftNewLabel = "client lunch",
            suggestedId = null,
            defaultId = "ca_personal_other",
            options = listOf(lunch),
        )
        assertEquals("custom_1", selected.categoryId)
        assertEquals(null, selected.pendingNewLabel)
    }

    @Test
    fun blankPdfCategoryFallsThroughToSuggested() {
        val office = CategoryChoice("ca_t2125_8810", "Office expenses")
        val selected = ReviewScreenLogic.categorySelection(
            draftCategoryId = null,
            draftNewLabel = "  ",
            suggestedId = "ca_t2125_8810",
            defaultId = "ca_personal_other",
            options = listOf(office),
        )
        assertEquals("ca_t2125_8810", selected.categoryId)
        assertEquals(null, selected.pendingNewLabel)
    }

    @Test
    fun purposeChangeKeepsPendingAndCustomCategories() {
        assertTrue(ReviewScreenLogic.keepCategoryOnPurposeChange(CategoryCatalog.PENDING_ID, false))
        assertTrue(ReviewScreenLogic.keepCategoryOnPurposeChange("custom_1", false))
        assertFalse(ReviewScreenLogic.keepCategoryOnPurposeChange("ca_t2125_8810", false))
        assertTrue(ReviewScreenLogic.keepCategoryOnPurposeChange("ca_t2125_8810", true))
    }

    @Test
    fun aiCatalogOmitsThePendingOption() {
        val options = listOf(
            CategoryChoice("ca_t2125_8810", "Office expenses"),
            CategoryChoice(
                CategoryCatalog.PENDING_ID,
                "Client lunch (will be created)",
                custom = true,
                pending = true,
            ),
        )
        assertEquals(listOf("ca_t2125_8810"), ReviewScreenLogic.catalogIdsForAi(options))
    }

    @Test
    fun eachImportedPhotoGetsItsOwnReviewSession() {
        assertEquals(
            "image:file:///a.jpg",
            ReviewScreenLogic.sessionKey(existingId = null, imageUri = "file:///a.jpg"),
        )
        assertEquals(
            "image:file:///b.jpg",
            ReviewScreenLogic.sessionKey(existingId = null, imageUri = "file:///b.jpg"),
        )
        assertEquals("id:abc", ReviewScreenLogic.sessionKey(existingId = "abc", imageUri = "file:///a.jpg"))
        assertTrue(
            ReviewScreenLogic.sessionKey(null, "file:///a.jpg") !=
                ReviewScreenLogic.sessionKey(null, "file:///b.jpg"),
        )
    }
}
