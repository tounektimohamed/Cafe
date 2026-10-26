package com.example.ui.screens.kitchen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.data.entity.StaffEntity
import com.example.core.model.OrderState
import com.example.core.repository.PosRepository
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenScreen(
    repository: PosRepository,
    currentStaff: StaffEntity,
    onNavigateToRoleSelect: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val allOrders by repository.getAllOrders().collectAsState(initial = emptyList())
    val connectionStatus by repository.networkManager.connectionStatus.collectAsState()
    val isHost by repository.networkManager.isHost.collectAsState()

    var filterState by remember { mutableStateOf("ALL") } // ALL, PAID, READY

    // Filter kitchen orders (NEW, AWAITING_PAYMENT, PAID, READY)
    val kitchenOrders = remember(allOrders, filterState) {
        allOrders.filter { item ->
            val state = OrderState.fromString(item.order.state)
            val isKitchenRelevant = state in listOf(
                OrderState.NEW,
                OrderState.AWAITING_PAYMENT,
                OrderState.PAID,
                OrderState.READY
            )
            if (!isKitchenRelevant) return@filter false

            when (filterState) {
                "PAID" -> state == OrderState.PAID || state == OrderState.AWAITING_PAYMENT || state == OrderState.NEW
                "READY" -> state == OrderState.READY
                else -> true
            }
        }.sortedByDescending { it.order.createdAt }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.SoupKitchen, contentDescription = null, tint = Color(0xFFE65100))
                        Text(
                            text = "شاشة المطبخ والبار (KDS)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        ConnectionStatusBadge(status = connectionStatus, isHost = isHost)
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToRoleSelect,
                        modifier = Modifier.testTag("kitchen_switch_role_btn")
                    ) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Filter Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = filterState == "ALL",
                    onClick = { filterState = "ALL" },
                    label = { Text("جميع الطلبات (${kitchenOrders.size})") }
                )
                FilterChip(
                    selected = filterState == "PAID",
                    onClick = { filterState = "PAID" },
                    label = { Text("في التحضير / مدفوع") }
                )
                FilterChip(
                    selected = filterState == "READY",
                    onClick = { filterState = "READY" },
                    label = { Text("جاهزة للتقديم") }
                )
            }

            if (kitchenOrders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لا توجد طلبات تحضير حالياً في المطبخ",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(kitchenOrders, key = { it.order.id }) { item ->
                        val orderState = OrderState.fromString(item.order.state)
                        val orderTimeStr = timeFormat.format(Date(item.order.createdAt))

                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when (orderState) {
                                    OrderState.READY -> StatusReadyColor.copy(alpha = 0.15f)
                                    OrderState.PAID -> StatusPaidColor.copy(alpha = 0.12f)
                                    OrderState.AWAITING_PAYMENT -> StatusAwaitingColor.copy(alpha = 0.12f)
                                    else -> StatusNewColor.copy(alpha = 0.12f)
                                }
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Ticket Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = item.order.tableName.ifBlank { "طلب سفري" },
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "النادل: ${item.order.waiterName} • $orderTimeStr",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Status Badge
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when (orderState) {
                                            OrderState.READY -> StatusReadyColor
                                            OrderState.PAID -> StatusPaidColor
                                            OrderState.AWAITING_PAYMENT -> StatusAwaitingColor
                                            else -> StatusNewColor
                                        }
                                    ) {
                                        Text(
                                            text = orderState.labelAr,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                HorizontalDivider()

                                // Items List
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    item.lines.forEach { line ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${line.quantity}x",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.width(36.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = line.productNameAr,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                                if (line.notes.isNotBlank()) {
                                                    Text(
                                                        text = "ملاحظات / إضافات: ${line.notes}",
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider()

                                // Action Buttons
                                if (orderState != OrderState.READY) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                repository.transitionOrder(
                                                    orderId = item.order.id,
                                                    targetState = OrderState.READY,
                                                    actingStaff = currentStaff
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusReadyColor),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("جاهز للتقديم (تنبيه النادل)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
