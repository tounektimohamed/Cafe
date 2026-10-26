package com.example.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.core.data.dao.*
import com.example.core.data.entity.*

@Database(
    entities = [
        StaffEntity::class,
        CategoryEntity::class,
        ProductEntity::class,
        ModifierOptionEntity::class,
        DiningTableEntity::class,
        OrderEntity::class,
        OrderLineEntity::class,
        OrderDeltaEntity::class,
        ShiftEntity::class,
        CashLedgerEntryEntity::class,
        AuditLogEntity::class,
        PrintJobEntity::class,
        DeviceEntity::class,
        AppSettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun staffDao(): StaffDao
    abstract fun menuDao(): MenuDao
    abstract fun diningTableDao(): DiningTableDao
    abstract fun orderDao(): OrderDao
    abstract fun shiftDao(): ShiftDao
    abstract fun auditDao(): AuditDao
    abstract fun printJobDao(): PrintJobDao
    abstract fun deviceDao(): DeviceDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cafeflow_pos.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
