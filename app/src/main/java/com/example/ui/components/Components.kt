package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.model.Money
import com.example.core.network.ConnectionStatus
import com.example.ui.theme.*

@Composable
fun MoneyBadge(
    money: Money,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = Color.White,
    fontSize: Int = 16
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        color = color
    ) {
        Text(
            text = money.formatArabic(),
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ConnectionStatusBadge(
    status: ConnectionStatus,
    isHost: Boolean,
    modifier: Modifier = Modifier
) {
    val (dotColor, text) = when (status) {
        ConnectionStatus.CONNECTED -> if (isHost) (Color(0xFF2E7D32) to "الخادم الرئيسي (الكاونتر)") else (Color(0xFF2E7D32) to "متصل بالخادم")
        ConnectionStatus.RECONNECTING -> (Color(0xFFE65100) to "جارِ الاتصال...")
        ConnectionStatus.OFFLINE -> (Color(0xFFC62828) to "وضع دون اتصال")
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = dotColor.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, dotColor.copy(alpha = 0.4f)),
        modifier = modifier.testTag("connection_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = dotColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun PinPadDialog(
    title: String = "أدخل رمز الدخول (PIN)",
    subtitle: String? = null,
    onDismiss: () -> Unit,
    onPinSubmitted: (String) -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("pin_pad_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    repeat(4) { index ->
                        val isFilled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3x4 Keypad
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "OK")
                )

                keys.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { key ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (key == "OK") MaterialTheme.colorScheme.primary
                                else if (key == "C") MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(68.dp)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        when (key) {
                                            "C" -> {
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                }
                                            }
                                            "OK" -> {
                                                if (enteredPin.length >= 4) {
                                                    onPinSubmitted(enteredPin)
                                                }
                                            }
                                            else -> {
                                                if (enteredPin.length < 4) {
                                                    enteredPin += key
                                                    if (enteredPin.length == 4) {
                                                        onPinSubmitted(enteredPin)
                                                    }
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = key,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (key == "OK") MaterialTheme.colorScheme.onPrimary
                                        else if (key == "C") MaterialTheme.colorScheme.onErrorContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("pin_cancel_button")
                ) {
                    Text("إلغاء", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun CancellationDialog(
    orderNumber: Int,
    requiresManagerPin: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (reason: String, pin: String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    val quickReasons = listOf(
        "الزبون غادر المقهى",
        "خطأ في إدخال الطلب",
        "المنتج غير متوفر",
        "تأخر في التحضير"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("إلغاء الطلب #$orderNumber", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("حدد أو اكتب سبب الإلغاء الإجباري لسجل الرقابة:")

                // Quick choice chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickReasons.take(2).forEach { r ->
                        SuggestionChip(
                            onClick = { reason = r },
                            label = { Text(r, fontSize = 12.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("سبب الإلغاء") },
                    isError = hasError && reason.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cancel_reason_input")
                )

                if (requiresManagerPin) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "هذا الطلب مدفوع! يلزم إدخال رمز المدير (PIN) للمصادقة:",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("رمز المدير (1234)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manager_pin_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reason.isBlank()) {
                        hasError = true
                    } else {
                        onConfirm(reason, pin)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_cancel_button")
            ) {
                Text("تأكيد الإلغاء")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("تراجع")
            }
        }
    )
}

@Composable
fun ReceiptPreviewDialog(
    bitmap: Bitmap?,
    onDismiss: () -> Unit
) {
    if (bitmap == null) return

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "معاينة الوصل الحراري",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "معاينة الوصل",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("تمت المعاينة والطباعة بنجاح")
                }
            }
        }
    }
}
