package com.example.core.model

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Value class representing money in Tunisian Millimes (1 Dinar = 1000 Millimes).
 * All financial arithmetic is strictly performed on 64-bit Long integers to prevent
 * floating-point inaccuracy.
 */
@JvmInline
value class Money(val millimes: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(millimes + other.millimes)

    operator fun minus(other: Money): Money = Money(millimes - other.millimes)

    operator fun times(factor: Int): Money = Money(millimes * factor)

    operator fun times(factor: Long): Money = Money(millimes * factor)

    operator fun div(divisor: Int): Money {
        require(divisor != 0) { "Divisor cannot be zero" }
        return Money(millimes / divisor)
    }

    operator fun unaryMinus(): Money = Money(-millimes)

    override fun compareTo(other: Money): Int = millimes.compareTo(other.millimes)

    /**
     * Calculates percentage discount: amount * (percentage / 100) rounded to nearest millime.
     */
    fun percentage(percent: Int): Money {
        require(percent in 0..100) { "Percentage must be between 0 and 100" }
        val discounted = (millimes * percent + 50) / 100
        return Money(discounted)
    }

    /**
     * Returns a formatted Arabic string, e.g. "1.500 د.ت" or "0.800 د.ت"
     */
    fun formatArabic(): String {
        val dinars = millimes / 1000
        val remainingMillimes = kotlin.math.abs(millimes % 1000)
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            decimalSeparator = '.'
        }
        val millimesFormatted = DecimalFormat("000", symbols).format(remainingMillimes)
        return "$dinars.$millimesFormatted د.ت"
    }

    /**
     * Returns a standard formatted string, e.g. "1.500 TND"
     */
    fun formatStandard(): String {
        val dinars = millimes / 1000
        val remainingMillimes = kotlin.math.abs(millimes % 1000)
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            decimalSeparator = '.'
        }
        val millimesFormatted = DecimalFormat("000", symbols).format(remainingMillimes)
        return "$dinars.$millimesFormatted TND"
    }

    override fun toString(): String = formatArabic()

    companion object {
        val ZERO = Money(0L)

        fun fromDinars(dinars: Long, millimes: Long = 0L): Money {
            return Money((dinars * 1000L) + millimes)
        }

        fun fromDinarsString(dinarsString: String): Money {
            val cleaned = dinarsString.trim()
                .replace("د.ت", "")
                .replace("TND", "")
                .trim()
            if (cleaned.isEmpty()) return ZERO

            return try {
                if (cleaned.contains(".")) {
                    val parts = cleaned.split(".")
                    val dinars = parts[0].toLongOrNull() ?: 0L
                    val millimesRaw = parts.getOrNull(1)?.padEnd(3, '0')?.take(3) ?: "000"
                    val millimes = millimesRaw.toLongOrNull() ?: 0L
                    Money((dinars * 1000L) + millimes)
                } else {
                    val dinars = cleaned.toLongOrNull() ?: 0L
                    Money(dinars * 1000L)
                }
            } catch (e: Exception) {
                ZERO
            }
        }
    }
}
