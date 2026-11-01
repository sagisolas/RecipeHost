package org.sagisolas.recipehost.mod.common.net;

import okio.ByteString;
import org.junit.Test;
import org.sagisolas.recipehost.protocol.Auth;
import org.sagisolas.recipehost.protocol.Done;
import org.sagisolas.recipehost.protocol.Envelope;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class EnvelopeFramingTest {

    @Test
    public void roundTripsSingleEnvelope() throws Exception {
        Envelope sent = new Envelope.Builder()
                .done(new Done.Builder().build())
                .build();

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        EnvelopeFraming.writeFrame(buf, sent);

        Envelope got = EnvelopeFraming.readFrame(new ByteArrayInputStream(buf.toByteArray()));
        assertEquals(sent, got);
    }

    @Test
    public void readsBackToBackFramesIndependently() throws Exception {
        byte[] tokenBytes = new byte[32];
        for (int i = 0; i < 32; i++) tokenBytes[i] = (byte) i;
        Envelope first = new Envelope.Builder()
                .auth(new Auth.Builder().token(ByteString.of(tokenBytes)).build())
                .build();
        Envelope second = new Envelope.Builder()
                .done(new Done.Builder().build())
                .build();

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        EnvelopeFraming.writeFrame(buf, first);
        EnvelopeFraming.writeFrame(buf, second);

        ByteArrayInputStream in = new ByteArrayInputStream(buf.toByteArray());
        assertEquals(first, EnvelopeFraming.readFrame(in));
        assertEquals(second, EnvelopeFraming.readFrame(in));
        assertNull("stream must be exhausted after reading both frames", EnvelopeFraming.readFrame(in));
    }

    @Test
    public void truncatedFrameThrowsEof() {
        byte[] tokenBytes = new byte[32];
        Envelope env = new Envelope.Builder()
                .auth(new Auth.Builder().token(ByteString.of(tokenBytes)).build())
                .build();

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            EnvelopeFraming.writeFrame(buf, env);
        } catch (Exception e) {
            fail("writeFrame threw: " + e);
        }

        byte[] framed = buf.toByteArray();
        byte[] truncated = new byte[framed.length - 4];
        System.arraycopy(framed, 0, truncated, 0, truncated.length);

        try {
            EnvelopeFraming.readFrame(new ByteArrayInputStream(truncated));
            fail("expected EOFException on truncated frame");
        } catch (EOFException expected) {
            // ok
        } catch (Exception e) {
            fail("expected EOFException, got " + e);
        }
    }

    @Test
    public void lengthPrefixIsVarint() throws Exception {
        // A Done envelope with its oneof set to field 5 is 2 bytes:
        //   tag=0x2a (field 5, wire type 2), length=0x00
        // We assert the frame prefix is varint(2) = 0x02, followed by those 2 bytes.
        Envelope env = new Envelope.Builder().done(new Done.Builder().build()).build();

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        EnvelopeFraming.writeFrame(buf, env);

        byte[] framed = buf.toByteArray();
        assertArrayEquals(new byte[] {0x02, 0x2a, 0x00}, framed);
    }
}
