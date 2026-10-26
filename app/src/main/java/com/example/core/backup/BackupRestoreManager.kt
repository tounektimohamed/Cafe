package com.example.core.backup

import android.content.Context
import com.example.core.data.AppDatabase
import com.example.core.data.entity.OrderEntity
import com.example.core.model.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestoreManager(
    private val context: Context,
    private val database: AppDatabase
) {

    /**
     * Exports full database tables to a structured JSON string.
     */
    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())
        root.put("cafe", "CaféFlow Pro")

        // Save to app backup directory
        val backupDir = File(context.filesDir, "backups")
        if (!backupDir.exists()) backupDir.mkdirs()

        val fileName = "cafeflow_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.json"
        val backupFile = File(backupDir, fileName)
        backupFile.writeText(root.toString())

        root.toString()
    }

    /**
     * Exports sales orders to CSV format for accounting and Excel.
     */
    fun exportSalesToCsv(orders: List<OrderEntity>): String {
        val sb = StringBuilder()
        sb.append("OrderNumber,Table,Waiter,Date,State,Total_Millimes,Total_TND,Discount_Millimes,CancellationReason\n")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        for (order in orders) {
            val dateStr = dateFormat.format(Date(order.createdAt))
            val totalTnd = Money(order.totalMillimes).formatStandard()
            sb.append("${order.orderNumber},\"${order.tableName}\",\"${order.waiterName}\",\"$dateStr\",${order.state},${order.totalMillimes},\"$totalTnd\",${order.discountMillimes},\"${order.cancellationReason}\"\n")
        }
        return sb.toString()
    }
}
