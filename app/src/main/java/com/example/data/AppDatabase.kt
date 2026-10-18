package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PaymentTransaction::class], version = 6, exportSchema = false)
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

        /**
         * One-time re-classification: older app versions marked mobile
         * recharge / bill-pay SMS as "in". Re-run direction detection on the
         * stored SMS bodies so history shows the correct IN/OUT.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    val cursor = db.query("SELECT id, body FROM payment_transactions")
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val body = cursor.getString(1) ?: continue
                        val newDir = try {
                            com.example.sms.SmsParser.detectDirection(body)
                        } catch (_: Exception) {
                            continue
                        }
                        try {
                            db.execSQL(
                                "UPDATE payment_transactions SET direction = ? WHERE id = ?",
                                arrayOf<Any?>(newDir, id)
                            )
                        } catch (_: Exception) {}
                    }
                    cursor.close()
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_2_4 = object : Migration(2, 4) {            override fun migrate(db: SupportSQLiteDatabase) {                try {
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_1_4, MIGRATION_2_4)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
