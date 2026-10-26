package com.example.core.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.example.core.data.dao.OrderWithLines
import com.example.core.data.entity.ShiftEntity
import com.example.core.model.Money
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ArabicReceiptRenderer {

    private const val PRINT_WIDTH_DOTS = 384 // Standard 58mm thermal printer (or 576 for 80mm)

    /**
     * Renders a Customer Receipt into a clean monochrome Bitmap.
     */
    fun renderCustomerReceipt(
        cafeName: String,
        orderWithLines: OrderWithLines,
        footerText: String = "شكراً لزيارتكم • Merci pour votre visite"
    ): Bitmap {
        val order = orderWithLines.order
        val lines = orderWithLines.lines
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date(order.createdAt))

        // Estimate canvas height based on line items
        val baseHeight = 450
        val itemsHeight = lines.size * 55 + orderWithLines.deltas.size * 45
        val totalHeight = baseHeight + itemsHeight

        val bitmap = Bitmap.createBitmap(PRINT_WIDTH_DOTS, totalHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        var y = 50f

        // Café Title
        paint.textSize = 32f
        canvas.drawText(cafeName, PRINT_WIDTH_DOTS / 2f, y, paint)
        y += 40f

        // Subtitle / Date
        paint.textSize = 20f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("تاريخ: $dateStr", PRINT_WIDTH_DOTS / 2f, y, paint)
        y += 35f

        // Order Info
        val tableInfo = if (order.isTakeaway) "طلبية سريعة (سفري)" else order.tableName
        canvas.drawText("طلب رقم #${order.orderNumber} | $tableInfo", PRINT_WIDTH_DOTS / 2f, y, paint)
        y += 25f

        // Divider
        canvas.drawLine(10f, y, (PRINT_WIDTH_DOTS - 10).toFloat(), y, paint)
        y += 30f

        // Table Header
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("المنتج", (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("الكمية", (PRINT_WIDTH_DOTS / 2).toFloat(), y, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("المبلغ", 20f, y, paint)
        y += 25f

        canvas.drawLine(10f, y, (PRINT_WIDTH_DOTS - 10).toFloat(), y, paint)
        y += 30f

        // Line Items
        paint.typeface = Typeface.DEFAULT
        for (item in lines) {
            val itemTotal = Money(item.unitPriceMillimes * item.quantity).formatArabic()
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(item.productNameAr, (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)

            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${item.quantity}x", (PRINT_WIDTH_DOTS / 2).toFloat(), y, paint)

            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(itemTotal, 20f, y, paint)
            y += 40f

            if (item.modifiersJson.isNotBlank() || item.notes.isNotBlank()) {
                val extra = listOf(item.modifiersJson, item.notes).filter { it.isNotBlank() }.joinToString(" • ")
                paint.textSize = 17f
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("  ($extra)", (PRINT_WIDTH_DOTS - 35).toFloat(), y, paint)
                paint.textSize = 20f
                y += 30f
            }
        }

        // Deltas if any
        for (delta in orderWithLines.deltas) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("تعديل: ${delta.description}", (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(Money(delta.deltaMillimes).formatArabic(), 20f, y, paint)
            y += 35f
        }

        // Divider
        canvas.drawLine(10f, y, (PRINT_WIDTH_DOTS - 10).toFloat(), y, paint)
        y += 35f

        // Totals
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 24f

        val totalFormatted = Money(order.totalMillimes).formatArabic()
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("المجموع النهائي:", (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(totalFormatted, 20f, y, paint)
        y += 40f

        if (order.receivedMillimes > 0) {
            paint.textSize = 20f
            paint.typeface = Typeface.DEFAULT
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("المستلم نقداً:", (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(Money(order.receivedMillimes).formatArabic(), 20f, y, paint)
            y += 30f

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("الصرف المتبقي:", (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(Money(order.changeMillimes).formatArabic(), 20f, y, paint)
            y += 35f
        }

        // Footer
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 18f
        canvas.drawText(footerText, PRINT_WIDTH_DOTS / 2f, y, paint)

        return bitmap
    }

    /**
     * Renders a Bar/Kitchen Preparation Ticket.
     */
    fun renderKitchenTicket(
        orderWithLines: OrderWithLines
    ): Bitmap {
        val order = orderWithLines.order
        val lines = orderWithLines.lines
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeStr = dateFormat.format(Date(order.createdAt))

        val baseHeight = 300
        val itemsHeight = lines.size * 65
        val totalHeight = baseHeight + itemsHeight

        val bitmap = Bitmap.createBitmap(PRINT_WIDTH_DOTS, totalHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        var y = 50f
        canvas.drawText("تذكرة تحضير (البار / المطبخ)", PRINT_WIDTH_DOTS / 2f, y, paint)
        y += 45f

        paint.textSize = 28f
        val tableInfo = if (order.isTakeaway) "سفري (Emporter)" else order.tableName
        canvas.drawText("طاولة: $tableInfo", PRINT_WIDTH_DOTS / 2f, y, paint)
        y += 40f

        paint.textSize = 22f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("طلب #${order.orderNumber} | النادل: ${order.waiterName} | $timeStr", PRINT_WIDTH_DOTS / 2f, y, paint)
        y += 25f

        canvas.drawLine(10f, y, (PRINT_WIDTH_DOTS - 10).toFloat(), y, paint)
        y += 40f

        for (item in lines) {
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 26f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${item.quantity}x  ${item.productNameAr}", (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
            y += 35f

            if (item.modifiersJson.isNotBlank() || item.notes.isNotBlank()) {
                val extra = listOf(item.modifiersJson, item.notes).filter { it.isNotBlank() }.joinToString(" • ")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                paint.textSize = 22f
                canvas.drawText("   ملاحظة: $extra", (PRINT_WIDTH_DOTS - 25).toFloat(), y, paint)
                y += 35f
            }
            y += 10f
        }

        return bitmap
    }

    /**
     * Renders a Test Print Receipt.
     */
    fun renderTestReceipt(cafeName: String): Bitmap {
        val bitmap = Bitmap.createBitmap(PRINT_WIDTH_DOTS, 360, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        var y = 50f
        canvas.drawText("وصل اختبار الطابعة الحرارية", PRINT_WIDTH_DOTS / 2f, y, paint)
        y += 40f
        paint.textSize = 24f
        canvas.drawText(cafeName, PRINT_WIDTH_DOTS / 2f, y, paint)
        y += 30f

        canvas.drawLine(10f, y, (PRINT_WIDTH_DOTS - 10).toFloat(), y, paint)
        y += 35f

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 20f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("✅ الاتصال بالطابعة: سليم 100%", (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
        y += 35f
        canvas.drawText("✅ دعم اللغة العربية: متطابق ومضبوط", (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
        y += 35f
        canvas.drawText("✅ العملة التونسية: 1.500 د.ت (مليم)", (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
        y += 35f
        canvas.drawText("✅ جاهز للاستخدام في المقهى", (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
        y += 40f

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 18f
        canvas.drawText("CaféFlow Pro — نظام المقاهي التونسية", PRINT_WIDTH_DOTS / 2f, y, paint)

        return bitmap
    }

    /**
     * Renders a Shift Closing Report.
     */
    fun renderShiftReport(shift: ShiftEntity, cafeName: String): Bitmap {
        val bitmap = Bitmap.createBitmap(PRINT_WIDTH_DOTS, 480, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        var y = 50f
        canvas.drawText("تقرير غلق الصندوق (Clôture)", PRINT_WIDTH_DOTS / 2f, y, paint)
        y += 40f
        paint.textSize = 22f
        canvas.drawText(cafeName, PRINT_WIDTH_DOTS / 2f, y, paint)
        y += 30f

        canvas.drawLine(10f, y, (PRINT_WIDTH_DOTS - 10).toFloat(), y, paint)
        y += 35f

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 20f
        paint.textAlign = Paint.Align.RIGHT

        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        val openStr = dateFormat.format(Date(shift.openedAt))
        val closeStr = shift.closedAt?.let { dateFormat.format(Date(it)) } ?: "مستمرة"

        canvas.drawText("المسؤول: ${shift.openedByName}", (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
        y += 30f
        canvas.drawText("وقت الفتح: $openStr", (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
        y += 30f
        canvas.drawText("وقت الغلق: $closeStr", (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
        y += 35f

        canvas.drawLine(10f, y, (PRINT_WIDTH_DOTS - 10).toFloat(), y, paint)
        y += 35f

        canvas.drawText("رصيد البداية (Fond de caisse):", (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(Money(shift.startingFloatMillimes).formatArabic(), 20f, y, paint)
        y += 35f

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("المبلغ المتوقع في الصندوق:", (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(Money(shift.expectedCashMillimes).formatArabic(), 20f, y, paint)
        y += 35f

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("المبلغ الفعلي المحسوب:", (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(Money(shift.actualCashMillimes).formatArabic(), 20f, y, paint)
        y += 35f

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        val diffLabel = if (shift.differenceMillimes >= 0) "الفارق (فائض):" else "الفارق (عجز):"
        canvas.drawText(diffLabel, (PRINT_WIDTH_DOTS - 20).toFloat(), y, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(Money(shift.differenceMillimes).formatArabic(), 20f, y, paint)

        return bitmap
    }

    /**
     * Converts a Bitmap into ESC/POS Raster bit-image commands (GS v 0).
     */
    fun bitmapToEscPosBytes(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8

        val stream = ByteArrayOutputStream()

        // Initialize printer: ESC @
        stream.write(byteArrayOf(0x1B, 0x40))

        // Center alignment: ESC a 1
        stream.write(byteArrayOf(0x1B, 0x61, 0x01))

        // GS v 0 m xL xH yL yH
        // m = 0 (normal)
        val xL = (widthBytes and 0xFF).toByte()
        val xH = ((widthBytes shr 8) and 0xFF).toByte()
        val yL = (height and 0xFF).toByte()
        val yH = ((height shr 8) and 0xFF).toByte()

        val header = byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH)
        stream.write(header)

        for (y in 0 until height) {
            for (xByte in 0 until widthBytes) {
                var slice: Byte = 0
                for (b in 0 until 8) {
                    val x = xByte * 8 + b
                    if (x < width) {
                        val pixel = bitmap.getPixel(x, y)
                        // Luminance formula to determine black vs white
                        val red = Color.red(pixel)
                        val green = Color.green(pixel)
                        val blue = Color.blue(pixel)
                        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()
                        if (luminance < 160) { // Black pixel = 1
                            slice = (slice.toInt() or (1 shl (7 - b))).toByte()
                        }
                    }
                }
                stream.write(slice.toInt())
            }
        }

        // Feed 4 lines: ESC d 4
        stream.write(byteArrayOf(0x1B, 0x64, 0x04))

        // Cut paper: GS V 66 0
        stream.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))

        return stream.toByteArray()
    }
}
