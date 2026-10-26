package com.example.core.license

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class LicenseInfo(
    val isValid: Boolean = true,
    val isLifetime: Boolean = true,
    val licenseType: String = "باقة المقهى الكاملة (جهازين + طابعة + ترخيص مدى الحياة)",
    val deviceInstallationId: String,
    val customerName: String = "مقهى تونسي معتمد",
    val statusMessageAr: String = "الترخيص مفعل وصالح مدى الحياة (Offline Verified)",
    val isReadOnly: Boolean = false
)

class LicenseManager(context: Context) {

    private val prefs = context.getSharedPreferences("cafeflow_license_prefs", Context.MODE_PRIVATE)

    private val _licenseInfo = MutableStateFlow(loadLicense(context))
    val licenseInfo: StateFlow<LicenseInfo> = _licenseInfo.asStateFlow()

    private fun loadLicense(context: Context): LicenseInfo {
        var installId = prefs.getString("device_installation_id", null)
        if (installId == null) {
            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (e: Exception) {
                null
            }
            installId = "CFP-TN-" + (androidId?.take(8)?.uppercase() ?: UUID.randomUUID().toString().take(8).uppercase())
            prefs.edit().putString("device_installation_id", installId).apply()
        }

        return LicenseInfo(
            isValid = true,
            isLifetime = true,
            licenseType = "باقة المقاهي التونسية (ترخيص دائم)",
            deviceInstallationId = installId,
            customerName = prefs.getString("cafe_name", "مقهى النخيل - تونس") ?: "مقهى النخيل - تونس",
            statusMessageAr = "الترخيص مفعل وصالح مدى الحياة دون الحاجة للإنترنت",
            isReadOnly = false
        )
    }

    fun updateCustomerName(name: String) {
        prefs.edit().putString("cafe_name", name).apply()
        _licenseInfo.value = _licenseInfo.value.copy(customerName = name)
    }
}
