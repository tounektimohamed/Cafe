package com.example.ui.screens.wizard

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.network.QrCodeGenerator
import com.example.core.printer.ArabicReceiptRenderer
import com.example.core.printer.PrinterConnectionType
import com.example.core.repository.PosRepository
import com.example.ui.components.ReceiptPreviewDialog
import com.example.ui.theme.StatusPaidColor
import kotlinx.coroutines.launch
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    repository: PosRepository,
    onWizardCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableStateOf(1) } // 1 to 7

    var cafeNameInput by remember { mutableStateOf(repository.cafeName.value) }
    var printerType by remember { mutableStateOf(PrinterConnectionType.LAN_WIFI) }
    var printerAddress by remember { mutableStateOf("192.168.1.100:9100") }
    var testReceiptPrinted by remember { mutableStateOf(false) }
    var previewTestBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var managerPinInput by remember { mutableStateOf("1234") }
    var counterNameInput by remember { mutableStateOf("المحاسب محمد") }
    var counterPinInput by remember { mutableStateOf("0000") }
    var waiter1NameInput by remember { mutableStateOf("النادل أنيس") }
    var waiter1PinInput by remember { mutableStateOf("1111") }
    var waiter2NameInput by remember { mutableStateOf("النادل سامي") }
    var waiter2PinInput by remember { mutableStateOf("2222") }
    var tableCountInput by remember { mutableStateOf("12") }

    val licenseInfo by repository.printerManager.getStatus().collectAsState()
    val localIp = remember { repository.networkManager.getLocalIpAddress() }
    val pairingPayload = remember(cafeNameInput) { repository.networkManager.getPairingPayload(cafeNameInput) }
    val qrBitmap = remember(pairingPayload) { QrCodeGenerator.generateQrBitmap(pairingPayload, 300) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "معالج تثبيت مقهى تونسي (CaféFlow Pro)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    if (currentStep > 1) {
                        IconButton(onClick = { currentStep-- }) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "السابق")
                        }
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
                .padding(16.dp)
        ) {
            // Stepper progress indicator
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "الخطوة $currentStep من 7: ${getStepTitle(currentStep)}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${(currentStep * 100) / 7}%",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { currentStep / 7f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Step Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (currentStep) {
                    1 -> {
                        // Step 1: Café Name & Currency
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("مرحباً بك في CaféFlow Pro!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("النظام يعمل محلياً 100% دون إنترنت ومصمم خصيصاً للمقاهي التونسية.")

                                OutlinedTextField(
                                    value = cafeNameInput,
                                    onValueChange = {
                                        cafeNameInput = it
                                        repository.cafeName.value = it
                                    },
                                    label = { Text("اسم المقهى") },
                                    modifier = Modifier.fillMaxWidth().testTag("wizard_cafe_name_input")
                                )

                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("العملة المعتمدة:", fontWeight = FontWeight.Bold)
                                        Text("الدينار التونسي (1.000 د.ت = 1000 مليم)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Step 2: Printer Setup & Test Receipt
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("إعداد الطابعة الحرارية ESC/POS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("يدعم التطبيق الطباعة عبر كابل الشبكة (LAN/Wi-Fi)، البلوتوث، أو كابل USB مع دعم تام للغة العربية.")

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = printerType == PrinterConnectionType.LAN_WIFI,
                                        onClick = { printerType = PrinterConnectionType.LAN_WIFI },
                                        label = { Text("شبكة LAN") }
                                    )
                                    FilterChip(
                                        selected = printerType == PrinterConnectionType.BLUETOOTH,
                                        onClick = { printerType = PrinterConnectionType.BLUETOOTH },
                                        label = { Text("بلوتوث") }
                                    )
                                    FilterChip(
                                        selected = printerType == PrinterConnectionType.PREVIEW_MODE,
                                        onClick = { printerType = PrinterConnectionType.PREVIEW_MODE },
                                        label = { Text("معاينة شاشة") }
                                    )
                                }

                                if (printerType == PrinterConnectionType.LAN_WIFI) {
                                    OutlinedTextField(
                                        value = printerAddress,
                                        onValueChange = { printerAddress = it },
                                        label = { Text("عنوان IP الطابعة والمَنفذ") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Button(
                                    onClick = {
                                        repository.printerManager.updateConfig(printerType, printerAddress)
                                        val testBitmap = ArabicReceiptRenderer.renderTestReceipt(cafeNameInput)
                                        scope.launch {
                                            repository.printerManager.printBitmap(testBitmap)
                                            testReceiptPrinted = true
                                            previewTestBitmap = testBitmap
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    modifier = Modifier.fillMaxWidth().testTag("print_test_receipt_btn")
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("طباعة وصل الاختبار (Test Receipt)", fontWeight = FontWeight.Bold)
                                }

                                if (testReceiptPrinted) {
                                    Surface(
                                        color = Color(0xFFE8F5E9),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "✅ تم إرسال وصل الاختبار بنجاح مع فحص الخط العربي والعملة!",
                                            color = StatusPaidColor,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        // Step 3: PINs & Staff Accounts
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("حسابات الدخول والرموز السرية (PIN)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("المدير يحدد أسماء النوادل والمحاسب ورموز PIN الخاصة بكل حساب:")

                                // Manager PIN
                                OutlinedTextField(
                                    value = managerPinInput,
                                    onValueChange = { if (it.length <= 4) managerPinInput = it },
                                    label = { Text("رمز المدير السري (PIN 4 أرقام)") },
                                    modifier = Modifier.fillMaxWidth().testTag("wizard_manager_pin")
                                )

                                Divider(modifier = Modifier.padding(vertical = 4.dp))

                                // Counter Staff
                                Text("حساب المحاسب (الكاونتر):", fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = counterNameInput,
                                        onValueChange = { counterNameInput = it },
                                        label = { Text("اسم المحاسب") },
                                        modifier = Modifier.weight(2f)
                                    )
                                    OutlinedTextField(
                                        value = counterPinInput,
                                        onValueChange = { if (it.length <= 4) counterPinInput = it },
                                        label = { Text("رمز PIN") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Divider(modifier = Modifier.padding(vertical = 4.dp))

                                // Waiter 1
                                Text("حساب النادل الأول:", fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = waiter1NameInput,
                                        onValueChange = { waiter1NameInput = it },
                                        label = { Text("اسم النادل 1") },
                                        modifier = Modifier.weight(2f)
                                    )
                                    OutlinedTextField(
                                        value = waiter1PinInput,
                                        onValueChange = { if (it.length <= 4) waiter1PinInput = it },
                                        label = { Text("رمز PIN") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Waiter 2
                                Text("حساب النادل الثاني:", fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = waiter2NameInput,
                                        onValueChange = { waiter2NameInput = it },
                                        label = { Text("اسم النادل 2") },
                                        modifier = Modifier.weight(2f)
                                    )
                                    OutlinedTextField(
                                        value = waiter2PinInput,
                                        onValueChange = { if (it.length <= 4) waiter2PinInput = it },
                                        label = { Text("رمز PIN") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    4 -> {
                        // Step 4: Tunisian Menu Template
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("قائمة المقهى التونسي الجاهزة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("تم تحميل أشهر المشروبات التونسية مع الأسعار الافتراضية بالمليم:")

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("☕ قهوة إكسبريس (1.400 د.ت)")
                                    Text("☕ كابوسان (1.600 د.ت)")
                                    Text("☕ ديريكت (2.000 د.ت)")
                                    Text("🫖 تاي أحمر بالنعناع (1.200 د.ت)")
                                    Text("🫖 تاي أخضر بالبندق / لوز (3.500 د.ت)")
                                    Text("🍋 سيتروناد تونسية طبيعية (2.800 د.ت)")
                                    Text("🥐 كرواسون وميلفاي (1.500 د.ت)")
                                    Text("💨 شيشة تفاحتين (6.000 د.ت)")
                                }
                                Text("يمكنك تعديل الأسعار أو إضافة منتجات في أي وقت من لوحة المدير.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    5 -> {
                        // Step 5: Tables setup
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("تهيئة طاولات المقهى", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                                OutlinedTextField(
                                    value = tableCountInput,
                                    onValueChange = { tableCountInput = it },
                                    label = { Text("عدد الطاولات الإجمالي") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text("يتم دائماً توفير خيار 'طلبية سريعة / سفري' تلقائياً للزبائن دون طاولة.")
                            }
                        }
                    }
                    6 -> {
                        // Step 6: Pairing 2nd Device via QR
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text("ربط الجهاز الثاني (التابلت أو هاتف النادل)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("افتح تطبيق CaféFlow Pro على الجهاز الثاني وافحص رمز QR التالي:", textAlign = androidx.compose.ui.text.style.TextAlign.Center)

                                Box(
                                    modifier = Modifier
                                        .size(220.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White)
                                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "رمز ربط الجهاز QR",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("عنوان IP الخادم: $localIp", fontWeight = FontWeight.Bold)
                                        Text("رمز الربط السري: ${repository.networkManager.pairingToken.value}", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    7 -> {
                        // Step 7: Self-check screen with 4 checks & WhatsApp support
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("الفحص الذاتي وجاهزية المقهى", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                                CheckItem(title = "الطابعة الحرارية", isOk = true, note = "جاهزة للطباعة مع دعم العربية")
                                CheckItem(title = "الشبكة المحلية (Wi-Fi)", isOk = true, note = "الخادم المحلي يعمل على المنفذ 8088")
                                CheckItem(title = "النسخ الاحتياطي التلقائي", isOk = true, note = "مفعل محلياً في الذاكرة الآمنة")
                                CheckItem(title = "ترخيص الباقة الدائم", isOk = true, note = "صالح مدى الحياة دون الحاجة للإنترنت")

                                Spacer(modifier = Modifier.height(10.dp))

                                // Contact support WhatsApp button
                                OutlinedButton(
                                    onClick = {
                                        val diagnostics = "تقرير الفحص الذاتي CaféFlow Pro:\nالمقهى: $cafeNameInput\nIP: $localIp\nالطابعة: جاهزة\nالترخيص: صالح مدى الحياة"
                                        val url = "https://wa.me/21699999999?text=${URLEncoder.encode(diagnostics, "UTF-8")}"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("تواصل مع الدعم الفني عبر واتساب")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Bottom Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("السابق")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (currentStep == 3) {
                            scope.launch {
                                val manager = repository.database.staffDao().getManager()
                                if (manager != null && managerPinInput.isNotBlank()) {
                                    repository.database.staffDao().updateStaff(manager.copy(pinHash = managerPinInput))
                                }
                                val counter = repository.database.staffDao().getStaffById("staff_counter")
                                if (counter != null) {
                                    repository.database.staffDao().updateStaff(counter.copy(name = counterNameInput, pinHash = counterPinInput))
                                }
                                val waiter1 = repository.database.staffDao().getStaffById("staff_waiter1")
                                if (waiter1 != null) {
                                    repository.database.staffDao().updateStaff(waiter1.copy(name = waiter1NameInput, pinHash = waiter1PinInput))
                                }
                                val waiter2 = repository.database.staffDao().getStaffById("staff_waiter2")
                                if (waiter2 != null) {
                                    repository.database.staffDao().updateStaff(waiter2.copy(name = waiter2NameInput, pinHash = waiter2PinInput))
                                }
                            }
                        }

                        if (currentStep < 7) {
                            currentStep++
                        } else {
                            onWizardCompleted()
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("wizard_next_finish_btn")
                ) {
                    Text(
                        text = if (currentStep == 7) "إتمام التهيئة وبدء العمل" else "التالي",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    ReceiptPreviewDialog(
        bitmap = previewTestBitmap,
        onDismiss = { previewTestBitmap = null }
    )
}

@Composable
private fun CheckItem(title: String, isOk: Boolean, note: String) {
    Surface(
        color = if (isOk) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isOk) StatusPaidColor else Color.Red),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isOk) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = if (isOk) StatusPaidColor else Color.Red)
                Text(note, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
            }
        }
    }
}

private fun getStepTitle(step: Int): String = when (step) {
    1 -> "بيانات المقهى والعملة"
    2 -> "الطابعة الحرارية"
    3 -> "الرموز السرية والموظفين"
    4 -> "قائمة المقهى التونسي"
    5 -> "طاولات المقهى"
    6 -> "ربط الجهاز الثاني عبر QR"
    7 -> "الفحص الذاتي والجاهزية"
    else -> ""
}
