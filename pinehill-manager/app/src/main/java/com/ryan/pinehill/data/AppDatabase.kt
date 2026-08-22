package com.ryan.pinehill.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.ryan.pinehill.data.dao.*
import com.ryan.pinehill.data.model.Expense
import com.ryan.pinehill.data.model.ExpenseCategory
import com.ryan.pinehill.data.model.ExpenseSource
import com.ryan.pinehill.data.model.Payment
import com.ryan.pinehill.data.model.PaymentSource
import com.ryan.pinehill.data.model.PaymentStatus
import com.ryan.pinehill.data.model.Tenant
import com.ryan.pinehill.data.model.Unit as RentalUnit
import com.ryan.pinehill.data.model.UnitStatus

class Converters {
    @TypeConverter
    fun fromUnitStatus(value: UnitStatus): String = value.name

    @TypeConverter
    fun toUnitStatus(value: String): UnitStatus = UnitStatus.valueOf(value)

    @TypeConverter
    fun fromPaymentStatus(value: PaymentStatus): String = value.name

    @TypeConverter
    fun toPaymentStatus(value: String): PaymentStatus = PaymentStatus.valueOf(value)

    @TypeConverter
    fun fromPaymentSource(value: PaymentSource): String = value.name

    @TypeConverter
    fun toPaymentSource(value: String): PaymentSource = PaymentSource.valueOf(value)

    @TypeConverter
    fun fromExpenseCategory(value: ExpenseCategory): String = value.name

    @TypeConverter
    fun toExpenseCategory(value: String): ExpenseCategory = ExpenseCategory.valueOf(value)

    @TypeConverter
    fun fromExpenseSource(value: ExpenseSource): String = value.name

    @TypeConverter
    fun toExpenseSource(value: String): ExpenseSource = ExpenseSource.valueOf(value)
}

@Database(
    entities = [RentalUnit::class, Tenant::class, Payment::class, Expense::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun unitDao(): UnitDao
    abstract fun tenantDao(): TenantDao
    abstract fun paymentDao(): PaymentDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pinehill_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
