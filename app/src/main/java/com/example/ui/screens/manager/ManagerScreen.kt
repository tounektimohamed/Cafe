package com.example.ui.screens.manager

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.data.entity.ProductEntity
import com.example.core.data.entity.StaffEntity
import com.example.core.model.Money
import com.example.core.model.OrderState
import com.example.core.model.Role
import com.example.core.printer.ArabicReceiptRenderer
import com.example.core.repository.PosRepository
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.components.MoneyBadge
import com.example.ui.components.ReceiptPreviewDialog
import com.example.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerScreen(
    repository: PosRepository,
    currentStaff: StaffEntity,
    onNavigateToRoleSelect: () -> Unit,
    onNavigateToWizard: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val allOrders by repository.getAllOrders().collectAsState(initial = emptyList())
    val allProducts by repository.getAllProducts().collectAsState(initial = emptyList())
    val allStaff by repository.getAllStaff().collectAsState(initial = emptyList())
    val activeShift by repository.getActiveShift().collectAsState(initial = null)
    val auditLogs by repository.database.auditDao().getRecentAuditLogs().collectAsState(initial = emptyList())
    val connectionStatus by repository.networkManager.connectionStatus.collectAsState()
    val isHost by repository.networkManager.isHost.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Sales, 1 = Staff/PIN, 2 = Shifts, 3 = Menu, 4 = Audit
    var showShiftCloseDialog by remember { mutableStateOf(false) }
    var showAddStaffDialog by remember { mutableStateOf(false) }
    var staffToEdit by remember { mutableStateOf<StaffEntity?>(null) }
    var staffToChangePin by remember { mutableStateOf<StaffEntity?>(null) }
    var staffToDelete by remember { mutableStateOf<StaffEntity?>(null) }
    var productToEditPrice by remember { mutableStateOf<ProductEntity?>(null) }
    var previewReportBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    // Live KPI Metrics
    val completedOrders = remember(allOrders) {
        allOrders.filter { it.order.state == OrderState.PAID.name || it.order.state == OrderState.SERVED.name }
    }
    val todayTotalSales = remember(completedOrders) {
        completedOrders.sumOf { it.order.totalMillimes }
    }
    val cancelledOrders = remember(allOrders) {
        allOrders.filter { it.order.state == OrderState.CANCELLED.name }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "لوحة تحكم المدير (Gérant)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        ConnectionStatusBadge(status = connectionStatus, isHost = isHost)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToWizard, modifier = Modifier.testTag("manager_run_wizard_btn")) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "معالج التهيئة")
                    }
                    IconButton(onClick = onNavigateToRoleSelect, modifier = Modifier.testTag("manager_switch_role_btn")) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "تغيير الحساب")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Manager Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("المبيعات", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("النوادل والموظفين (${allStaff.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("الصندوق والوردية", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("الأسعار", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("الرقابة", fontWeight = FontWeight.Bold) }
                )
            }

            when (selectedTab) {
                0 -> {
                    // KPI Dashboard
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            // High-level Sales Hero Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = "مبيعات اليوم الإجمالية (د.ت)",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = Money(todayTotalSales).formatArabic(),
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("عدد الطلبات المكتملة: ${completedOrders.size}")
                                        val avg = if (completedOrders.isNotEmpty()) todayTotalSales / completedOrders.size else 0L
                                        Text("متوسط الطلب: ${Money(avg).formatArabic()}")
                                    }
                                }
                            }
                        }

                        item {
                            // Waiters Cash Ledger Reconciled
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "متابعة مداخيل النوادل (Cash Ledger)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Divider(modifier = Modifier.padding(vertical = 10.dp))

                                    val waitersSummary = completedOrders
                                        .groupBy { it.order.waiterName }
                                        .mapValues { (_, list) -> list.sumOf { it.order.totalMillimes } }

                                    if (waitersSummary.isEmpty()) {
                                        Text("لم يتم تسجيل مبيعات حتى الآن", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        waitersSummary.forEach { (name, total) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(name, fontWeight = FontWeight.SemiBold)
                                                MoneyBadge(Money(total))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            // Backup & CSV Export
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val backup = repository.database.orderDao().getAllOrdersWithLines().first()
                                            val ordersList = backup.map { it.order }
                                            val csv = repository.backupRestoreManager.exportSalesToCsv(ordersList)
                                            snackbarMessage = "تم تصدير ملف المبيعات بنجاح (${ordersList.size} طلب)"
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تصدير CSV")
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            repository.backupRestoreManager.createBackupJson()
                                            snackbarMessage = "تم إنشاء نسخة احتياطية محلية لقاعدة البيانات"
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Backup, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("نسخ احتياطي")
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // Staff & PINs Management (المدير يحدد أسماء النوادل والأدوار ويغير PIN كل حساب)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "إدارة طاقم المقهى والرموز السرية (PIN)",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "المدير هو من يحدد أسماء النوادل وأدوارهم، ويغير رمز PIN لكل حساب.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    Button(
                                        onClick = { showAddStaffDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.testTag("add_staff_button")
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إضافة موظف", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(allStaff, key = { it.id }) { staff ->
                                val role = Role.fromString(staff.role)
                                val (badgeColor, roleIcon) = when (role) {
                                    Role.COUNTER -> (MaterialTheme.colorScheme.primary to Icons.Default.PointOfSale)
                                    Role.WAITER -> (MaterialTheme.colorScheme.secondary to Icons.Default.RestaurantMenu)
                                    Role.MANAGER -> (Color(0xFF6A1B9A) to Icons.Default.AdminPanelSettings)
                                }

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("staff_card_${staff.id}")
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = badgeColor.copy(alpha = 0.15f),
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(roleIcon, contentDescription = null, tint = badgeColor)
                                                    }
                                                }

                                                Column {
                                                    Text(
                                                        text = staff.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 17.sp
                                                    )
                                                    Text(
                                                        text = role.titleAr,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = badgeColor,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }

                                            // Display Current PIN directly to the Manager
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Text(
                                                        text = "PIN: ${staff.pinHash}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                            }
                                        }

                                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Change PIN button
                                            OutlinedButton(
                                                onClick = { staffToChangePin = staff },
                                                modifier = Modifier.testTag("change_pin_btn_${staff.id}")
                                            ) {
                                                Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("تغيير PIN", fontSize = 13.sp)
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Edit Name and Role button
                                            Button(
                                                onClick = { staffToEdit = staff },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                modifier = Modifier.testTag("edit_staff_btn_${staff.id}")
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("تعديل الاسم والدور", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            val isOnlyManager = staff.role == "MANAGER" && allStaff.count { it.role == "MANAGER" } <= 1
                                            IconButton(
                                                onClick = { staffToDelete = staff },
                                                enabled = !isOnlyManager
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "حذف الموظف",
                                                    tint = if (isOnlyManager) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Shift Closing & Reconciliation
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "حالة وردية الصندوق الحالية",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                if (activeShift != null) {
                                    val shift = activeShift!!
                                    Text("المسؤول: ${shift.openedByName}")
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("رصيد البداية: ${Money(shift.startingFloatMillimes).formatArabic()}")
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "المبلغ المتوقع في الصندوق: ${Money(shift.expectedCashMillimes).formatArabic()}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = { showShiftCloseDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.fillMaxWidth().testTag("close_shift_button")
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("غلق الصندوق ومطابقة الحساب (Clôture)")
                                    }
                                } else {
                                    Text("لا توجد وردية مفتوحة حالياً. يمكنك فتح وردية جديدة:")
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                repository.openShift(currentStaff, startingFloatMillimes = 50000L) // 50.000 TND float
                                                snackbarMessage = "تم فتح الوردية برصيد 50.000 د.ت"
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("فتح وردية جديدة (50.000 د.ت كاش أولي)")
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // Menu & Prices Manager
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(allProducts, key = { it.id }) { product ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(product.nameAr, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text(product.nameFr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        MoneyBadge(Money(product.priceMillimes))
                                        IconButton(onClick = { productToEditPrice = product }) {
                                            Icon(Icons.Default.Edit, contentDescription = "تعديل السعر")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                4 -> {
                    // Audit Logs
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(auditLogs, key = { it.id }) { log ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(log.action, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text(log.staffName, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(log.details, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Product Price Dialog
    productToEditPrice?.let { prod ->
        var newPriceText by remember { mutableStateOf((prod.priceMillimes / 1000.0).toString()) }
        AlertDialog(
            onDismissRequest = { productToEditPrice = null },
            title = { Text("تعديل سعر: ${prod.nameAr}") },
            text = {
                Column {
                    Text("السعر الحالي: ${Money(prod.priceMillimes)}")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPriceText,
                        onValueChange = { newPriceText = it },
                        label = { Text("السعر الجديد بالدينار (مثال: 1.800)") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = Money.fromDinarsString(newPriceText)
                        if (parsed.millimes > 0) {
                            scope.launch {
                                repository.database.menuDao().updateProduct(
                                    prod.copy(priceMillimes = parsed.millimes)
                                )
                                repository.logAudit(
                                    action = "UPDATE_PRICE",
                                    details = "تعديل سعر ${prod.nameAr} إلى ${parsed.formatArabic()}",
                                    staff = currentStaff
                                )
                                productToEditPrice = null
                            }
                        }
                    }
                ) {
                    Text("حفظ السعر")
                }
            },
            dismissButton = {
                TextButton(onClick = { productToEditPrice = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Shift Close Dialog (Reconciliation)
    if (showShiftCloseDialog && activeShift != null) {
        val shift = activeShift!!
        var countedCashText by remember { mutableStateOf((shift.expectedCashMillimes / 1000.0).toString()) }
        var managerPin by remember { mutableStateOf("") }
        val countedMoney = Money.fromDinarsString(countedCashText)
        val diffMoney = countedMoney - Money(shift.expectedCashMillimes)

        Dialog(onDismissRequest = { showShiftCloseDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("مطابقة وغلق الصندوق", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("المبلغ المتوقع محاسبياً: ${Money(shift.expectedCashMillimes).formatArabic()}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = countedCashText,
                        onValueChange = { countedCashText = it },
                        label = { Text("المبلغ الفعلي المحسوب في الدرج (د.ت)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = if (diffMoney.millimes == 0L) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (diffMoney.millimes >= 0L) "الفارق (فائض): +${diffMoney.formatArabic()}" else "الفارق (عجز): ${diffMoney.formatArabic()}",
                            fontWeight = FontWeight.Bold,
                            color = if (diffMoney.millimes >= 0L) StatusPaidColor else StatusCancelledColor,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = managerPin,
                        onValueChange = { managerPin = it },
                        label = { Text("رمز المدير (1234) للمصادقة") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (managerPin == "1234") {
                                scope.launch {
                                    val closed = repository.closeShift(
                                        shiftId = shift.id,
                                        actualCashCounted = countedMoney.millimes,
                                        managerPin = managerPin,
                                        staff = currentStaff
                                    )
                                    showShiftCloseDialog = false
                                    if (closed != null) {
                                        val bitmap = ArabicReceiptRenderer.renderShiftReport(closed, repository.cafeName.value)
                                        previewReportBitmap = bitmap
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("تأكيد غلق الوردية وطباعة التقرير", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Add New Staff Dialog
    if (showAddStaffDialog) {
        var nameInput by remember { mutableStateOf("") }
        var selectedRole by remember { mutableStateOf(Role.WAITER) }
        var pinInput by remember { mutableStateOf("") }
        var validationError by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { showAddStaffDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("إضافة موظف جديد", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("اسم الموظف أو النادل (مثال: أنيس)") },
                        modifier = Modifier.fillMaxWidth().testTag("new_staff_name_input")
                    )

                    Text("تحديد الدور والصلاحية:", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedRole == Role.WAITER,
                            onClick = { selectedRole = Role.WAITER },
                            label = { Text("نادل") }
                        )
                        FilterChip(
                            selected = selectedRole == Role.COUNTER,
                            onClick = { selectedRole = Role.COUNTER },
                            label = { Text("محاسب") }
                        )
                        FilterChip(
                            selected = selectedRole == Role.MANAGER,
                            onClick = { selectedRole = Role.MANAGER },
                            label = { Text("مدير") }
                        )
                    }

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4) pinInput = it },
                        label = { Text("رمز PIN السري (4 أرقام)") },
                        modifier = Modifier.fillMaxWidth().testTag("new_staff_pin_input")
                    )

                    validationError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAddStaffDialog = false }) {
                            Text("إلغاء")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (nameInput.isBlank()) {
                                    validationError = "يرجى كتابة اسم الموظف"
                                } else if (pinInput.length != 4) {
                                    validationError = "رمز PIN يجب أن يتكون من 4 أرقام"
                                } else {
                                    scope.launch {
                                        repository.addStaff(
                                            name = nameInput,
                                            role = selectedRole,
                                            pin = pinInput,
                                            manager = currentStaff
                                        )
                                        showAddStaffDialog = false
                                        snackbarMessage = "تمت إضافة الموظف $nameInput بنجاح"
                                    }
                                }
                            },
                            modifier = Modifier.testTag("save_new_staff_btn")
                        ) {
                            Text("حفظ الحساب")
                        }
                    }
                }
            }
        }
    }

    // Edit Staff Name & Role Dialog
    staffToEdit?.let { staff ->
        var editName by remember { mutableStateOf(staff.name) }
        var editRole by remember { mutableStateOf(Role.fromString(staff.role)) }

        AlertDialog(
            onDismissRequest = { staffToEdit = null },
            title = { Text("تعديل حساب: ${staff.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("اسم الموظف") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("الدور:", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = editRole == Role.WAITER,
                            onClick = { editRole = Role.WAITER },
                            label = { Text("نادل") }
                        )
                        FilterChip(
                            selected = editRole == Role.COUNTER,
                            onClick = { editRole = Role.COUNTER },
                            label = { Text("محاسب") }
                        )
                        FilterChip(
                            selected = editRole == Role.MANAGER,
                            onClick = { editRole = Role.MANAGER },
                            label = { Text("مدير") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            scope.launch {
                                repository.updateStaff(
                                    staffId = staff.id,
                                    newName = editName,
                                    newRole = editRole,
                                    newPin = staff.pinHash,
                                    manager = currentStaff
                                )
                                staffToEdit = null
                                snackbarMessage = "تم تحديث بيانات الحساب بنجاح"
                            }
                        }
                    }
                ) {
                    Text("حفظ التعديل")
                }
            },
            dismissButton = {
                TextButton(onClick = { staffToEdit = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Change Staff PIN Dialog
    staffToChangePin?.let { staff ->
        var newPinInput by remember { mutableStateOf("") }
        var confirmPinInput by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { staffToChangePin = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("تغيير رمز PIN: ${staff.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("الرمز السري الحالي: ${staff.pinHash}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 4) newPinInput = it },
                        label = { Text("رمز PIN الجديد (4 أرقام)") },
                        modifier = Modifier.fillMaxWidth().testTag("new_pin_field")
                    )

                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { if (it.length <= 4) confirmPinInput = it },
                        label = { Text("تأكيد رمز PIN الجديد") },
                        modifier = Modifier.fillMaxWidth().testTag("confirm_new_pin_field")
                    )

                    pinError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { staffToChangePin = null }) {
                            Text("إلغاء")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newPinInput.length != 4) {
                                    pinError = "رمز PIN يجب أن يتكون من 4 أرقام"
                                } else if (newPinInput != confirmPinInput) {
                                    pinError = "الرمزان غير متطابقين"
                                } else {
                                    scope.launch {
                                        repository.updateStaff(
                                            staffId = staff.id,
                                            newName = staff.name,
                                            newRole = Role.fromString(staff.role),
                                            newPin = newPinInput,
                                            manager = currentStaff
                                        )
                                        staffToChangePin = null
                                        snackbarMessage = "تم تغيير رمز PIN بنجاح لـ ${staff.name}"
                                    }
                                }
                            },
                            modifier = Modifier.testTag("submit_change_pin_btn")
                        ) {
                            Text("تأكيد وحفظ PIN")
                        }
                    }
                }
            }
        }
    }

    // Delete Staff Confirmation Dialog
    staffToDelete?.let { staff ->
        AlertDialog(
            onDismissRequest = { staffToDelete = null },
            title = { Text("حذف الحساب") },
            text = { Text("هل أنت متأكد من حذف حساب ${staff.name} (${Role.fromString(staff.role).titleAr})؟ لن يتمكن من تسجيل الدخول بعد الآن.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val success = repository.deleteStaff(staff.id, currentStaff)
                            staffToDelete = null
                            if (success) {
                                snackbarMessage = "تم حذف الحساب بنجاح"
                            } else {
                                snackbarMessage = "تعذر حذف المدير الوحيد في النظام"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("تأكيد الحذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { staffToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Receipt Preview Dialog
    ReceiptPreviewDialog(
        bitmap = previewReportBitmap,
        onDismiss = { previewReportBitmap = null }
    )

    // Snackbar alert
    snackbarMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { snackbarMessage = null },
            title = { Text("إشعار") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { snackbarMessage = null }) {
                    Text("حسناً")
                }
            }
        )
    }
}
