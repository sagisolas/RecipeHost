package org.sagisolas.recipehost.mod.common.net;

import okio.ByteString;
import org.junit.After;
import org.junit.Test;
import org.sagisolas.recipehost.protocol.Auth;
import org.sagisolas.recipehost.protocol.Envelope;
import org.sagisolas.recipehost.protocol.ErrorCode;
import org.sagisolas.recipehost.protocol.Handshake;
import org.sagisolas.recipehost.protocol.IconBatch;
import org.sagisolas.recipehost.protocol.ItemBatch;
import org.sagisolas.recipehost.protocol.RecipeBatch;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SessionListenerTest {

    private SessionListener listener;

    @After
    public void tearDown() throws IOException {
        if (listener != null) listener.close();
    }

    @Test
    public void validTokenReceivesFullSnapshot() throws Exception {
        byte[] token = tokenOf(1);
        Handshake handshake = new Handshake.Builder().protocol_version(1).minecraft_version("1.21.1").build();
        EmptySource source = new EmptySource(handshake);

        listener = SessionListener.bind(InetAddress.getLoopbackAddress(), 0, ByteString.of(token), source);
        listener.start();

        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), listener.port())) {
            EnvelopeFraming.writeFrame(s.getOutputStream(),
                    new Envelope.Builder().auth(new Auth.Builder().token(ByteString.of(token)).build()).build());
            s.getOutputStream().flush();

            List<Envelope> frames = readAll(s);
            assertEquals(handshake, frames.get(0).handshake);
            assertNotNull("final frame must be Done", frames.get(frames.size() - 1).done);
        }

        assertTrue("source must be consumed exactly once", source.consumed.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void wrongTokenGetsErrorFrameAndNoSnapshot() throws Exception {
        byte[] expected = tokenOf(2);
        byte[] wrong = tokenOf(3);
        EmptySource source = new EmptySource(new Handshake.Builder().protocol_version(1).build());

        listener = SessionListener.bind(InetAddress.getLoopbackAddress(), 0, ByteString.of(expected), source);
        listener.start();

        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), listener.port())) {
            EnvelopeFraming.writeFrame(s.getOutputStream(),
                    new Envelope.Builder().auth(new Auth.Builder().token(ByteString.of(wrong)).build()).build());
            s.getOutputStream().flush();

            List<Envelope> frames = readAll(s);
            assertEquals("only an Error frame expected", 1, frames.size());
            assertNotNull(frames.get(0).error);
            assertEquals(ErrorCode.UNAUTHORIZED, frames.get(0).error.code);
        }

        // Source must never have been consumed — handshake() was never called.
        assertEquals(0, source.handshakeCalls.get());
    }

    @Test
    public void secondConcurrentConnectionIsRefused() throws Exception {
        byte[] token = tokenOf(4);
        BlockingSource source = new BlockingSource(new Handshake.Builder().protocol_version(1).build());

        listener = SessionListener.bind(InetAddress.getLoopbackAddress(), 0, ByteString.of(token), source);
        listener.start();

        // First connection — authenticates, blocks inside the source so the session stays open.
        Socket first = new Socket(InetAddress.getLoopbackAddress(), listener.port());
        EnvelopeFraming.writeFrame(first.getOutputStream(),
                new Envelope.Builder().auth(new Auth.Builder().token(ByteString.of(token)).build()).build());
        first.getOutputStream().flush();
        assertTrue("first session must reach the source", source.entered.await(2, TimeUnit.SECONDS));

        // Second connection — should be refused with an Error, not hang.
        try (Socket second = new Socket(InetAddress.getLoopbackAddress(), listener.port())) {
            second.setSoTimeout(2000);
            List<Envelope> frames = readAll(second);
            assertEquals(1, frames.size());
            assertNotNull(frames.get(0).error);
            assertEquals(ErrorCode.BUSY, frames.get(0).error.code);
        }

        source.release.countDown();
        first.close();
    }

    private static byte[] tokenOf(int seed) {
        byte[] b = new byte[32];
        for (int i = 0; i < 32; i++) b[i] = (byte) (seed + i);
        return b;
    }

    private static List<Envelope> readAll(Socket s) throws IOException {
        List<Envelope> frames = new ArrayList<>();
        Envelope e;
        try {
            while ((e = EnvelopeFraming.readFrame(s.getInputStream())) != null) {
                frames.add(e);
            }
        } catch (IOException ignored) {
            // socket closed by peer — ok.
        }
        return frames;
    }

    private static final class EmptySource implements SnapshotSource {
        final Handshake handshake;
        final CountDownLatch consumed = new CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger handshakeCalls = new java.util.concurrent.atomic.AtomicInteger(0);
        EmptySource(Handshake h) { this.handshake = h; }
        @Override public Handshake handshake() {
            handshakeCalls.incrementAndGet();
            consumed.countDown();
            return handshake;
        }
        @Override public Iterator<ItemBatch> itemBatches() { return Collections.<ItemBatch>emptyIterator(); }
        @Override public Iterator<RecipeBatch> recipeBatches() { return Collections.<RecipeBatch>emptyIterator(); }
        @Override public Iterator<IconBatch> iconBatches() { return Collections.<IconBatch>emptyIterator(); }
    }

    private static final class BlockingSource implements SnapshotSource {
        final Handshake handshake;
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        BlockingSource(Handshake h) { this.handshake = h; }
        @Override public Handshake handshake() {
            entered.countDown();
            try { release.await(); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            return handshake;
        }
        @Override public Iterator<ItemBatch> itemBatches() { return Collections.<ItemBatch>emptyIterator(); }
        @Override public Iterator<RecipeBatch> recipeBatches() { return Collections.<RecipeBatch>emptyIterator(); }
        @Override public Iterator<IconBatch> iconBatches() { return Collections.<IconBatch>emptyIterator(); }
    }
}
