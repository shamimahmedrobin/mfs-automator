package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PaymentTransaction::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun paymentTransactionDao(): PaymentTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE payment_transactions ADD COLUMN deviceId TEXT DEFAULT NULL")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE payment_transactions ADD COLUMN errorLog TEXT DEFAULT NULL")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE payment_transactions ADD COLUMN currentBalance TEXT DEFAULT NULL")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE payment_transactions ADD COLUMN deviceId TEXT DEFAULT NULL")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE payment_transactions ADD COLUMN errorLog TEXT DEFAULT NULL")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE payment_transactions ADD COLUMN currentBalance TEXT DEFAULT NULL")
                } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mfs_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_1_3)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
