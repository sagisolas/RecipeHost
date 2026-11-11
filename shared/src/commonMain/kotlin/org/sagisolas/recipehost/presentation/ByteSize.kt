package org.sagisolas.recipehost.presentation

import kotlin.math.pow
import kotlin.math.round

/** A byte count for display: [amount] in [unit], which the UI turns into a localized string. */
internal data class ByteSize(
    val amount: String,
    val unit: ByteUnit,
)

/** Decimal (SI) units, as Android and iOS show file sizes. */
internal enum class ByteUnit { Bytes, Kilobytes, Megabytes, Gigabytes }

/**
 * [bytes] in the largest unit it fills at least once: whole bytes, else one decimal below 10 and
 * whole numbers above (1.5 MB, 15 MB, 150 MB). Rounding up to 1000 moves to the next unit.
 */
internal fun byteSizeOf(bytes: Long): ByteSize {
    if (bytes < 1000) return ByteSize(bytes.coerceAtLeast(0).toString(), ByteUnit.Bytes)
    val units = ByteUnit.entries.drop(1)
    return units.withIndex().firstNotNullOf { (index, unit) ->
        val amount = amountOf(bytes / 1000.0.pow(index + 1))
        ByteSize(amount, unit).takeIf { amount.toDouble() < 1000 || unit == units.last() }
    }
}

private fun amountOf(value: Double): String {
    if (value >= 9.95) return round(value).toLong().toString()
    val tenths = round(value * 10).toLong()
    return "${tenths / 10}.${tenths % 10}"
}
