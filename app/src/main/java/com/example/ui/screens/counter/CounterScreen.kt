package com.example.ui.screens.counter

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.core.data.dao.OrderWithLines
import com.example.core.data.entity.StaffEntity
import com.example.core.model.Money
import com.example.core.model.OrderState
import com.example.core.model.OrderStateMachine
import com.example.core.model.Role
import com.example.core.model.TransitionResult
import com.example.core.printer.ArabicReceiptRenderer
import com.example.core.repository.PosRepository
import com.example.ui.components.CancellationDialog
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.components.MoneyBadge
import com.example.ui.components.ReceiptPreviewDialog
import com.example.ui.screens.pos.PosCartState
import com.example.ui.screens.pos.PosOrderScreen
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterScreen(
    repository: PosRepository,
    currentStaff: StaffEntity,
    onNavigateToRoleSelect: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val orders by repository.getActiveOrders().collectAsState(initial = emptyList())
    val connectionStatus by repository.networkManager.connectionStatus.collectAsState()
    val isHost by repository.networkManager.isHost.collectAsState()

    val categories by repository.getAllCategories().collectAsState(initial = emptyList())
    val products by repository.getAvailableProducts().collectAsState(initial = emptyList())
    val modifierOptions by repository.getAllModifiers().collectAsState(initial = emptyList())

    var selectedFilter by remember { mutableStateOf<OrderState?>(null) }
    var selectedOrderForPayment by remember { mutableStateOf<OrderWithLines?>(null) }
    var orderToCancel by remember { mutableStateOf<OrderWithLines?>(null) }
    var previewReceiptBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showNewCounterOrderDialog by remember { mutableStateOf(false) }
    val counterPosCart = remember { PosCartState() }

    val stateMachine = remember { OrderStateMachine() }

    // Check for stuck orders (> 5 minutes in AWAITING_PAYMENT)
    val stuckOrders = remember(orders) {
        val now = System.currentTimeMillis()
        orders.filter {
            stateMachine.isStuckAwaitingPayment(
                currentState = OrderState.fromString(it.order.state),
                updatedAtMillis = it.order.updatedAt,
                currentTimeMillis = now
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "شاشة الكاونتر والمحاسبة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        ConnectionStatusBadge(status = connectionStatus, isHost = isHost)
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToRoleSelect,
                        modifier = Modifier.testTag("switch_role_button")
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "تغيير الحساب")
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "الإعدادات")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewCounterOrderDialog = true },
                icon = { Icon(Icons.Default.AddShoppingCart, contentDescription = null) },
                text = { Text("طلب كاونتر مباشر", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("counter_direct_order_fab")
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Stuck Orders Alert Banner
            if (stuckOrders.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "تنبيه",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "تنبيه: يوجد ${stuckOrders.size} طلب معلق في انتظار الدفع لأكثر من 5 دقائق!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Order Status Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("الكل (${orders.size})") }
                    )
                }
                items(listOf(OrderState.NEW, OrderState.AWAITING_PAYMENT, OrderState.PAID, OrderState.READY)) { state ->
                    val count = orders.count { it.order.state == state.name }
                    FilterChip(
                        selected = selectedFilter == state,
                        onClick = { selectedFilter = state },
                        label = { Text("${state.labelAr} ($count)") }
                    )
                }
            }

            val filteredOrders = remember(orders, selectedFilter) {
                if (selectedFilter == null) orders
                else orders.filter { it.order.state == selectedFilter!!.name }
            }

            if (filteredOrders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Coffee,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "لا توجد طلبات نشطة حالياً",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "طلبات النادل ستظهر هنا لحظياً عبر الشبكة المحلية",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredOrders, key = { it.order.id }) { orderWithLines ->
                        val order = orderWithLines.order
                        val currentState = OrderState.fromString(order.state)

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("order_card_${order.orderNumber}")
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Header: Table + Order # + State
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = if (order.isTakeaway) "سفري" else order.tableName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                        Text(
                                            text = "طلب #${order.orderNumber}",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "• النادل: ${order.waiterName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Status Badge
                                    val (badgeBg, badgeFg) = when (currentState) {
                                        OrderState.NEW -> (StatusNewColor.copy(alpha = 0.15f) to StatusNewColor)
                                        OrderState.AWAITING_PAYMENT -> (StatusAwaitingColor.copy(alpha = 0.15f) to StatusAwaitingColor)
                                        OrderState.PAID -> (StatusPaidColor.copy(alpha = 0.15f) to StatusPaidColor)
                                        OrderState.READY -> (StatusReadyColor.copy(alpha = 0.15f) to StatusReadyColor)
                                        OrderState.SERVED -> (StatusServedColor.copy(alpha = 0.15f) to StatusServedColor)
                                        OrderState.CANCELLED -> (StatusCancelledColor.copy(alpha = 0.15f) to StatusCancelledColor)
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = badgeBg
                                    ) {
                                        Text(
                                            text = currentState.labelAr,
                                            color = badgeFg,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Divider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )

                                // Items summary
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    orderWithLines.lines.forEach { line ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = "${line.quantity}x",
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(text = line.productNameAr)
                                                if (line.notes.isNotBlank()) {
                                                    Text(
                                                        text = "(${line.notes})",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Text(
                                                text = Money(line.unitPriceMillimes * line.quantity).formatArabic(),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Total and Actions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "المجموع المطلوب:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = Money(order.totalMillimes).formatArabic(),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Action buttons based on workflow state
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Cancel button
                                        IconButton(
                                            onClick = { orderToCancel = orderWithLines },
                                            modifier = Modifier.testTag("cancel_order_btn_${order.orderNumber}")
                                        ) {
                                            Icon(
                                                Icons.Default.Cancel,
                                                contentDescription = "إلغاء الطلب",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }

                                        when (currentState) {
                                            OrderState.NEW -> {
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            repository.transitionOrder(
                                                                orderId = order.id,
                                                                targetState = OrderState.AWAITING_PAYMENT,
                                                                actingStaff = currentStaff
                                                            )
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = StatusAwaitingColor),
                                                    modifier = Modifier.testTag("request_payment_btn_${order.orderNumber}")
                                                ) {
                                                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("طلب الدفع")
                                                }
                                            }
                                            OrderState.AWAITING_PAYMENT -> {
                                                Button(
                                                    onClick = { selectedOrderForPayment = orderWithLines },
                                                    colors = ButtonDefaults.buttonColors(containerColor = StatusPaidColor),
                                                    modifier = Modifier.testTag("confirm_payment_btn_${order.orderNumber}")
                                                ) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("استلام الخلاص")
                                                }
                                            }
                                            OrderState.PAID -> {
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            repository.transitionOrder(
                                                                orderId = order.id,
                                                                targetState = OrderState.READY,
                                                                actingStaff = currentStaff
                                                            )
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = StatusReadyColor),
                                                    modifier = Modifier.testTag("mark_ready_btn_${order.orderNumber}")
                                                ) {
                                                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("جاهز في البار")
                                                }
                                            }
                                            OrderState.READY -> {
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            repository.transitionOrder(
                                                                orderId = order.id,
                                                                targetState = OrderState.SERVED,
                                                                actingStaff = currentStaff
                                                            )
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = StatusServedColor),
                                                    modifier = Modifier.testTag("mark_served_btn_${order.orderNumber}")
                                                ) {
                                                    Text("تم التقديم للزبون")
                                                }
                                            }
                                            else -> {}
                                        }

                                        // Receipt print / preview
                                        IconButton(
                                            onClick = {
                                                val bitmap = ArabicReceiptRenderer.renderCustomerReceipt(
                                                    cafeName = repository.cafeName.value,
                                                    orderWithLines = orderWithLines
                                                )
                                                scope.launch {
                                                    repository.printerManager.printBitmap(bitmap)
                                                    previewReceiptBitmap = bitmap
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Print, contentDescription = "طباعة الوصل")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Cash Payment Confirmation Modal (Change calculator)
    selectedOrderForPayment?.let { orderWithLines ->
        val order = orderWithLines.order
        var receivedText by remember { mutableStateOf(Money(order.totalMillimes).formatArabic().replace(" د.ت", "").trim()) }
        val receivedMoney = Money.fromDinarsString(receivedText)
        val changeMoney = (receivedMoney - Money(order.totalMillimes)).coerceAtLeast(Money.ZERO)

        Dialog(onDismissRequest = { selectedOrderForPayment = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "خلاص طلب #${order.orderNumber} - ${order.tableName}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("المبلغ المطلوب خلاصُه:")
                            Text(
                                text = Money(order.totalMillimes).formatArabic(),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quick Cash Bills in Tunisian Dinars
                    Text("المبلغ المستلم نقداً من النادل/الزبون:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(5, 10, 20, 50).forEach { dinars ->
                            Button(
                                onClick = { receivedText = dinars.toString() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("$dinars د.ت", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = { receivedText = (order.totalMillimes / 1000.0).toString() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("المضبوط", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = receivedText,
                        onValueChange = { receivedText = it },
                        label = { Text("المبلغ المستلم بالدينار (مثال: 5 أو 10.000)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Change calculated
                    Surface(
                        color = if (changeMoney > Money.ZERO) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("الصرف المتبقي للزبون (Change):", fontWeight = FontWeight.Bold)
                            Text(
                                text = changeMoney.formatArabic(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusPaidColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                val result = repository.transitionOrder(
                                    orderId = order.id,
                                    targetState = OrderState.PAID,
                                    receivedMillimes = receivedMoney.millimes,
                                    actingStaff = currentStaff
                                )
                                if (result is TransitionResult.Success) {
                                    selectedOrderForPayment = null
                                } else if (result is TransitionResult.Error) {
                                    errorMessage = result.messageAr
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusPaidColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("confirm_cash_received_button")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تأكيد استلام المبلغ وطباعة تذكرة التحضير", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Cancellation Dialog
    orderToCancel?.let { orderWithLines ->
        val isPaid = orderWithLines.order.state == OrderState.PAID.name || orderWithLines.order.state == OrderState.READY.name
        CancellationDialog(
            orderNumber = orderWithLines.order.orderNumber,
            requiresManagerPin = isPaid,
            onDismiss = { orderToCancel = null },
            onConfirm = { reason, pin ->
                scope.launch {
                    val isApproved = if (isPaid) pin == "1234" else false
                    val result = repository.transitionOrder(
                        orderId = orderWithLines.order.id,
                        targetState = OrderState.CANCELLED,
                        cancellationReason = reason,
                        managerApproved = isApproved,
                        actingStaff = currentStaff
                    )
                    if (result is TransitionResult.Success) {
                        orderToCancel = null
                    } else if (result is TransitionResult.Error) {
                        errorMessage = result.messageAr
                    }
                }
            }
        )
    }

    // Receipt Preview Dialog
    ReceiptPreviewDialog(
        bitmap = previewReceiptBitmap,
        onDismiss = { previewReceiptBitmap = null }
    )

    // Error Snackbar
    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("تنبيه") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("حسناً")
                }
            }
        )
    }

    // Direct Counter Order Modal using PosOrderScreen
    if (showNewCounterOrderDialog) {
        Dialog(
            onDismissRequest = {
                showNewCounterOrderDialog = false
                counterPosCart.clear()
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "تسجيل طلب مباشر في الكاونتر",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = {
                                showNewCounterOrderDialog = false
                                counterPosCart.clear()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق")
                            }
                        }
                    }

                    PosOrderScreen(
                        title = "قائمة المشروبات والمأكولات",
                        categories = categories,
                        products = products,
                        modifierOptions = modifierOptions,
                        cart = counterPosCart,
                        onSendToKitchen = {
                            if (counterPosCart.lines.isNotEmpty()) {
                                scope.launch {
                                    repository.createOrderFromPosCart(
                                        tableId = null,
                                        tableName = "كاونتر مباشر",
                                        isTakeaway = true,
                                        cart = counterPosCart,
                                        waiter = currentStaff
                                    )
                                    counterPosCart.clear()
                                    showNewCounterOrderDialog = false
                                }
                            }
                        },
                        onPay = {
                            if (counterPosCart.lines.isNotEmpty()) {
                                scope.launch {
                                    val orderId = repository.createOrderFromPosCart(
                                        tableId = null,
                                        tableName = "كاونتر مباشر",
                                        isTakeaway = true,
                                        cart = counterPosCart,
                                        waiter = currentStaff
                                    )
                                    // Transition directly to PAID and print ticket automatically
                                    repository.transitionOrder(
                                        orderId = orderId,
                                        targetState = OrderState.PAID,
                                        receivedMillimes = counterPosCart.total.millimes,
                                        actingStaff = currentStaff
                                    )
                                    counterPosCart.clear()
                                    showNewCounterOrderDialog = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
