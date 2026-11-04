package org.sagisolas.recipehost.protocol;

import okio.Buffer;
import okio.ByteString;
import org.junit.Test;

import java.io.EOFException;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class EnvelopeFramingTest {

    @Test
    public void readsBackToBackFramesIndependently() throws Exception {
        Buffer buf = new Buffer();
        EnvelopeFraming.writeFrame(buf, authEnvelope());
        EnvelopeFraming.writeFrame(buf, doneEnvelope());

        assertEquals(authEnvelope(), EnvelopeFraming.readFrame(buf));
        assertEquals(doneEnvelope(), EnvelopeFraming.readFrame(buf));
        assertNull("stream must be exhausted after reading both frames", EnvelopeFraming.readFrame(buf));
    }

    @Test
    public void emptyStreamIsCleanEof() throws Exception {
        assertNull(EnvelopeFraming.readFrame(new Buffer()));
    }

    @Test
    public void streamEndingInsideBodyThrows() {
        ByteString framed = ByteString.decodeHex(AUTH_HEX);
        Buffer truncated = new Buffer().write(framed.substring(0, framed.size() - 4));
        assertThrows(EOFException.class, () -> EnvelopeFraming.readFrame(truncated));
    }

    @Test
    public void streamEndingInsideLengthPrefixThrows() {
        // d9 has the continuation bit set, so a second length byte must follow.
        Buffer source = new Buffer().write(ByteString.decodeHex("d9"));
        assertThrows(EOFException.class, () -> EnvelopeFraming.readFrame(source));
    }

    @Test
    public void negativeLengthPrefixIsIoError() {
        // ff ff ff ff 0f is varint 0xffffffff, which overflows int to -1.
        Buffer source = new Buffer().write(ByteString.decodeHex("ffffffff0f"));
        assertThrows(IOException.class, () -> EnvelopeFraming.readFrame(source));
    }

    // Golden vectors: the exact frame bytes for a few envelopes. The same hex
    // literals live in the Kotlin EnvelopeFramingTest (:shared commonTest), so
    // both sides are pinned to identical bytes. Keep the two copies in sync.

    // varint(2), then Envelope.done (field 5, LEN) with an empty body.
    private static final String DONE_HEX = "022a00";

    // varint(36), Envelope.auth (field 7, LEN 34), Auth.token (field 1, LEN 32) = bytes 0x00..0x1f.
    private static final String AUTH_HEX =
            "243a220a20"
            + "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";

    // varint(217) = d9 01 (two bytes), Envelope.item_batch (field 2, LEN 214), six Items.
    private static final String ITEM_BATCH_HEX =
            "d90112d601"
            + "0a180a0f6d696e6563726166743a73746f6e65120553746f6e65"
            + "0a1c0a116d696e6563726166743a6772616e69746512074772616e697465"
            + "0a2e0a1a6d696e6563726166743a706f6c69736865645f6772616e6974651210506f6c6973686564204772616e697465"
            + "0a1c0a116d696e6563726166743a64696f72697465120744696f72697465"
            + "0a2e0a1a6d696e6563726166743a706f6c69736865645f64696f726974651210506f6c69736865642044696f72697465"
            + "0a1e0a126d696e6563726166743a616e6465736974651208416e646573697465";

    private static Envelope doneEnvelope() {
        return new Envelope.Builder().done(new Done.Builder().build()).build();
    }

    private static Envelope authEnvelope() {
        byte[] token = new byte[32];
        for (int i = 0; i < 32; i++) token[i] = (byte) i;
        return new Envelope.Builder().auth(new Auth.Builder().token(ByteString.of(token)).build()).build();
    }

    private static Envelope itemBatchEnvelope() {
        return new Envelope.Builder().item_batch(new ItemBatch.Builder().items(List.of(
                item("minecraft:stone", "Stone"),
                item("minecraft:granite", "Granite"),
                item("minecraft:polished_granite", "Polished Granite"),
                item("minecraft:diorite", "Diorite"),
                item("minecraft:polished_diorite", "Polished Diorite"),
                item("minecraft:andesite", "Andesite"))).build()).build();
    }

    private static Item item(String id, String displayName) {
        return new Item.Builder().id(id).display_name(displayName).build();
    }

    private static void assertGolden(Envelope envelope, String hex) throws Exception {
        ByteString expected = ByteString.decodeHex(hex);

        Buffer written = new Buffer();
        EnvelopeFraming.writeFrame(written, envelope);
        assertEquals("encoded frame bytes", expected, written.readByteString());

        Buffer source = new Buffer().write(expected);
        assertEquals("decoded envelope", envelope, EnvelopeFraming.readFrame(source));
        assertNull("frame must consume exactly its bytes", EnvelopeFraming.readFrame(source));
    }

    @Test
    public void goldenDone() throws Exception {
        assertGolden(doneEnvelope(), DONE_HEX);
    }

    @Test
    public void goldenAuth() throws Exception {
        assertGolden(authEnvelope(), AUTH_HEX);
    }

    @Test
    public void goldenItemBatchWithTwoByteLength() throws Exception {
        assertGolden(itemBatchEnvelope(), ITEM_BATCH_HEX);
    }
}
