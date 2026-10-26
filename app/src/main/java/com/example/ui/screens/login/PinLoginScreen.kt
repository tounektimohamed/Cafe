package com.example.ui.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.data.entity.StaffEntity
import com.example.core.model.Role
import com.example.core.repository.PosRepository
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.components.PinPadDialog
import com.example.ui.theme.DarkRoast
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinLoginScreen(
    repository: PosRepository,
    onRoleSelected: (Role, StaffEntity) -> Unit,
    onOpenWizard: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val staffList by repository.getAllStaff().collectAsState(initial = emptyList())
    val connectionStatus by repository.networkManager.connectionStatus.collectAsState()
    val isHost by repository.networkManager.isHost.collectAsState()

    var staffToLogin by remember { mutableStateOf<StaffEntity?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "CaféFlow Pro — اختيار الحساب والوردية",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        ConnectionStatusBadge(status = connectionStatus, isHost = isHost)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenWizard, modifier = Modifier.testTag("wizard_icon_btn")) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "معالج التثبيت")
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Café Branding Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_coffee_espresso),
                        contentDescription = "Café",
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column {
                        Text(
                            text = repository.cafeName.value,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "نظام الدخول الإجباري للموظفين والورديات بالرمز السري (PIN)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "اختر حسابك وأدخل الرمز السري لبدء العمل:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(staffList, key = { it.id }) { staff ->
                    val role = Role.fromString(staff.role)
                    val (icon, color) = when (role) {
                        Role.COUNTER -> (Icons.Default.PointOfSale to MaterialTheme.colorScheme.primary)
                        Role.WAITER -> (Icons.Default.RestaurantMenu to MaterialTheme.colorScheme.secondary)
                        Role.MANAGER -> (Icons.Default.AdminPanelSettings to Color(0xFF6A1B9A))
                    }

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier
                            .height(180.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { staffToLogin = staff }
                            .testTag("staff_card_${staff.id}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = color.copy(alpha = 0.15f),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = staff.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = role.titleAr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick hint
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "💡 أدخل رمز PIN الخاص بحسابك. يمكن للمدير تخصيص أسماء النوادل وتغيير رمز PIN لكل حساب من لوحة تحكم المدير.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    // PIN Entry Dialog
    staffToLogin?.let { staff ->
        PinPadDialog(
            title = "تسجيل دخول: ${staff.name}",
            subtitle = "أدخل الرمز السري المكون من 4 أرقام",
            onDismiss = { staffToLogin = null },
            onPinSubmitted = { enteredPin ->
                if (enteredPin == staff.pinHash) {
                    val role = Role.fromString(staff.role)
                    repository.currentStaff.value = staff
                    repository.currentRole.value = role
                    staffToLogin = null
                    onRoleSelected(role, staff)
                } else {
                    errorMessage = "الرمز السري غير صحيح، حاول مرة أخرى"
                }
            }
        )
    }

    // Error Alert
    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("خطأ في التحقق") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("حسناً")
                }
            }
        )
    }
}
