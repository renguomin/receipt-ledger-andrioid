package com.receiptledger.app.data

import com.receiptledger.engine.export.ReceiptPdfLayout
import com.receiptledger.engine.model.Country
import com.receiptledger.engine.model.Purpose
import com.receiptledger.engine.tax.TaxCategories

data class CategoryChoice(
    val id: String,
    val label: String,
    val mealsFiftyPercent: Boolean = false,
    val personal: Boolean = false,
    val custom: Boolean = false,
    val pending: Boolean = false,
)

object CategoryCatalog {
    const val ID_PREFIX = "custom_"
    const val PENDING_ID = "pending_new"

    fun visible(
        country: Country,
        custom: List<CustomCategoryEntity>,
        pendingNewLabel: String? = null,
    ): List<CategoryChoice> {
        val predefined = TaxCategories.forCountry(country).map { category ->
            CategoryChoice(
                id = category.id,
                label = category.label,
                mealsFiftyPercent = category.mealsFiftyPercent,
                personal = category.personal,
            )
        }
        val extras = custom.map { row ->
            CategoryChoice(id = row.id, label = row.label, custom = true)
        }
        val catalog = predefined + extras
        val pending = pendingChoice(pendingNewLabel, catalog) ?: return catalog
        return listOf(pending) + catalog
    }

    fun pendingMenuLabel(name: String): String = "${name.trim()} (will be created)"

    fun pendingChoice(rawLabel: String?, catalog: List<CategoryChoice>): CategoryChoice? {
        val name = normalizedPendingLabel(rawLabel) ?: return null
        if (catalog.any { ReceiptPdfLayout.labelsMatch(it.label, name) }) return null
        return CategoryChoice(
            id = PENDING_ID,
            label = pendingMenuLabel(name),
            custom = true,
            pending = true,
        )
    }

    fun existingIdForPending(
        rawLabel: String?,
        custom: List<CustomCategoryEntity>,
    ): String? {
        val name = normalizedPendingLabel(rawLabel) ?: return null
        return custom.firstOrNull { ReceiptPdfLayout.labelsMatch(it.label, name) }?.id
    }

    fun label(id: String?, custom: List<CustomCategoryEntity>): String? {
        if (id.isNullOrBlank()) return null
        return TaxCategories.find(id)?.label
            ?: custom.firstOrNull { it.id == id }?.label
            ?: id
    }

    fun isCustom(id: String?): Boolean = id.orEmpty().startsWith(ID_PREFIX)

    fun isPending(id: String?): Boolean = id == PENDING_ID

    fun defaultId(
        country: Country,
        purpose: Purpose,
        custom: List<CustomCategoryEntity>,
    ): String? {
        if (country == Country.NONE) return custom.firstOrNull()?.id
        return if (purpose == Purpose.PERSONAL) {
            TaxCategories.defaultPersonal(country)?.id
        } else {
            TaxCategories.forCountry(country).firstOrNull { !it.personal }?.id
        }
    }

    fun normalizedPendingLabel(rawLabel: String?): String? =
        rawLabel?.trim()?.takeIf { it.isNotEmpty() }
}
