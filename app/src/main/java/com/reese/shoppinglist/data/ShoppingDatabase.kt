package com.reese.shoppinglist.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Store::class, Item::class, StoreItem::class, ListEntry::class],
    version = 6,
    exportSchema = false
)
abstract class ShoppingDatabase : RoomDatabase() {

    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile
        private var INSTANCE: ShoppingDatabase? = null

        // Adds StoreItem.showIfAisleUnassigned (default true)
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE store_items
                    ADD COLUMN showIfAisleUnassigned INTEGER NOT NULL DEFAULT 1
                    """.trimIndent()
                )
            }
        }

        // Repurposes showIfAisleUnassigned to mean "store-specific item"; reset all existing to 0
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE store_items SET showIfAisleUnassigned = 0")
            }
        }

        fun getDatabase(context: Context): ShoppingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShoppingDatabase::class.java,
                    "shopping_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_5_6)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
