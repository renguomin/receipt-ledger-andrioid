package com.receiptledger.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val imagePath: String,
    val merchant: String?,
    val merchantConfidence: Float,
    val merchantSource: String,
    val dateIso: String?,
    val dateConfidence: Float,
    val currency: String?,
    val subtotal: Double?,
    val taxTotal: Double?,
    val taxJson: String,
    val total: Double?,
    val totalConfidence: Float,
    val paymentMethod: String?,
    val gstHstNumber: String?,
    val receiptNumber: String? = null,
    val lineItemsJson: String,
    val purpose: String,
    val categoryId: String?,
    val rawText: String,
    val year: Int,
)

@Entity(tableName = "merchant_aliases")
data class AliasEntity(
    @PrimaryKey val rawKey: String,
    val canonical: String,
)

@Entity(tableName = "custom_categories")
data class CustomCategoryEntity(
    @PrimaryKey val id: String,
    val label: String,
    val createdAt: Long,
)
