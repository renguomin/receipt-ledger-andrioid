package com.receiptledger.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Recorded in every backup so a restore can refuse an archive written by a newer build; Room
 * cannot migrate a database backwards and would crash on every launch afterwards.
 */
const val RECEIPT_DATABASE_VERSION = 3

@Database(
    entities = [ReceiptEntity::class, AliasEntity::class, CustomCategoryEntity::class],
    version = RECEIPT_DATABASE_VERSION,
    exportSchema = false,
)
abstract class ReceiptDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    abstract fun aliasDao(): AliasDao
    abstract fun customCategoryDao(): CustomCategoryDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS custom_categories (
                id TEXT NOT NULL PRIMARY KEY,
                label TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE receipts ADD COLUMN receiptNumber TEXT")
    }
}
