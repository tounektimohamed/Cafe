package com.example.core.data.dao

import androidx.room.*
import com.example.core.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff WHERE active = 1 ORDER BY name ASC")
    fun getAllActiveStaff(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff WHERE id = :id LIMIT 1")
    suspend fun getStaffById(id: String): StaffEntity?

    @Query("SELECT * FROM staff WHERE role = 'MANAGER' LIMIT 1")
    suspend fun getManager(): StaffEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaff(staff: StaffEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(staffList: List<StaffEntity>)

    @Update
    suspend fun updateStaff(staff: StaffEntity)

    @Query("DELETE FROM staff WHERE id = :id")
    suspend fun deleteStaffById(id: String)
}

@Dao
interface MenuDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM products WHERE isAvailable = 1 ORDER BY sortOrder ASC")
    fun getAllAvailableProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY categoryId, sortOrder ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE categoryId = :categoryId AND isAvailable = 1")
    fun getProductsByCategory(categoryId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isFavorite = 1 AND isAvailable = 1")
    fun getFavoriteProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM modifier_options")
    fun getAllModifiers(): Flow<List<ModifierOptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModifiers(modifiers: List<ModifierOptionEntity>)
}

@Dao
interface DiningTableDao {
    @Query("SELECT * FROM dining_tables ORDER BY tableNumber ASC")
    fun getAllTables(): Flow<List<DiningTableEntity>>

    @Query("SELECT * FROM dining_tables WHERE id = :id LIMIT 1")
    suspend fun getTableById(id: String): DiningTableEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTables(tables: List<DiningTableEntity>)

    @Update
    suspend fun updateTable(table: DiningTableEntity)
}

data class OrderWithLines(
    @Embedded val order: OrderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "orderId"
    )
    val lines: List<OrderLineEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "orderId"
    )
    val deltas: List<OrderDeltaEntity>
)

@Dao
interface OrderDao {
    @Transaction
    @Query("SELECT * FROM orders WHERE state NOT IN ('SERVED', 'CANCELLED') ORDER BY createdAt DESC")
    fun getActiveOrdersWithLines(): Flow<List<OrderWithLines>>

    @Transaction
    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun getAllOrdersWithLines(): Flow<List<OrderWithLines>>

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun getOrderWithLinesById(orderId: String): OrderWithLines?

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    fun observeOrderWithLinesById(orderId: String): Flow<OrderWithLines?>

    @Query("SELECT MAX(orderNumber) FROM orders")
    suspend fun getMaxOrderNumber(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderLines(lines: List<OrderLineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderDelta(delta: OrderDeltaEntity)

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Transaction
    @Query("SELECT * FROM orders WHERE createdAt >= :startTime AND createdAt <= :endTime ORDER BY createdAt DESC")
    suspend fun getOrdersBetween(startTime: Long, endTime: Long): List<OrderWithLines>
}

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE isClosed = 0 LIMIT 1")
    fun getActiveShift(): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts ORDER BY openedAt DESC")
    fun getAllShifts(): Flow<List<ShiftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: ShiftEntity)

    @Update
    suspend fun updateShift(shift: ShiftEntity)

    @Query("SELECT * FROM cash_ledger_entries WHERE shiftId = :shiftId ORDER BY timestamp DESC")
    fun getLedgerEntriesForShift(shiftId: String): Flow<List<CashLedgerEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedgerEntry(entry: CashLedgerEntryEntity)
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLogEntity)
}

@Dao
interface PrintJobDao {
    @Query("SELECT * FROM print_jobs WHERE status = 'PENDING' ORDER BY createdAt ASC")
    fun getPendingPrintJobs(): Flow<List<PrintJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrintJob(job: PrintJobEntity)

    @Update
    suspend fun updatePrintJob(job: PrintJobEntity)
}

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY lastSeen DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity)

    @Update
    suspend fun updateDevice(device: DeviceEntity)
}

@Dao
interface AppSettingDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    fun observeSetting(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: AppSettingEntity)
}
