package com.ryan.pinehill

import android.app.Application
import com.ryan.pinehill.data.AppDatabase
import com.ryan.pinehill.data.model.Unit as RentalUnit
import com.ryan.pinehill.data.model.UnitStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PinehillApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            if (database.unitDao().getUnitCount() == 0) {
                seedUnits()
            }
        }
    }

    private suspend fun seedUnits() {
        val units = listOf(
            RentalUnit("PINE-201", 201, 2, UnitStatus.RENTED, "1.5룸", "500-50"),
            RentalUnit("PINE-202", 202, 2, UnitStatus.RENTED),
            RentalUnit("PINE-203", 203, 2, UnitStatus.RENTED),
            RentalUnit("PINE-204", 204, 2, UnitStatus.LAWSUIT),
            RentalUnit("PINE-205", 205, 2, UnitStatus.RENTED, "투룸", "500-60"),
            RentalUnit("PINE-206", 206, 2, UnitStatus.RENTED, "투룸", "500-60"),
            RentalUnit("PINE-207", 207, 2, UnitStatus.RENTED),
            RentalUnit("PINE-301", 301, 3, UnitStatus.RENTED, "1.5룸", "500-50"),
            RentalUnit("PINE-302", 302, 3, UnitStatus.RENTED),
            RentalUnit("PINE-303", 303, 3, UnitStatus.RENTED),
            RentalUnit("PINE-304", 304, 3, UnitStatus.RENTED),
            RentalUnit("PINE-305", 305, 3, UnitStatus.RENTED, "투룸", "500-60"),
            RentalUnit("PINE-306", 306, 3, UnitStatus.RENTED, "투룸", "500-60"),
            RentalUnit("PINE-307", 307, 3, UnitStatus.RENTED),
            RentalUnit("PINE-401", 401, 4, UnitStatus.RENTED, "1.5룸", "500-50"),
            RentalUnit("PINE-402", 402, 4, UnitStatus.RENTED),
            RentalUnit("PINE-403", 403, 4, UnitStatus.RENTED),
            RentalUnit("PINE-404", 404, 4, UnitStatus.RENTED),
            RentalUnit("PINE-405", 405, 4, UnitStatus.MAINTENANCE)
        )
        database.unitDao().insertUnits(units)
    }
}
