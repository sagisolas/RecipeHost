package org.sagisolas.recipehost.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteSizeTest {
    @Test
    fun underAKilobyteIsWholeBytes() {
        assertEquals(ByteSize("0", ByteUnit.Bytes), byteSizeOf(0))
        assertEquals(ByteSize("999", ByteUnit.Bytes), byteSizeOf(999))
    }

    @Test
    fun belowTenShowsOneDecimalAndAboveIsWhole() {
        assertEquals(ByteSize("1.0", ByteUnit.Kilobytes), byteSizeOf(1_000))
        assertEquals(ByteSize("1.5", ByteUnit.Megabytes), byteSizeOf(1_540_000))
        assertEquals(ByteSize("15", ByteUnit.Megabytes), byteSizeOf(15_400_000))
        assertEquals(ByteSize("150", ByteUnit.Megabytes), byteSizeOf(150_400_000))
    }

    @Test
    fun roundingUpTo1000MovesToTheNextUnit() {
        assertEquals(ByteSize("10", ByteUnit.Kilobytes), byteSizeOf(9_960))
        assertEquals(ByteSize("1.0", ByteUnit.Megabytes), byteSizeOf(999_600))
    }

    @Test
    fun gigabytesIsTheLargestUnit() {
        assertEquals(ByteSize("2.5", ByteUnit.Gigabytes), byteSizeOf(2_500_000_000))
        assertEquals(ByteSize("5000", ByteUnit.Gigabytes), byteSizeOf(5_000_000_000_000))
    }
}
