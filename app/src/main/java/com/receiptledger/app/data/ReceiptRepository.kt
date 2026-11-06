package com.receiptledger.app.data

import com.receiptledger.engine.alias.AliasNormalizer
import com.receiptledger.engine.alias.MerchantAliasBook
import com.receiptledger.engine.export.ReceiptCsv
import com.receiptledger.engine.export.ReceiptCsvRow
import com.receiptledger.engine.model.Field
import com.receiptledger.engine.model.FieldSource
import com.receiptledger.engine.model.LineItem
import com.receiptledger.engine.model.ParsedReceipt
import com.receiptledger.engine.model.Purpose
import com.receiptledger.engine.model.LineItemQuantity
import com.receiptledger.engine.model.ReceiptRecordLogic
import com.receiptledger.engine.model.TaxAmount
import com.receiptledger.engine.model.TaxKind
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

class ReceiptRepository(
    private val receiptDao: ReceiptDao,
    private val aliasDao: AliasDao,
    private val customCategoryDao: CustomCategoryDao,
    private val imageStore: ImageStore,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val receipts: Flow<List<ReceiptEntity>> = receiptDao.observeAll()
    val customCategories: Flow<List<CustomCategoryEntity>> = customCategoryDao.observeAll()

    suspend fun aliasBook(): MerchantAliasBook =
        MerchantAliasBook(aliasDao.all().associate { it.rawKey to it.canonical })

    suspend fun get(id: String): ReceiptEntity? = receiptDao.get(id)

    suspend fun getAll(): List<ReceiptEntity> = receiptDao.getAll()

    suspend fun addCustomCategory(label: String): String? {
        val name = label.trim()
        if (name.isEmpty()) return null
        val id = CategoryCatalog.ID_PREFIX + UUID.randomUUID().toString()
        customCategoryDao.upsert(
            CustomCategoryEntity(
                id = id,
                label = name,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    suspend fun addCustomCategoryIfNew(label: String, existing: List<CustomCategoryEntity>): String? {
        val name = label.trim()
        if (name.isEmpty()) return null
        existing.firstOrNull { it.label.equals(name, ignoreCase = true) }?.id?.let { return it }
        return addCustomCategory(name)
    }

    suspend fun deleteCustomCategory(id: String) {
        customCategoryDao.delete(id)
    }

    suspend fun save(
        existingId: String?,
        imagePath: String,
        draft: ParsedReceipt,
        purpose: Purpose,
        categoryId: String?,
        previousMerchant: String?,
    ): String {
        val id = existingId ?: UUID.randomUUID().toString()
        val existing = existingId?.let { receiptDao.get(it) }
        val dateIso = draft.date.value
        val year = dateIso?.let { runCatching { LocalDate.parse(it).year }.getOrNull() }
            ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        receiptDao.upsert(
            ReceiptEntity(
                id = id,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                imagePath = existing?.imagePath ?: imagePath,
                merchant = draft.merchant.value,
                merchantConfidence = draft.merchant.confidence,
                merchantSource = draft.merchant.source.name,
                dateIso = dateIso,
                dateConfidence = draft.date.confidence,
                currency = draft.currency.value,
                subtotal = draft.subtotal.value,
                taxTotal = draft.taxTotal,
                taxJson = json.encodeToString(draft.taxes.map { it.toStored() }),
                total = draft.total.value,
                totalConfidence = draft.total.confidence,
                paymentMethod = draft.paymentMethod.value,
                gstHstNumber = draft.gstHstNumber.value,
                receiptNumber = draft.receiptNumber.value,
                lineItemsJson = json.encodeToString(
                    LineItemQuantity.resolveAll(
                        ReceiptRecordLogic.compactLineItems(draft.lineItems),
                    ),
                ),
                purpose = purpose.name,
                categoryId = categoryId,
                rawText = draft.rawText,
                year = year,
            ),
        )
        val newMerchant = draft.merchant.value?.trim()
        val old = previousMerchant?.trim()
        if (!newMerchant.isNullOrBlank() && !old.isNullOrBlank() && !old.equals(newMerchant, true)) {
            aliasDao.upsert(AliasEntity(AliasNormalizer.key(old), newMerchant))
        }
        return id
    }

    suspend fun delete(id: String) {
        val row = receiptDao.get(id)
        receiptDao.delete(id)
        row?.imagePath?.let { path ->
            imageStore.file(path).takeIf { it.exists() }?.delete()
        }
    }

    suspend fun delete(ids: Collection<String>) {
        ids.forEach { delete(it) }
    }

    fun toCsv(
        rows: List<ReceiptEntity>,
        custom: List<CustomCategoryEntity> = emptyList(),
    ): String {
        return ReceiptCsv.document(
            rows.map { row ->
                ReceiptCsvRow(
                    date = row.dateIso.orEmpty(),
                    merchant = row.merchant.orEmpty(),
                    total = row.total?.toString().orEmpty(),
                    subtotal = row.subtotal?.toString().orEmpty(),
                    tax = row.taxTotal?.toString().orEmpty(),
                    taxKinds = decodeTaxes(row.taxJson).joinToString("/") { it.kind.name },
                    currency = row.currency.orEmpty(),
                    purpose = runCatching { Purpose.valueOf(row.purpose) }.getOrDefault(Purpose.PERSONAL),
                    categoryId = row.categoryId.orEmpty(),
                    categoryLabel = CategoryCatalog.label(row.categoryId, custom).orEmpty(),
                    gstHstNumber = row.gstHstNumber.orEmpty(),
                    paymentMethod = row.paymentMethod.orEmpty(),
                    receiptNumber = row.receiptNumber.orEmpty(),
                    lineItems = ReceiptRecordLogic.formatLineItems(decodeItems(row.lineItemsJson)),
                    imagePath = row.imagePath,
                    dateAdded = ReceiptRecordLogic.dateAddedForExport(row.createdAt),
                )
            },
        )
    }

    fun decodeTaxes(raw: String): List<TaxAmount> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredTax>>(raw).map { it.toModel() }
        }.getOrDefault(emptyList())
    }

    /** Resolving on read covers receipts stored before quantity was always filled in. */
    fun decodeItems(raw: String): List<LineItem> {
        if (raw.isBlank()) return emptyList()
        val stored = runCatching { json.decodeFromString<List<LineItem>>(raw) }
            .getOrDefault(emptyList())
        return LineItemQuantity.resolveAll(stored)
    }

    fun toParsed(entity: ReceiptEntity): ParsedReceipt {
        return ParsedReceipt(
            merchant = Field(entity.merchant, entity.merchantConfidence, sourceOf(entity.merchantSource)),
            date = Field(entity.dateIso, entity.dateConfidence, FieldSource.USER),
            currency = Field(entity.currency, 1f, FieldSource.USER),
            subtotal = Field(entity.subtotal, 1f, FieldSource.USER),
            taxes = decodeTaxes(entity.taxJson),
            total = Field(entity.total, entity.totalConfidence, FieldSource.USER),
            paymentMethod = Field(entity.paymentMethod, 1f, FieldSource.USER),
            gstHstNumber = Field(entity.gstHstNumber, 1f, FieldSource.USER),
            receiptNumber = Field(entity.receiptNumber, 1f, FieldSource.USER),
            lineItems = decodeItems(entity.lineItemsJson),
            suggestedCategoryId = Field(entity.categoryId, 1f, FieldSource.USER),
            rawText = entity.rawText,
        )
    }

    private fun sourceOf(raw: String): FieldSource =
        runCatching { FieldSource.valueOf(raw) }.getOrDefault(FieldSource.USER)
}

@kotlinx.serialization.Serializable
data class StoredTax(
    val kind: String,
    val amount: Double,
    val rate: Double? = null,
)

private fun TaxAmount.toStored() = StoredTax(kind.name, amount, rate)

private fun StoredTax.toModel() = TaxAmount(
    kind = runCatching { TaxKind.valueOf(kind) }.getOrDefault(TaxKind.UNKNOWN),
    amount = amount,
    rate = rate,
    confidence = 1f,
    source = FieldSource.USER,
)
