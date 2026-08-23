package com.ryan.pinehill.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ryan.pinehill.data.dao.*
import com.ryan.pinehill.data.model.*
import com.ryan.pinehill.data.model.Unit as RentalUnit

class Converters {
    @TypeConverter fun fromUnitStatus(value: UnitStatus): String = value.name
    @TypeConverter fun toUnitStatus(value: String): UnitStatus = UnitStatus.valueOf(value)
    @TypeConverter fun fromPaymentStatus(value: PaymentStatus): String = value.name
    @TypeConverter fun toPaymentStatus(value: String): PaymentStatus = PaymentStatus.valueOf(value)
    @TypeConverter fun fromPaymentSource(value: PaymentSource): String = value.name
    @TypeConverter fun toPaymentSource(value: String): PaymentSource = PaymentSource.valueOf(value)
    @TypeConverter fun fromExpenseCategory(value: ExpenseCategory): String = value.name
    @TypeConverter fun toExpenseCategory(value: String): ExpenseCategory = ExpenseCategory.valueOf(value)
    @TypeConverter fun fromExpenseSource(value: ExpenseSource): String = value.name
    @TypeConverter fun toExpenseSource(value: String): ExpenseSource = ExpenseSource.valueOf(value)
}

@Database(
    entities = [
        RentalUnit::class,
        Tenant::class,
        Payment::class,
        Expense::class,
        ContractRecord::class,
        PaymentMatchRule::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun unitDao(): UnitDao
    abstract fun tenantDao(): TenantDao
    abstract fun paymentDao(): PaymentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun contractDao(): ContractDao
    abstract fun paymentMatchRuleDao(): PaymentMatchRuleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS contract_records (
                        contractId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        tenantName TEXT NOT NULL,
                        tenantPhone TEXT NOT NULL,
                        unitId TEXT NOT NULL,
                        deposit INTEGER NOT NULL,
                        monthlyRent INTEGER NOT NULL,
                        paymentDay INTEGER NOT NULL,
                        startDate TEXT NOT NULL,
                        endDate TEXT NOT NULL,
                        documentUri TEXT NOT NULL,
                        sourceName TEXT NOT NULL,
                        rawOcrText TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contract_records_tenantName ON contract_records (tenantName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contract_records_unitId ON contract_records (unitId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contract_records_startDate ON contract_records (startDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contract_records_endDate ON contract_records (endDate)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS payment_match_rules (
                        ruleId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bankName TEXT NOT NULL,
                        senderName TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        dayOfMonth INTEGER NOT NULL,
                        unitId TEXT NOT NULL,
                        tenantName TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payment_match_rules_bankName_senderName_amount_dayOfMonth ON payment_match_rules (bankName, senderName, amount, dayOfMonth)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_match_rules_unitId ON payment_match_rules (unitId)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pinehill_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
