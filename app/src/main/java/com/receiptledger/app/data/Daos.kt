package com.receiptledger.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Query("SELECT * FROM receipts ORDER BY dateIso DESC, createdAt DESC")
    fun observeAll(): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun get(id: String): ReceiptEntity?

    @Query("SELECT * FROM receipts")
    suspend fun getAll(): List<ReceiptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(receipt: ReceiptEntity)

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CustomCategoryDao {
    @Query("SELECT * FROM custom_categories ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<CustomCategoryEntity>>

    @Query("SELECT * FROM custom_categories WHERE id = :id")
    suspend fun get(id: String): CustomCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: CustomCategoryEntity)

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AliasDao {
    @Query("SELECT * FROM merchant_aliases")
    suspend fun all(): List<AliasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alias: AliasEntity)
}
