package org.sagisolas.recipehost.data.transport

import okio.Buffer
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import okio.EOFException
import okio.IOException
import org.sagisolas.recipehost.protocol.Auth
import org.sagisolas.recipehost.protocol.Done
import org.sagisolas.recipehost.protocol.Envelope
import org.sagisolas.recipehost.protocol.Item
import org.sagisolas.recipehost.protocol.ItemBatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EnvelopeFramingTest {
    // Golden vectors: the exact frame bytes for a few envelopes. The same hex
    // literals live in the Java EnvelopeFramingTest (:protocol), so both sides
    // are pinned to identical bytes. Keep the two copies in sync.

    // varint(2), then Envelope.done (field 5, LEN) with an empty body.
    private val doneHex = "022a00"

    // varint(36), Envelope.auth (field 7, LEN 34), Auth.token (field 1, LEN 32) = bytes 0x00..0x1f.
    private val authHex =
        "243a220a20" +
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"

    // varint(217) = d9 01 (two bytes), Envelope.item_batch (field 2, LEN 214), six Items.
    private val itemBatchHex =
        "d90112d601" +
            "0a180a0f6d696e6563726166743a73746f6e65120553746f6e65" +
            "0a1c0a116d696e6563726166743a6772616e69746512074772616e697465" +
            "0a2e0a1a6d696e6563726166743a706f6c69736865645f6772616e6974651210506f6c6973686564204772616e697465" +
            "0a1c0a116d696e6563726166743a64696f72697465120744696f72697465" +
            "0a2e0a1a6d696e6563726166743a706f6c69736865645f64696f726974651210506f6c69736865642044696f72697465" +
            "0a1e0a126d696e6563726166743a616e6465736974651208416e646573697465"

    private val doneEnvelope = Envelope(done = Done())

    private val authEnvelope = Envelope(auth = Auth(token = ByteArray(32) { it.toByte() }.toByteString()))

    private val itemBatchEnvelope =
        Envelope(
            item_batch =
                ItemBatch(
                    items =
                        listOf(
                            Item(id = "minecraft:stone", display_name = "Stone"),
                            Item(id = "minecraft:granite", display_name = "Granite"),
                            Item(id = "minecraft:polished_granite", display_name = "Polished Granite"),
                            Item(id = "minecraft:diorite", display_name = "Diorite"),
                            Item(id = "minecraft:polished_diorite", display_name = "Polished Diorite"),
                            Item(id = "minecraft:andesite", display_name = "Andesite"),
                        ),
                ),
        )

    private fun assertGolden(
        envelope: Envelope,
        hex: String,
    ) {
        val expected = hex.decodeHex()

        val written = Buffer()
        EnvelopeFraming.write(written, envelope)
        assertEquals(expected, written.readByteString(), "encoded frame bytes")

        val source = Buffer().write(expected)
        assertEquals(envelope, EnvelopeFraming.read(source), "decoded envelope")
        assertNull(EnvelopeFraming.read(source), "frame must consume exactly its bytes")
    }

    @Test
    fun goldenDone() {
        assertGolden(doneEnvelope, doneHex)
    }

    @Test
    fun goldenAuth() {
        assertGolden(authEnvelope, authHex)
    }

    @Test
    fun goldenItemBatchWithTwoByteLength() {
        assertGolden(itemBatchEnvelope, itemBatchHex)
    }

    @Test
    fun emptyStreamIsCleanEof() {
        assertNull(EnvelopeFraming.read(Buffer()))
    }

    @Test
    fun streamEndingInsideBodyThrows() {
        val truncated = authHex.decodeHex().let { it.substring(0, it.size - 4) }
        assertFailsWith<EOFException> { EnvelopeFraming.read(Buffer().write(truncated)) }
    }

    @Test
    fun streamEndingInsideLengthPrefixThrows() {
        // d9 has the continuation bit set, so a second length byte must follow.
        assertFailsWith<EOFException> { EnvelopeFraming.read(Buffer().write("d9".decodeHex())) }
    }

    @Test
    fun negativeLengthPrefixIsIoError() {
        // ff ff ff ff 0f is varint 0xffffffff, which overflows Int to -1.
        assertFailsWith<IOException> { EnvelopeFraming.read(Buffer().write("ffffffff0f".decodeHex())) }
    }
}
