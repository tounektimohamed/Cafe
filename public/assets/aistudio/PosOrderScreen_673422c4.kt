package com.example.ui.screens.pos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.data.entity.CategoryEntity
import com.example.core.data.entity.ModifierOptionEntity
import com.example.core.data.entity.ProductEntity
import com.example.core.model.Money
import com.example.ui.screens.waiter.getProductDrawableRes
import com.example.ui.theme.StatusPaidColor
import com.example.ui.theme.StatusAwaitingColor
import java.util.UUID

// ───────────────────────── State ─────────────────────────

data class PosCartLine(
    val id: String = UUID.randomUUID().toString(),
    val product: ProductEntity,
    val quantity: Int = 1,
    val modifiers: List<ModifierOptionEntity> = emptyList()
) {
    val unitPrice: Money
        get() = Money(product.priceMillimes + modifiers.sumOf { it.extraPriceMillimes })
    val total: Money get() = unitPrice * quantity
}

/** Hoist this in a ViewModel (or remember it) and pass it to [PosOrderScreen]. */
@Stable
class PosCartState {
    val lines = mutableStateListOf<PosCartLine>()
    var discountPercent by mutableStateOf(0)

    val itemCount: Int get() = lines.sumOf { it.quantity }
    val subtotal: Money get() = lines.fold(Money(0L)) { acc, l -> acc + l.total }
    val discount: Money get() = subtotal.percentage(discountPercent)
    val total: Money get() = subtotal - discount

    fun quantityOf(productId: String): Int =
        lines.filter { it.product.id == productId }.sumOf { it.quantity }

    fun add(product: ProductEntity) {
        val i = lines.indexOfFirst { it.product.id == product.id && it.modifiers.isEmpty() }
        if (i >= 0) lines[i] = lines[i].copy(quantity = lines[i].quantity + 1)
        else lines.add(PosCartLine(product = product))
    }

    fun increment(lineId: String) {
        val i = lines.indexOfFirst { it.id == lineId }
        if (i >= 0) lines[i] = lines[i].copy(quantity = lines[i].quantity + 1)
    }

    fun decrement(lineId: String) {
        val i = lines.indexOfFirst { it.id == lineId }
        if (i < 0) return
        if (lines[i].quantity <= 1) lines.removeAt(i)
        else lines[i] = lines[i].copy(quantity = lines[i].quantity - 1)
    }

    fun setModifiers(lineId: String, mods: List<ModifierOptionEntity>) {
        val i = lines.indexOfFirst { it.id == lineId }
        if (i >= 0) lines[i] = lines[i].copy(modifiers = mods)
    }

    fun clear() {
        lines.clear()
        discountPercent = 0
    }
}

// ───────────────────────── Screen ─────────────────────────

private const val ALL = "__ALL__"
private const val FAV = "__FAV__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosOrderScreen(
    title: String,
    categories: List<CategoryEntity>,
    products: List<ProductEntity>,
    modifierOptions: List<ModifierOptionEntity>,
    cart: PosCartState,
    onSendToKitchen: () -> Unit,
    onPay: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingLineId by remember { mutableStateOf<String?>(null) }
    var showCartSheet by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 840.dp

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                MenuPane(
                    title, categories, products, cart,
                    Modifier.weight(1f).fillMaxHeight()
                )
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier.width(380.dp).fillMaxHeight()
                ) {
                    CartPanel(
                        cart = cart,
                        onEditLine = { if (modifierOptions.isNotEmpty()) editingLineId = it },
                        onSendToKitchen = onSendToKitchen,
                        onPay = onPay,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                MenuPane(title, categories, products, cart, Modifier.weight(1f).fillMaxWidth())
                CartBar(cart) { showCartSheet = true }
            }
        }
    }

    if (showCartSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCartSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            CartPanel(
                cart = cart,
                onEditLine = { if (modifierOptions.isNotEmpty()) editingLineId = it },
                onSendToKitchen = { showCartSheet = false; onSendToKitchen() },
                onPay = { showCartSheet = false; onPay() },
                modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp)
            )
        }
    }

    editingLineId?.let { id ->
        cart.lines.firstOrNull { it.id == id }?.let { line ->
            ModifiersDialog(
                line = line,
                options = modifierOptions,
                onDismiss = { editingLineId = null },
                onConfirm = { cart.setModifiers(id, it); editingLineId = null }
            )
        } ?: run { editingLineId = null }
    }
}

// ───────────────────────── Menu ─────────────────────────

