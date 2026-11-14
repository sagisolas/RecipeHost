/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.presentation

import kotlin.math.pow
import kotlin.math.round

/**
 * A byte count for display: [whole] and, when it shows one decimal, [tenths], in [unit]. The UI joins
 * them with the language's decimal separator and turns the result into a localized string.
 */
internal data class ByteSize(
    val whole: Long,
    val tenths: Int?,
    val unit: ByteUnit,
)

/** Decimal (SI) units, as Android and iOS show file sizes. */
internal enum class ByteUnit { Bytes, Kilobytes, Megabytes, Gigabytes }

/**
 * [bytes] in the largest unit it fills at least once: whole bytes, else one decimal below 10 and
 * whole numbers above (1.5 MB, 15 MB, 150 MB). Rounding up to 1000 moves to the next unit.
 */
internal fun byteSizeOf(bytes: Long): ByteSize {
    if (bytes < 1000) return ByteSize(bytes.coerceAtLeast(0), null, ByteUnit.Bytes)
    val units = ByteUnit.entries.drop(1)
    return units.withIndex().firstNotNullOf { (index, unit) ->
        sizeIn(bytes / 1000.0.pow(index + 1), unit).takeIf { it.whole < 1000 || unit == units.last() }
    }
}

private fun sizeIn(
    value: Double,
    unit: ByteUnit,
): ByteSize {
    if (value >= 9.95) return ByteSize(round(value).toLong(), null, unit)
    val tenths = round(value * 10).toLong()
    return ByteSize(tenths / 10, (tenths % 10).toInt(), unit)
}
