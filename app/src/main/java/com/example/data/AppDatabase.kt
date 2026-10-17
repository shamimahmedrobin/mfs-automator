package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PaymentTransaction::class], version = 5, exportSchema = false)
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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE payment_transactions ADD COLUMN isSuspicious INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE payment_transactions ADD COLUMN suspicionReason TEXT DEFAULT NULL")
                } catch (_: Exception) {}
                try {
                    // Hard dedupe guard: trxIds are normalized before insert.
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payment_transactions_trxId ON payment_transactions(trxId)")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_1_4 = object : Migration(1, 4) {
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
                try {
                    db.execSQL("ALTER TABLE payment_transactions ADD COLUMN isSuspicious INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE payment_transactions ADD COLUMN suspicionReason TEXT DEFAULT NULL")
                } catch (_: Exception) {}
                try {
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payment_transactions_trxId ON payment_transactions(trxId)")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE payment_transactions ADD COLUMN direction TEXT NOT NULL DEFAULT 'in'")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE payment_transactions ADD COLUMN fee TEXT DEFAULT NULL")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_2_4 = object : Migration(2, 4) {            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE payment_transactions ADD COLUMN currentBalance TEXT DEFAULT NULL")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE payment_transactions ADD COLUMN isSuspicious INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE payment_transactions ADD COLUMN suspicionReason TEXT DEFAULT NULL")
                } catch (_: Exception) {}
                try {
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payment_transactions_trxId ON payment_transactions(trxId)")
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_1_4, MIGRATION_2_4)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
