package com.example.core.repository

import android.content.Context
import com.example.core.data.AppDatabase
import com.example.core.data.TunisianMenuTemplate
import com.example.core.data.dao.OrderWithLines
import com.example.core.data.entity.*
import com.example.core.model.*
import com.example.core.network.LocalNetworkManager
import com.example.core.printer.ArabicReceiptRenderer
import com.example.core.printer.PosPrinterManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.example.core.backup.BackupRestoreManager
import com.example.core.license.LicenseManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class PosRepository(
    private val context: Context,
    val database: AppDatabase,
    val networkManager: LocalNetworkManager,
    val printerManager: PosPrinterManager,
    val licenseManager: LicenseManager,
    val backupRestoreManager: BackupRestoreManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stateMachine = OrderStateMachine()

    // Current logged-in staff
    val currentStaff = MutableStateFlow<StaffEntity?>(null)

    // Current selected role for this device
    val currentRole = MutableStateFlow<Role>(Role.COUNTER)

    // Café settings
    val cafeName = MutableStateFlow("مقهى النخيل - تونس")
    val paymentBeforePreparation = MutableStateFlow(true)
    val awaitingPaymentAlertMinutes = MutableStateFlow(5)
    val enableTablesMode = MutableStateFlow(true) // Toggle between Tables Mode and Direct/Counter Mode

    init {
        scope.launch {
            checkAndSeedDatabase()
            listenToNetworkMessages()
        }
    }

    private suspend fun checkAndSeedDatabase() {
        val manager = database.staffDao().getManager()
        if (manager == null) {
            // Seed staff
            database.staffDao().insertAll(TunisianMenuTemplate.createDefaultStaff())
            // Seed categories
            database.menuDao().insertCategories(TunisianMenuTemplate.categories)
            // Seed products
            database.menuDao().insertProducts(TunisianMenuTemplate.products)
            // Seed modifiers
            database.menuDao().insertModifiers(TunisianMenuTemplate.modifiers)
            // Seed tables
            database.diningTableDao().insertTables(TunisianMenuTemplate.createInitialTables(12))

            // Initial seeding complete - staff must log in with PIN
        }
    }

    fun logout() {
        currentStaff.value = null
    }

    private fun listenToNetworkMessages() {
        scope.launch {
            networkManager.incomingMessages.collect { msg ->
                // Sync notification received
                when (msg.type) {
                    "PAYMENT_REQUESTED" -> {
                        // Waiter device gets alert
                    }
                    "ORDER_READY" -> {
                        // Waiter device vibrates/beeps
                    }
                }
            }
        }
    }

    // --- Menu & Tables Streams ---
    fun getAllCategories(): Flow<List<CategoryEntity>> = database.menuDao().getAllCategories()
    fun getAllProducts(): Flow<List<ProductEntity>> = database.menuDao().getAllProducts()
    fun getAvailableProducts(): Flow<List<ProductEntity>> = database.menuDao().getAllAvailableProducts()
    fun getAllTables(): Flow<List<DiningTableEntity>> = database.diningTableDao().getAllTables()
    fun getAllStaff(): Flow<List<StaffEntity>> = database.staffDao().getAllActiveStaff()

    suspend fun addStaff(name: String, role: Role, pin: String, manager: StaffEntity): StaffEntity {
        val newStaff = StaffEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            role = role.name,
            pinHash = pin.trim(),
            active = true
        )
        database.staffDao().insertStaff(newStaff)
        logAudit(
            action = "ADD_STAFF",
            details = "إضافة حساب موظف جديد: $name بدور ${role.titleAr}",
            staff = manager
        )
        return newStaff
    }

    suspend fun updateStaff(staffId: String, newName: String, newRole: Role, newPin: String, manager: StaffEntity) {
        val existing = database.staffDao().getStaffById(staffId) ?: return
        val updated = existing.copy(
            name = newName.trim(),
            role = newRole.name,
            pinHash = newPin.trim()
        )
        database.staffDao().updateStaff(updated)
        logAudit(
            action = "UPDATE_STAFF",
            details = "تعديل بيانات ${existing.name}: الاسم الجديد ($newName)، الدور (${newRole.titleAr})، وتحديث رمز PIN",
            staff = manager
        )
    }

    suspend fun deleteStaff(staffId: String, manager: StaffEntity): Boolean {
        val existing = database.staffDao().getStaffById(staffId) ?: return false
        if (existing.role == "MANAGER") {
            val allStaff = database.staffDao().getAllActiveStaff().first()
            val managerCount = allStaff.count { it.role == "MANAGER" }
            if (managerCount <= 1) {
                return false
            }
        }
        database.staffDao().deleteStaffById(staffId)
        logAudit(
            action = "DELETE_STAFF",
            details = "حذف حساب الموظف: ${existing.name} (${existing.role})",
            staff = manager
        )
        return true
    }

    // --- Order Streams ---
    fun getActiveOrders(): Flow<List<OrderWithLines>> = database.orderDao().getActiveOrdersWithLines()
    fun getAllOrders(): Flow<List<OrderWithLines>> = database.orderDao().getAllOrdersWithLines()
    fun observeOrder(orderId: String): Flow<OrderWithLines?> = database.orderDao().observeOrderWithLinesById(orderId)

    // --- Shift Streams ---
    fun getActiveShift(): Flow<ShiftEntity?> = database.shiftDao().getActiveShift()
    fun getLedgerForShift(shiftId: String): Flow<List<CashLedgerEntryEntity>> =
        database.shiftDao().getLedgerEntriesForShift(shiftId)

    // --- Create Order ---
    suspend fun createOrder(
        tableId: String?,
        tableName: String,
        isTakeaway: Boolean,
        items: List<Pair<ProductEntity, Int>>, // Product and quantity
        modifiersNotes: Map<String, String> = emptyMap(),
        waiter: StaffEntity
    ): String {
        val nextOrderNumber = (database.orderDao().getMaxOrderNumber() ?: 0) + 1
        val orderId = UUID.randomUUID().toString()

        var totalMillimes = 0L
        val lines = items.map { (product, qty) ->
            val unitPrice = product.priceMillimes
            totalMillimes += (unitPrice * qty)
            OrderLineEntity(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                productId = product.id,
                productNameAr = product.nameAr,
                unitPriceMillimes = unitPrice,
                quantity = qty,
                notes = modifiersNotes[product.id] ?: ""
            )
        }

        val order = OrderEntity(
            id = orderId,
            orderNumber = nextOrderNumber,
            tableId = tableId,
            tableName = tableName,
            isTakeaway = isTakeaway,
            waiterId = waiter.id,
            waiterName = waiter.name,
            state = OrderState.NEW.name,
            totalMillimes = totalMillimes,
            paymentFirst = paymentBeforePreparation.value,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        database.orderDao().insertOrder(order)
        database.orderDao().insertOrderLines(lines)

        // Update table state
        if (tableId != null) {
            val table = database.diningTableDao().getTableById(tableId)
            if (table != null) {
                database.diningTableDao().updateTable(
                    table.copy(
                        status = "OCCUPIED",
                        activeOrderId = orderId,
                        activeWaiterName = waiter.name
                    )
                )
            }
        }

        logAudit(
            action = "CREATE_ORDER",
            details = "إنشاء طلب جديد رقم #$nextOrderNumber لطاولة $tableName بمبلغ ${Money(totalMillimes)}",
            staff = waiter
        )

        networkManager.broadcastMessage("ORDER_CREATED", orderId)
        return orderId
    }

    // --- Transition Order State ---
    suspend fun transitionOrder(
        orderId: String,
        targetState: OrderState,
        cancellationReason: String? = null,
        receivedMillimes: Long = 0L,
        managerApproved: Boolean = false,
        actingStaff: StaffEntity
    ): TransitionResult {
        val orderWithLines = database.orderDao().getOrderWithLinesById(orderId)
            ?: return TransitionResult.Error("الطلب غير موجود")

        val currentOrder = orderWithLines.order
        val currentState = OrderState.fromString(currentOrder.state)

        val result = stateMachine.canTransition(
            currentState = currentState,
            targetState = targetState,
            paymentFirst = currentOrder.paymentFirst,
            cancellationReason = cancellationReason,
            isManagerApproved = managerApproved
        )

        if (result is TransitionResult.Success) {
            var updatedOrder = currentOrder.copy(
                state = targetState.name,
                updatedAt = System.currentTimeMillis(),
                version = currentOrder.version + 1
            )

            // When paid, compute change & update cash ledger
            if (targetState == OrderState.PAID) {
                val change = (receivedMillimes - currentOrder.totalMillimes).coerceAtLeast(0L)
                updatedOrder = updatedOrder.copy(
                    receivedMillimes = receivedMillimes,
                    changeMillimes = change
                )

                // Record in Cash Ledger for Waiter & Active Shift
                val activeShift = database.shiftDao().getActiveShift().firstOrNull()
                if (activeShift != null) {
                    val ledgerEntry = CashLedgerEntryEntity(
                        shiftId = activeShift.id,
                        waiterId = currentOrder.waiterId,
                        waiterName = currentOrder.waiterName,
                        orderId = currentOrder.id,
                        amountMillimes = currentOrder.totalMillimes,
                        notes = "خلاص طلب #${currentOrder.orderNumber}"
                    )
                    database.shiftDao().insertLedgerEntry(ledgerEntry)

                    // Update shift expected cash
                    val newExpected = activeShift.expectedCashMillimes + currentOrder.totalMillimes
                    database.shiftDao().updateShift(activeShift.copy(expectedCashMillimes = newExpected))
                }
            }

            if (targetState == OrderState.CANCELLED) {
                updatedOrder = updatedOrder.copy(
                    cancellationReason = cancellationReason ?: "ملغى"
                )
                // Free table if occupied
                if (currentOrder.tableId != null) {
                    val table = database.diningTableDao().getTableById(currentOrder.tableId)
                    if (table != null) {
                        database.diningTableDao().updateTable(
                            table.copy(status = "FREE", activeOrderId = null, activeWaiterName = null)
                        )
                    }
                }
            }

            if (targetState == OrderState.SERVED) {
                // Free table upon service completion
                if (currentOrder.tableId != null) {
                    val table = database.diningTableDao().getTableById(currentOrder.tableId)
                    if (table != null) {
                        database.diningTableDao().updateTable(
                            table.copy(status = "FREE", activeOrderId = null, activeWaiterName = null)
                        )
                    }
                }
            }

            database.orderDao().updateOrder(updatedOrder)

            logAudit(
                action = "STATE_CHANGE_${targetState.name}",
                details = "تغيير حالة الطلب #${currentOrder.orderNumber} إلى ${targetState.labelAr}",
                staff = actingStaff
            )

            // Auto-print preparation ticket when required!
            if (result.requiresPrintTicket) {
                scope.launch {
                    val ticketBitmap = ArabicReceiptRenderer.renderKitchenTicket(
                        orderWithLines.copy(order = updatedOrder)
                    )
                    printerManager.printBitmap(ticketBitmap)
                }
            }

            // Broadcast event over LAN
            val eventType = when (targetState) {
                OrderState.AWAITING_PAYMENT -> "PAYMENT_REQUESTED"
                OrderState.PAID -> "ORDER_PAID"
                OrderState.READY -> "ORDER_READY"
                OrderState.SERVED -> "ORDER_SERVED"
                OrderState.CANCELLED -> "ORDER_CANCELLED"
                else -> "ORDER_UPDATED"
            }
            networkManager.broadcastMessage(eventType, orderId)
        }

        return result
    }

    // --- Order Edit (Delta line after Paid) ---
    suspend fun addOrderDelta(
        orderId: String,
        description: String,
        deltaMillimes: Long,
        staff: StaffEntity
    ) {
        val orderWithLines = database.orderDao().getOrderWithLinesById(orderId) ?: return
        val currentOrder = orderWithLines.order

        val delta = OrderDeltaEntity(
            orderId = orderId,
            description = description,
            deltaMillimes = deltaMillimes,
            approvedByStaffId = staff.id
        )
        database.orderDao().insertOrderDelta(delta)

        val newTotal = currentOrder.totalMillimes + deltaMillimes
        database.orderDao().updateOrder(
            currentOrder.copy(
                totalMillimes = newTotal,
                updatedAt = System.currentTimeMillis(),
                version = currentOrder.version + 1
            )
        )

        logAudit(
            action = "ADD_ORDER_DELTA",
            details = "إضافة تعديل للطلب #${currentOrder.orderNumber}: $description (${Money(deltaMillimes)})",
            staff = staff
        )
    }

    // --- Shift Operations ---
    suspend fun openShift(
        staff: StaffEntity,
        startingFloatMillimes: Long
    ) {
        val shift = ShiftEntity(
            openedByStaffId = staff.id,
            openedByName = staff.name,
            role = staff.role,
            startingFloatMillimes = startingFloatMillimes,
            expectedCashMillimes = startingFloatMillimes,
            actualCashMillimes = 0L,
            differenceMillimes = 0L,
            isClosed = false
        )
        database.shiftDao().insertShift(shift)

        logAudit(
            action = "OPEN_SHIFT",
            details = "فتح وردية جديدة بواسطة ${staff.name} برصيد بداية ${Money(startingFloatMillimes)}",
            staff = staff
        )
    }

    suspend fun closeShift(
        shiftId: String,
        actualCashCounted: Long,
        managerPin: String,
        staff: StaffEntity
    ): ShiftEntity? {
        val shift = database.shiftDao().getAllShifts().first().firstOrNull { it.id == shiftId } ?: return null
        val diff = actualCashCounted - shift.expectedCashMillimes

        val closed = shift.copy(
            closedAt = System.currentTimeMillis(),
            actualCashMillimes = actualCashCounted,
            differenceMillimes = diff,
            managerApprovalPin = managerPin,
            isClosed = true
        )
        database.shiftDao().updateShift(closed)

        logAudit(
            action = "CLOSE_SHIFT",
            details = "غلق الوردية: المتوقع ${Money(shift.expectedCashMillimes)}، الفعلي ${Money(actualCashCounted)}، الفارق ${Money(diff)}",
            staff = staff
        )

        // Print shift report
        scope.launch {
            val reportBitmap = ArabicReceiptRenderer.renderShiftReport(closed, cafeName.value)
            printerManager.printBitmap(reportBitmap)
        }

        return closed
    }

    // --- Audit Logging ---
    suspend fun logAudit(action: String, details: String, staff: StaffEntity?) {
        val log = AuditLogEntity(
            action = action,
            details = details,
            staffId = staff?.id ?: "system",
            staffName = staff?.name ?: "النظام",
            timestamp = System.currentTimeMillis()
        )
        database.auditDao().insertLog(log)
    }
}
