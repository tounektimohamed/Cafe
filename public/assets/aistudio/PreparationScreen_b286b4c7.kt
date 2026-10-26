package com.example.ui.screens.prep

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.data.dao.OrderWithLines
import com.example.core.data.entity.StaffEntity
import com.example.core.model.OrderState
import com.example.core.model.TransitionResult
import com.example.core.repository.PosRepository
import com.example.ui.theme.StatusAwaitingColor
import com.example.ui.theme.StatusCancelledColor
import com.example.ui.theme.StatusPaidColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * شاشة تحضير الطلبات: تعرض طلباً واحداً في كل مرة (الأقدم أولاً).
 * عند الضغط على «تم» يصبح الطلب READY ويظهر الطلب التالي تلقائياً.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparationScreen(
    repository: PosRepository,
    currentStaff: StaffEntity,
    onNavigateToRoleSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    // Keep the screen awake while preparing orders
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val ordersFlow = remember { repository.getActiveOrders() }
    val active by ordersFlow.collectAsState(initial = emptyList())

    // Live clock for the waiting timers
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    // Orders that are ready to be prepared:
    //  - payment-first mode  → PAID
    //  - prepare-first mode  → NEW
    val postponed = remember { mutableStateListOf<String>() }
    val queue: List<OrderWithLines> = remember(active, postponed.toList()) {
        val base = active
            .filter {
                val s = OrderState.fromString(it.order.state)
                if (it.order.paymentFirst) s == OrderState.PAID else s == OrderState.NEW
            }
            .sortedBy { it.order.updatedAt }
        val normal = base.filter { it.order.id !in postponed }
        val later = postponed.mapNotNull { id -> base.firstOrNull { it.order.id == id } }
        normal + later
    }
    val current = queue.firstOrNull()
    val upNext = queue.drop(1)

    // Alert when a new order arrives
    var lastCount by remember { mutableStateOf(-1) }
    LaunchedEffect(queue.size) {
        if (lastCount >= 0 && queue.size > lastCount) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            try {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                    .startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            } catch (_: Exception) { /* ignore devices without audio */ }
        }
        lastCount = queue.size
    }

    var busy by remember { mutableStateOf(false) }

    fun markDone(order: OrderWithLines) {
        if (busy) return
        busy = true
        scope.launch {
            val result = repository.transitionOrder(
                orderId = order.order.id,
                targetState = OrderState.READY,
                actingStaff = currentStaff
            )
            if (result is TransitionResult.Error) {
                snackbar.showSnackbar(result.messageAr)
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            delay(600) // anti double-tap: prevents skipping the next order by mistake
            busy = false
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("تحضير الطلبات", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text(
                            if (queue.isEmpty()) "لا توجد طلبات في الانتظار"
                            else "في الانتظار: ${queue.size}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToRoleSelect) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "تغيير الحساب")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val wide = maxWidth >= 840.dp

            if (current == null) {
                EmptyState(Modifier.fillMaxSize())
            } else if (wide) {
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CurrentTicket(
                        current, now, busy, queue.size > 1,
                        onDone = { markDone(current) },
                        onPostpone = { postponed.remove(current.order.id); postponed.add(current.order.id) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    UpNextColumn(upNext, now, Modifier.width(260.dp).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (upNext.isNotEmpty()) UpNextRow(upNext, now)
                    CurrentTicket(
                        current, now, busy, queue.size > 1,
                        onDone = { markDone(current) },
                        onPostpone = { postponed.remove(current.order.id); postponed.add(current.order.id) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ───────────────────────── Current order ─────────────────────────

@Composable
private fun CurrentTicket(
    order: OrderWithLines,
    now: Long,
    busy: Boolean,
    canPostpone: Boolean,
    onDone: () -> Unit,
    onPostpone: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = order,
        contentKey = { it.order.id },
        transitionSpec = {
            (slideInHorizontally { it / 2 } + fadeIn()) togetherWith
                (slideOutHorizontally { -it / 2 } + fadeOut())
        },
        modifier = modifier,
        label = "ticket"
    ) { o ->
        val waitMs = (now - o.order.updatedAt).coerceAtLeast(0L)
        val urgency = urgencyColor(waitMs)

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Header: number • table/takeaway • timer
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(urgency).padding(horizontal = 18.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("#${o.order.orderNumber}", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (o.order.isTakeaway) Icons.Filled.TakeoutDining else Icons.Filled.TableRestaurant,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (o.order.isTakeaway) "سفري" else o.order.tableName,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (o.order.waiterName.isNotBlank()) {
                            Text("النادل: ${o.order.waiterName}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Icon(Icons.Filled.Timer, null, tint = urgency)
                        Text(formatElapsed(waitMs), color = urgency, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // Post-order changes must never be missed
                if (o.deltas.isNotEmpty()) {
                    Surface(
                        color = StatusAwaitingColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null, tint = StatusAwaitingColor)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "تعديل على الطلب: " + o.deltas.joinToString("، ") { it.description },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Items
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(o.lines, key = { it.id }) { line ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(
                                Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "×${line.quantity}",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(line.productNameAr, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                if (line.modifiersJson.isNotBlank()) {
                                    Text(
                                        line.modifiersJson,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (line.notes.isNotBlank()) {
                                    Text("📝 ${line.notes}", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Actions
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (canPostpone) {
                        OutlinedButton(
                            onClick = onPostpone,
                            enabled = !busy,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f).height(76.dp)
                        ) {
                            Icon(Icons.Filled.SkipNext, null)
                            Spacer(Modifier.width(6.dp))
                            Text("تأجيل", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = onDone,
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusPaidColor),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(2.4f).height(76.dp)
                    ) {
                        Icon(Icons.Filled.Check, null, Modifier.size(34.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("تم", fontSize = 30.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

// ───────────────────────── Up next ─────────────────────────

@Composable
private fun UpNextColumn(orders: List<OrderWithLines>, now: Long, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("التالي", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (orders.isEmpty()) {
            Text("لا يوجد طلبات أخرى", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(orders, key = { it.order.id }) { UpNextCard(it, now, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun UpNextRow(orders: List<OrderWithLines>, now: Long) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) {
                Text("التالي ›", fontWeight = FontWeight.Bold)
            }
        }
        items(orders, key = { it.order.id }) { UpNextCard(it, now, Modifier.width(140.dp)) }
    }
}

@Composable
private fun UpNextCard(o: OrderWithLines, now: Long, modifier: Modifier = Modifier) {
    val wait = (now - o.order.updatedAt).coerceAtLeast(0L)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${o.order.orderNumber}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text(formatElapsed(wait), color = urgencyColor(wait), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text(
                "${o.lines.sumOf { it.quantity }} أصناف • " + if (o.order.isTakeaway) "سفري" else o.order.tableName,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ───────────────────────── Helpers ─────────────────────────

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Coffee, null, Modifier.size(88.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(12.dp))
        Text("كل الطلبات جاهزة 👌", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("ستظهر الطلبات الجديدة هنا تلقائياً", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun urgencyColor(waitMs: Long): Color = when {
    waitMs < 3 * 60_000L -> StatusPaidColor
    waitMs < 6 * 60_000L -> StatusAwaitingColor
    else -> StatusCancelledColor
}

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}
