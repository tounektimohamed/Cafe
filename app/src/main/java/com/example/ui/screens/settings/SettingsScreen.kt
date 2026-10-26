package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.license.LicenseInfo
import com.example.core.printer.PrinterConnectionType
import com.example.core.repository.PosRepository
import com.example.ui.components.PinPadDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: PosRepository,
    onNavigateBack: () -> Unit,
    onRerunWizard: () -> Unit
) {
    var cafeNameText by remember { mutableStateOf(repository.cafeName.value) }
    var paymentFirst by remember { mutableStateOf(repository.paymentBeforePreparation.value) }
    var alertThresholdMinutes by remember { mutableStateOf(repository.awaitingPaymentAlertMinutes.value.toString()) }
    var showManagerPinDialog by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val licenseInfo by repository.licenseManager.licenseInfo.collectAsState()
    val printerStatus by repository.printerManager.getStatus().collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعدادات وتكوين CaféFlow Pro", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // General Settings Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("بيانات المقهى والنظام", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = cafeNameText,
                        onValueChange = {
                            cafeNameText = it
                            repository.cafeName.value = it
                        },
                        label = { Text("اسم المقهى في الوصل") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Payment-first toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الدفع قبل التحضير (Payment First)", fontWeight = FontWeight.Bold)
                            Text(
                                "عند التفعيل: لا تطبع تذكرة التحضير ولا يبدأ العمل إلا بعد تأكيد خلاص الزبون.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = paymentFirst,
                            onCheckedChange = {
                                paymentFirst = it
                                repository.paymentBeforePreparation.value = it
                            },
                            modifier = Modifier.testTag("payment_first_switch")
                        )
                    }

                    Divider()

                    // Tables Mode toggle (بالطاولات أو بدون)
                    var tablesModeEnabled by remember { mutableStateOf(repository.enableTablesMode.value) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("نظام الطاولات لشاشة النادل", fontWeight = FontWeight.Bold)
                            Text(
                                if (tablesModeEnabled) "مفعل: النادل يختار الطاولة أولاً من الخريطة."
                                else "معطل: طلبات مباشرة وسريعة فورية دون الحاجة لاختيار طاولة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = tablesModeEnabled,
                            onCheckedChange = {
                                tablesModeEnabled = it
                                repository.enableTablesMode.value = it
                            },
                            modifier = Modifier.testTag("tables_mode_switch")
                        )
                    }

                    Divider()

                    // Alert threshold
                    OutlinedTextField(
                        value = alertThresholdMinutes,
                        onValueChange = {
                            alertThresholdMinutes = it
                            val parsed = it.toIntOrNull()
                            if (parsed != null && parsed > 0) {
                                repository.awaitingPaymentAlertMinutes.value = parsed
                            }
                        },
                        label = { Text("مهلة تنبيه الطلبات المعلقة في انتظار الدفع (بالدقائق)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Printer Settings Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("الطابعة الحرارية ESC/POS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("النوع الحالي: ${printerStatus.connectionType.name}")
                    Text("العنوان: ${printerStatus.targetAddress}")
                    Text("الحالة: ${if (printerStatus.isConnected) "متصلة وطباعة العربية مفعلة ✅" else "غير متصلة ❌"}")
                }
            }

            // License Information Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ترخيص الجهاز (Offline License)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("معرّف الجهاز (Installation ID): ${licenseInfo.deviceInstallationId}", fontWeight = FontWeight.SemiBold)
                    Text("نوع الباقة: ${licenseInfo.licenseType}")
                    Text("الحالة: ${licenseInfo.statusMessageAr}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            // Rerun Setup Wizard button (Manager approval)
            Button(
                onClick = { showManagerPinDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth().testTag("rerun_wizard_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إعادة تشغيل معالج التثبيت والتهيئة (خاص بالمدير)")
            }
        }
    }

    if (showManagerPinDialog) {
        PinPadDialog(
            title = "موافقة المدير مطلوبة",
            subtitle = "أدخل رمز المدير لتشغيل معالج التثبيت",
            onDismiss = { showManagerPinDialog = false },
            onPinSubmitted = { pin ->
                if (pin == "1234") {
                    showManagerPinDialog = false
                    onRerunWizard()
                } else {
                    snackbarMessage = "الرمز السري غير صحيح"
                }
            }
        )
    }

    snackbarMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { snackbarMessage = null },
            title = { Text("تنبيه") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { snackbarMessage = null }) {
                    Text("حسناً")
                }
            }
        )
    }
}