@Composable
private fun MenuPane(
    title: String,
    categories: List<CategoryEntity>,
    products: List<ProductEntity>,
    cart: PosCartState,
    modifier: Modifier = Modifier
) {
    var selected by rememberSaveable { mutableStateOf(ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var french by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val visible = remember(products, selected, query) {
        products
            .asSequence()
            .filter {
                when (selected) {
                    ALL -> true
                    FAV -> it.isFavorite
                    else -> it.categoryId == selected
                }
            }
            .filter {
                query.isBlank() ||
                    it.nameAr.contains(query, ignoreCase = true) ||
                    it.nameFr.contains(query, ignoreCase = true)
            }
            .sortedBy { it.sortOrder }
            .toList()
    }

    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { french = !french }) { Text(if (french) "عربي" else "FR") }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("ابحث عن منتج…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "مسح") }
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { CategoryChip("الكل", selected == ALL) { selected = ALL } }
            item {
                CategoryChip("⭐ المفضلة", selected == FAV) { selected = FAV }
            }
            items(categories.sortedBy { it.sortOrder }, key = { it.id }) { c ->
                CategoryChip(if (french) c.nameFr else c.nameAr, selected == c.id) { selected = c.id }
            }
        }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد نتائج", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(visible, key = { it.id }) { p ->
                    ProductTile(
                        product = p,
                        french = french,
                        inCart = cart.quantityOf(p.id),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            cart.add(p)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        modifier = Modifier.heightIn(min = 44.dp)
    )
}

@Composable
private fun ProductTile(
    product: ProductEntity,
    french: Boolean,
    inCart: Int,
    onClick: () -> Unit
) {
    val sellable = product.isAvailable && product.stockQuantity > 0
    val lowStock = sellable && product.stockQuantity <= 5

    Card(
        onClick = onClick,
        enabled = sellable,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box {
            Column {
                Image(
                    painter = painterResource(getProductDrawableRes(product.id, product.categoryId)),
                    contentDescription = product.nameAr,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.35f)
                )
                Column(Modifier.padding(10.dp)) {
                    Text(
                        if (french) product.nameFr else product.nameAr,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        minLines = 2,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Money(product.priceMillimes).formatArabic(),
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
            }

            if (inCart > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) { Text("$inCart", fontSize = 14.sp) }
            }
            if (lowStock) {
                Text(
                    "آخر القطع",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusAwaitingColor)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            if (!sellable) {
                Box(
                    Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) { Text("غير متوفر", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ───────────────────────── Cart ─────────────────────────

@Composable
private fun CartBar(cart: PosCartState, onOpen: () -> Unit) {
    Surface(tonalElevation = 6.dp, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BadgedBox(badge = { if (cart.itemCount > 0) Badge { Text("${cart.itemCount}") } }) {
                Icon(Icons.Filled.ShoppingCart, null, Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("المجموع", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(cart.total.formatArabic(), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
            Button(
                onClick = onOpen,
                enabled = cart.lines.isNotEmpty(),
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("عرض الطلب", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CartPanel(
    cart: PosCartState,
    onEditLine: (String) -> Unit,
    onSendToKitchen: () -> Unit,
    onPay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "الطلب الحالي",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (cart.lines.isNotEmpty()) {
                TextButton(onClick = { cart.clear() }) {
                    Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إفراغ")
                }
            }
        }

        if (cart.lines.isEmpty()) {
            Box(Modifier.weight(1f, fill = true).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("اضغط على منتج لإضافته", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cart.lines, key = { it.id }) { line ->
                    CartLineRow(
                        line = line,
                        onInc = { cart.increment(line.id) },
                        onDec = { cart.decrement(line.id) },
                        onEdit = { onEditLine(line.id) }
                    )
                }
            }
        }

        HorizontalDivider()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("خصم", fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp))
            listOf(0, 5, 10, 20).forEach { pct ->
                FilterChip(
                    selected = cart.discountPercent == pct,
                    onClick = { cart.discountPercent = pct },
                    label = { Text(if (pct == 0) "بدون" else "$pct%") }
                )
            }
        }

        if (cart.discountPercent > 0) {
            TotalRow("المجموع الفرعي", cart.subtotal.formatArabic())
            TotalRow("الخصم", "- " + cart.discount.formatArabic())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("الإجمالي", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(cart.total.formatArabic(), fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onSendToKitchen,
                enabled = cart.lines.isNotEmpty(),
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("إرسال", fontWeight = FontWeight.Bold) }
            Button(
                onClick = onPay,
                enabled = cart.lines.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = StatusPaidColor),
                modifier = Modifier.weight(1.4f).height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("دفع", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
private fun CartLineRow(
    line: PosCartLine,
    onInc: () -> Unit,
    onDec: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(line.product.nameAr, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (line.modifiers.isNotEmpty()) {
                    Text(
                        line.modifiers.joinToString("، ") { it.nameAr },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 2
                    )
                }
                Text(line.total.formatArabic(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperButton(Icons.Filled.Remove, "إنقاص", onDec)
                Text(
                    "${line.quantity}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    modifier = Modifier.widthIn(min = 28.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                StepperButton(Icons.Filled.Add, "زيادة", onInc)
            }
        }
    }
}

@Composable
private fun StepperButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = MaterialTheme.colorScheme.onPrimary) }
}

// ───────────────────────── Modifiers dialog ─────────────────────────

@Composable
private fun ModifiersDialog(
    line: PosCartLine,
    options: List<ModifierOptionEntity>,
    onDismiss: () -> Unit,
    onConfirm: (List<ModifierOptionEntity>) -> Unit
) {
    var chosen by remember(line.id) { mutableStateOf(line.modifiers) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(line.product.nameAr, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(options, key = { it.id }) { opt ->
                    val checked = chosen.any { it.id == opt.id }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                chosen = when {
                                    checked -> chosen.filterNot { it.id == opt.id }
                                    // one sugar level / one size at a time
                                    opt.isSugar -> chosen.filterNot { it.isSugar } + opt
                                    opt.isSize -> chosen.filterNot { it.isSize } + opt
                                    else -> chosen + opt
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(opt.nameAr, Modifier.weight(1f))
                        if (opt.extraPriceMillimes > 0) {
                            Text("+ " + Money(opt.extraPriceMillimes).formatArabic(), fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(chosen) }) { Text("تأكيد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
