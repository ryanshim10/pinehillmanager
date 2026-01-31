package com.ryan.pinehill

import android.app.Application
import com.ryan.pinehill.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PinehillApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    private val applicationScope = CoroutineScope(Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        
        // 앱 시작 시 19세대 초기 데이터 삽입 (처음 1회만)
        applicationScope.launch {
            if (database.unitDao().getUnitCount() == 0) {
                seedUnits()
            }
        }
    }
    
    private suspend fun seedUnits() {
        val units = listOf(
            Unit("PINE-201", 201, 2, UnitStatus.RENTED, "1.5룸", "500-50"),
            Unit("PINE-202", 202, 2, UnitStatus.RENTED),
            Unit("PINE-203", 203, 2, UnitStatus.RENTED),
            Unit("PINE-204", 204, 2, UnitStatus.LAWSUIT),
            Unit("PINE-205", 205, 2, UnitStatus.RENTED, "투룸", "500-60"),
            Unit("PINE-206", 206, 2, UnitStatus.RENTED, "투룸", "500-60"),
            Unit("PINE-207", 207, 2, UnitStatus.RENTED),
            Unit("PINE-301", 301, 3, UnitStatus.RENTED, "1.5룸", "500-50"),
            Unit("PINE-302", 302, 3, UnitStatus.RENTED),
            Unit("PINE-303", 303, 3, UnitStatus.RENTED),
            Unit("PINE-304", 304, 3, UnitStatus.RENTED),
            Unit("PINE-305", 305, 3, UnitStatus.RENTED, "투룸", "500-60"),
            Unit("PINE-306", 306, 3, UnitStatus.RENTED, "투룸", "500-60"),
            Unit("PINE-307", 307, 3, UnitStatus.RENTED),
            Unit("PINE-401", 401, 4, UnitStatus.RENTED, "1.5룸", "500-50"),
            Unit("PINE-402", 402, 4, UnitStatus.RENTED),
            Unit("PINE-403", 403, 4, UnitStatus.RENTED),
            Unit("PINE-404", 404, 4, UnitStatus.RENTED),
            Unit("PINE-405", 405, 4, UnitStatus.MAINTENANCE)
        )
        database.unitDao().insertUnits(units)
    }
}
