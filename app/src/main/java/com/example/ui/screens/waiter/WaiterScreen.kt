package com.example.ui.screens.waiter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.core.data.entity.CategoryEntity
import com.example.core.data.entity.DiningTableEntity
import com.example.core.data.entity.ProductEntity
import com.example.core.data.entity.StaffEntity
import com.example.core.model.Money
import com.example.core.model.OrderState
import com.example.core.repository.PosRepository
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.components.MoneyBadge
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Resolves local high-resolution appetizing photos for café items
 */
fun getProductDrawableRes(productId: String, categoryId: String): Int {
    val idLower = productId.lowercase()
    return when {
        idLower.contains("express") || idLower.contains("direct") -> R.drawable.img_coffee_espresso
        idLower.contains("capucin") || idLower.contains("creme") || idLower.contains("turc") -> R.drawable.img_coffee_capucin
        idLower.contains("tea") -> R.drawable.img_tea_mint
        idLower.contains("citronnade") || idLower.contains("orange") || idLower.contains("cocktail") || idLower.contains("soda") || idLower.contains("water") -> R.drawable.img_fresh_citronnade
        idLower.contains("croissant") || idLower.contains("millefeuille") || idLower.contains("cake") || idLower.contains("crepe") -> R.drawable.img_pastry_croissant
        categoryId == "cat_coffee" -> R.drawable.img_coffee_espresso
        categoryId == "cat_drinks" -> R.drawable.img_fresh_citronnade
        categoryId == "cat_pastry" -> R.drawable.img_pastry_croissant
        else -> R.drawable.img_coffee_capucin
    }
}

enum class ProductLayoutMode(val labelAr: String) {
    LARGE_TOUCH("أزرار كبيرة"),
    COMPACT_GRID("شبكة مكثفة"),
    LIST("قائمة سريعة")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaiterScreen(
    repository: PosRepository,
    currentStaff: StaffEntity,
    onNavigateToRoleSelect: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val tables by repository.getAllTables().collectAsState(initial = emptyList())
    val categories by repository.getAllCategories().collectAsState(initial = emptyList())
    val products by repository.getAvailableProducts().collectAsState(initial = emptyList())
    val orders by repository.getActiveOrders().collectAsState(initial = emptyList())
    val connectionStatus by repository.networkManager.connectionStatus.collectAsState()
    val isHost by repository.networkManager.isHost.collectAsState()
    val tablesModeEnabled by repository.enableTablesMode.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Main (Tables or Direct POS), 1 = Orders
    var showNewOrderDialog by remember { mutableStateOf(false) }
    var selectedTableForNewOrder by remember { mutableStateOf<DiningTableEntity?>(null) }

    // Direct mode cart state (when tables mode is disabled)
    val directCart = remember { mutableStateMapOf<String, Int>() }
    val directNotes = remember { mutableStateMapOf<String, String>() }
    var directCustomerNote by remember { mutableStateOf("") }
    var orderSuccessMessage by remember { mutableStateOf<String?>(null) }

    // Ready alert for this waiter
    val readyOrdersForWaiter = remember(orders, currentStaff) {
        orders.filter {
            it.order.state == OrderState.READY.name &&
            (it.order.waiterId == currentStaff.id || it.order.waiterName == currentStaff.name)
        }
    }

    // Awaiting payment orders for this waiter
    val awaitingPaymentForWaiter = remember(orders, currentStaff) {
        orders.filter {
            it.order.state == OrderState.AWAITING_PAYMENT.name &&
            (it.order.waiterId == currentStaff.id || it.order.waiterName == currentStaff.name)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "النادل: ${currentStaff.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        ConnectionStatusBadge(status = connectionStatus, isHost = isHost)
                    }
                },
                actions = {
                    // Quick toggle between Tables Mode and Direct/No-tables Mode
                    IconButton(
                        onClick = {
                            repository.enableTablesMode.value = !tablesModeEnabled
                        },
                        modifier = Modifier.testTag("toggle_tables_mode_btn")
                    ) {
                        Icon(
                            imageVector = if (tablesModeEnabled) Icons.Default.TableRestaurant else Icons.Default.FlashOn,
                            contentDescription = if (tablesModeEnabled) "وضع الطاولات مفعل" else "وضع الطلب المباشر دون طاولات",
                            tint = if (tablesModeEnabled) MaterialTheme.colorScheme.primary else StatusAwaitingColor
                        )
                    }

                    IconButton(
                        onClick = onNavigateToRoleSelect,
                        modifier = Modifier.testTag("waiter_switch_account_btn")
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "تغيير الحساب")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (tablesModeEnabled) {
                ExtendedFloatingActionButton(
                    onClick = {
                        selectedTableForNewOrder = null
                        showNewOrderDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("طلب جديد", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("waiter_new_order_fab")
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Live Ready Alert Banner
            if (readyOrdersForWaiter.isNotEmpty()) {
                Surface(
                    color = StatusReadyColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "طلبات جاهزة في البار للتقديم!",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = readyOrdersForWaiter.joinToString("، ") { "${it.order.tableName} (#${it.order.orderNumber})" },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // Payment Requested Banner
            if (awaitingPaymentForWaiter.isNotEmpty()) {
                Surface(
                    color = StatusAwaitingColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AttachMoney, contentDescription = null, tint = Color.White)
                        Text(
                            text = "مطلوب تحصيل الحساب من: ${awaitingPaymentForWaiter.joinToString("، ") { it.order.tableName }}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Tabs Header
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = if (tablesModeEnabled) "خريطة الطاولات (${tables.size})" else "تسجيل طلب مباشر سريع",
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "طلباتي النشطة (${orders.count { it.order.waiterId == currentStaff.id }})",
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }

            if (selectedTab == 0) {
                if (tablesModeEnabled) {
                    // MODE 1: Table Management Map
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 130.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(tables, key = { it.id }) { table ->
                            val isOccupied = table.status == "OCCUPIED"
                            val (cardColor, borderCol, statusText) = when (table.status) {
                                "OCCUPIED" -> Triple(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), MaterialTheme.colorScheme.error, "مشغولة")
                                "BILL_REQUESTED" -> Triple(StatusAwaitingColor.copy(alpha = 0.2f), StatusAwaitingColor, "طلب حساب")
                                else -> Triple(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant, "شاغرة")
                            }

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardColor),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, borderCol),
                                modifier = Modifier
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        selectedTableForNewOrder = table
                                        showNewOrderDialog = true
                                    }
                                    .testTag("table_card_${table.tableNumber}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = table.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (isOccupied) StatusAwaitingColor else StatusPaidColor)
                                        )
                                    }

                                    if (isOccupied && table.activeWaiterName != null) {
                                        Text(
                                            text = "النادل: ${table.activeWaiterName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isOccupied) StatusAwaitingColor else StatusPaidColor
                                        )
                                    }

                                    Text(
                                        text = "+ طلب جديد",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // MODE 2: Direct Fast POS (Without Tables Mode)
                    DirectPosOrderView(
                        categories = categories,
                        products = products,
                        cart = directCart,
                        notesMap = directNotes,
                        customerNote = directCustomerNote,
                        onCustomerNoteChange = { directCustomerNote = it },
                        onSubmitOrder = {
                            if (directCart.isNotEmpty()) {
                                scope.launch {
                                    val cartItems = directCart.mapNotNull { (prodId, qty) ->
                                        val prod = products.find { it.id == prodId }
                                        prod?.let { it to qty }
                                    }
                                    val orderTitle = if (directCustomerNote.isNotBlank()) directCustomerNote else "سفري مباشر"
                                    repository.createOrder(
                                        tableId = null,
                                        tableName = orderTitle,
                                        isTakeaway = true,
                                        items = cartItems,
                                        modifiersNotes = directNotes.toMap(),
                                        waiter = currentStaff
                                    )
                                    directCart.clear()
                                    directNotes.clear()
                                    directCustomerNote = ""
                                    orderSuccessMessage = "تم إرسال الطلب للكاونتر بنجاح!"
                                }
                            }
                        }
                    )
                }
            } else {
                // Active Orders Tab
                val myOrders = orders.filter { it.order.waiterId == currentStaff.id || it.order.waiterName == currentStaff.name }
                if (myOrders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("ليس لديك طلبات نشطة حالياً", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(myOrders, key = { it.order.id }) { orderWithLines ->
                            val order = orderWithLines.order
                            val state = OrderState.fromString(order.state)

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${order.tableName} • طلب #${order.orderNumber}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = when (state) {
                                                OrderState.NEW -> StatusNewColor.copy(alpha = 0.2f)
                                                OrderState.AWAITING_PAYMENT -> StatusAwaitingColor.copy(alpha = 0.2f)
                                                OrderState.PAID -> StatusPaidColor.copy(alpha = 0.2f)
                                                OrderState.READY -> StatusReadyColor.copy(alpha = 0.2f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ) {
                                            Text(
                                                text = state.labelAr,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    orderWithLines.lines.forEach { line ->
                                        Text(
                                            text = "${line.quantity}x ${line.productNameAr} ${if (line.notes.isNotBlank()) "(${line.notes})" else ""}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = Money(order.totalMillimes).formatArabic(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        if (state == OrderState.READY) {
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
                                                colors = ButtonDefaults.buttonColors(containerColor = StatusServedColor)
                                            ) {
                                                Text("تقديم للزبون وإغلاق")
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
    }

    // Modal for New Order in Table Mode
    if (showNewOrderDialog) {
        NewOrderDialog(
            initialTable = selectedTableForNewOrder,
            availableTables = tables,
            categories = categories,
            products = products,
            onDismiss = { showNewOrderDialog = false },
            onSubmitOrder = { table, isTakeaway, cartItems, notesMap ->
                scope.launch {
                    repository.createOrder(
                        tableId = table?.id,
                        tableName = if (isTakeaway) "سفري" else (table?.name ?: "سفري"),
                        isTakeaway = isTakeaway,
                        items = cartItems,
                        modifiersNotes = notesMap,
                        waiter = currentStaff
                    )
                    showNewOrderDialog = false
                    orderSuccessMessage = "تم إرسال الطلب للكاونتر بنجاح!"
                }
            }
        )
    }

    orderSuccessMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { orderSuccessMessage = null },
            title = { Text("تم بنجاح") },
            text = { Text(msg) },
            confirmButton = {
                Button(onClick = { orderSuccessMessage = null }) {
                    Text("حسناً")
                }
            }
        )
    }
}

/**
 * Direct POS Ordering Screen (Used in No-Tables mode).
 * Features dynamic product layouts, favorites, instant search, and instant cart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectPosOrderView(
    categories: List<CategoryEntity>,
    products: List<ProductEntity>,
    cart: MutableMap<String, Int>,
    notesMap: MutableMap<String, String>,
    customerNote: String,
    onCustomerNoteChange: (String) -> Unit,
    onSubmitOrder: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var layoutMode by remember { mutableStateOf(ProductLayoutMode.LARGE_TOUCH) }

    val totalMillimes = remember(cart.toMap(), products) {
        cart.entries.sumOf { (prodId, qty) ->
            val product = products.find { it.id == prodId }
            (product?.priceMillimes ?: 0L) * qty
        }
    }

    // Filtered products by category and search query
    val filteredProducts = remember(selectedCategoryId, searchQuery, products) {
        products.filter { prod ->
            val matchesCategory = selectedCategoryId.isBlank() || prod.categoryId == selectedCategoryId
            val matchesSearch = searchQuery.isBlank() ||
                    prod.nameAr.contains(searchQuery, ignoreCase = true) ||
                    prod.nameFr.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    // Pinned favorites (Top 5 fast-sellers)
    val favoriteProducts = remember(products) {
        products.filter { it.isFavorite }.take(6)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Controls Toolbar: Search + Layout Mode Switcher
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("🔍 بحث سريع في المشروبات...", fontSize = 13.sp) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        singleLine = true
                    )

                    // Dynamic Layout Density Switcher
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            IconButton(
                                onClick = { layoutMode = ProductLayoutMode.LARGE_TOUCH },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Apps,
                                    contentDescription = "أزرار كبيرة",
                                    tint = if (layoutMode == ProductLayoutMode.LARGE_TOUCH) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                            IconButton(
                                onClick = { layoutMode = ProductLayoutMode.COMPACT_GRID },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.GridView,
                                    contentDescription = "شبكة مكثفة",
                                    tint = if (layoutMode == ProductLayoutMode.COMPACT_GRID) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                            IconButton(
                                onClick = { layoutMode = ProductLayoutMode.LIST },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ViewList,
                                    contentDescription = "قائمة سريعة",
                                    tint = if (layoutMode == ProductLayoutMode.LIST) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Fast Favorites Row with photos (المفضلة السريعة بالصور)
                if (favoriteProducts.isNotEmpty() && searchQuery.isBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("⭐ الأكثر طلباً:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(favoriteProducts) { fav ->
                                SuggestionChip(
                                    onClick = {
                                        val current = cart[fav.id] ?: 0
                                        cart[fav.id] = current + 1
                                    },
                                    icon = {
                                        Image(
                                            painter = painterResource(id = getProductDrawableRes(fav.id, fav.categoryId)),
                                            contentDescription = fav.nameAr,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    },
                                    label = { Text("${fav.nameAr.split(" ")[0]} +", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        }
                    }
                }

                // Category Tabs
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId.isBlank(),
                            onClick = { selectedCategoryId = "" },
                            label = { Text("الكل") }
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.nameAr) }
                        )
                    }
                }
            }
        }

        // Product Display Area with Dynamic Layout Mode
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            when (layoutMode) {
                ProductLayoutMode.LARGE_TOUCH -> {
                    // Big Touch Buttons
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 135.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            val qty = cart[product.id] ?: 0
                            ProductBigTouchCard(
                                product = product,
                                quantity = qty,
                                onAdd = { cart[product.id] = qty + 1 },
                                onRemove = {
                                    if (qty > 1) cart[product.id] = qty - 1
                                    else cart.remove(product.id)
                                },
                                onQuickNote = { note ->
                                    notesMap[product.id] = note
                                }
                            )
                        }
                    }
                }
                ProductLayoutMode.COMPACT_GRID -> {
                    // Compact High-density Grid
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 95.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            val qty = cart[product.id] ?: 0
                            ProductCompactCard(
                                product = product,
                                quantity = qty,
                                onAdd = { cart[product.id] = qty + 1 },
                                onRemove = {
                                    if (qty > 1) cart[product.id] = qty - 1
                                    else cart.remove(product.id)
                                }
                            )
                        }
                    }
                }
                ProductLayoutMode.LIST -> {
                    // List View with Quick Stepper
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            val qty = cart[product.id] ?: 0
                            ProductListRow(
                                product = product,
                                quantity = qty,
                                onAdd = { cart[product.id] = qty + 1 },
                                onRemove = {
                                    if (qty > 1) cart[product.id] = qty - 1
                                    else cart.remove(product.id)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Active Cart Bottom Bar
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Optional order note / customer identifier
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customerNote,
                        onValueChange = onCustomerNoteChange,
                        placeholder = { Text("رقم الزبون أو ملاحظة سريعة (اختياري)...", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        singleLine = true
                    )
                    if (cart.isNotEmpty()) {
                        TextButton(onClick = {
                            cart.clear()
                            notesMap.clear()
                        }) {
                            Text("تفريغ", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "المجموع: ${cart.values.sum()} منتج",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = Money(totalMillimes).formatArabic(),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = onSubmitOrder,
                        enabled = cart.isNotEmpty(),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("direct_submit_order_btn")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إرسال للكاونتر", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Large Touch Card - Optimized for rapid 1-tap touch in rush hours
 */
@Composable
fun ProductBigTouchCard(
    product: ProductEntity,
    quantity: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onQuickNote: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (quantity > 0) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (quantity > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onAdd() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // High-resolution photo with quantity badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Image(
                    painter = painterResource(id = getProductDrawableRes(product.id, product.categoryId)),
                    contentDescription = product.nameAr,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (quantity > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$quantity",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = product.nameAr,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = Money(product.priceMillimes).formatArabic(),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (quantity > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { onRemove() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    Text("$quantity", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { onAdd() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                // Quick Sugar Modifier Chips for Hot Beverages
                if (product.categoryId == "cat_coffee") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "بدون سكر",
                            fontSize = 10.sp,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .clickable { onQuickNote("بدون سكر") }
                        )
                        Text(
                            text = "زيادة",
                            fontSize = 10.sp,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .clickable { onQuickNote("سكر زيادة") }
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "+ إضافة",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact Card - High Density with Thumbnail Photo
 */
@Composable
fun ProductCompactCard(
    product: ProductEntity,
    quantity: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (quantity > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onAdd() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Image(
                    painter = painterResource(id = getProductDrawableRes(product.id, product.categoryId)),
                    contentDescription = product.nameAr,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (quantity > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$quantity",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = product.nameAr,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = Money(product.priceMillimes).formatArabic(),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * List Row with Left Thumbnail Photo
 */
@Composable
fun ProductListRow(
    product: ProductEntity,
    quantity: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (quantity > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    painter = painterResource(id = getProductDrawableRes(product.id, product.categoryId)),
                    contentDescription = product.nameAr,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Column {
                    Text(product.nameAr, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        Money(product.priceMillimes).formatArabic(),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (quantity > 0) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = null)
                    }
                    Text("$quantity", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewOrderDialog(
    initialTable: DiningTableEntity?,
    availableTables: List<DiningTableEntity>,
    categories: List<CategoryEntity>,
    products: List<ProductEntity>,
    onDismiss: () -> Unit,
    onSubmitOrder: (table: DiningTableEntity?, isTakeaway: Boolean, cartItems: List<Pair<ProductEntity, Int>>, notesMap: Map<String, String>) -> Unit
) {
    var selectedTable by remember { mutableStateOf(initialTable ?: availableTables.firstOrNull()) }
    var isTakeaway by remember { mutableStateOf(initialTable?.isTakeaway ?: false) }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var layoutMode by remember { mutableStateOf(ProductLayoutMode.LARGE_TOUCH) }
    var searchQuery by remember { mutableStateOf("") }

    val cart = remember { mutableStateMapOf<String, Int>() }
    val itemNotes = remember { mutableStateMapOf<String, String>() }

    val totalMillimes = remember(cart.toMap(), products) {
        cart.entries.sumOf { (prodId, qty) ->
            val product = products.find { it.id == prodId }
            (product?.priceMillimes ?: 0L) * qty
        }
    }

    val filteredProducts = remember(selectedCategoryId, searchQuery, products) {
        products.filter { prod ->
            val matchesCategory = selectedCategoryId.isBlank() || prod.categoryId == selectedCategoryId
            val matchesSearch = searchQuery.isBlank() ||
                    prod.nameAr.contains(searchQuery, ignoreCase = true) ||
                    prod.nameFr.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "تسجيل طلب جديد",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = isTakeaway,
                                onClick = { isTakeaway = !isTakeaway },
                                label = { Text("سفري") }
                            )
                            if (!isTakeaway && selectedTable != null) {
                                Text(
                                    text = selectedTable!!.name,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إلغاء")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search & Layout Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("بحث سريع...", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        singleLine = true
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            IconButton(onClick = { layoutMode = ProductLayoutMode.LARGE_TOUCH }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Apps, contentDescription = null, tint = if (layoutMode == ProductLayoutMode.LARGE_TOUCH) MaterialTheme.colorScheme.primary else Color.Gray)
                            }
                            IconButton(onClick = { layoutMode = ProductLayoutMode.COMPACT_GRID }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.GridView, contentDescription = null, tint = if (layoutMode == ProductLayoutMode.COMPACT_GRID) MaterialTheme.colorScheme.primary else Color.Gray)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Category Tabs
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId.isBlank(),
                            onClick = { selectedCategoryId = "" },
                            label = { Text("الكل") }
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.nameAr) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Products View
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (layoutMode == ProductLayoutMode.LARGE_TOUCH) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 110.dp),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredProducts, key = { it.id }) { product ->
                                val qty = cart[product.id] ?: 0
                                ProductBigTouchCard(
                                    product = product,
                                    quantity = qty,
                                    onAdd = { cart[product.id] = qty + 1 },
                                    onRemove = {
                                        if (qty > 1) cart[product.id] = qty - 1
                                        else cart.remove(product.id)
                                    },
                                    onQuickNote = { note -> itemNotes[product.id] = note }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 90.dp),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredProducts, key = { it.id }) { product ->
                                val qty = cart[product.id] ?: 0
                                ProductCompactCard(
                                    product = product,
                                    quantity = qty,
                                    onAdd = { cart[product.id] = qty + 1 },
                                    onRemove = {
                                        if (qty > 1) cart[product.id] = qty - 1
                                        else cart.remove(product.id)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Footer Cart & Submit
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "المجموع: ${cart.values.sum()} منتج",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = Money(totalMillimes).formatArabic(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = {
                                if (cart.isNotEmpty()) {
                                    val cartItems = cart.mapNotNull { (prodId, qty) ->
                                        val prod = products.find { it.id == prodId }
                                        prod?.let { it to qty }
                                    }
                                    onSubmitOrder(selectedTable, isTakeaway, cartItems, itemNotes.toMap())
                                }
                            },
                            enabled = cart.isNotEmpty(),
                            modifier = Modifier.height(46.dp).testTag("submit_waiter_order_btn")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إرسال للكاونتر", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
