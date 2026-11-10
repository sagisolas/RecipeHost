package org.sagisolas.recipehost.mod.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.protobuf.ByteString;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Test;
import org.sagisolas.recipehost.protocol.Auth;
import org.sagisolas.recipehost.protocol.Envelope;
import org.sagisolas.recipehost.protocol.EnvelopeFraming;
import org.sagisolas.recipehost.protocol.ErrorCode;
import org.sagisolas.recipehost.protocol.Handshake;

public class SessionListenerTest {

    private SessionListener listener;

    @After
    public void tearDown() throws IOException {
        if (listener != null) listener.close();
    }

    @Test
    public void validTokenReceivesFullSnapshot() throws Exception {
        byte[] token = tokenOf(1);
        Handshake handshake = Handshake.newBuilder()
                .setProtocolVersion(1)
                .setMinecraftVersion("1.21.1")
                .build();
        EmptySource source = new EmptySource(handshake);

        listener = SessionListener.bind(InetAddress.getLoopbackAddress(), 0, ByteString.copyFrom(token), source);
        listener.start();

        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), listener.port())) {
            writeFrame(
                    s,
                    Envelope.newBuilder()
                            .setAuth(Auth.newBuilder()
                                    .setToken(ByteString.copyFrom(token))
                                    .build())
                            .build());

            List<Envelope> frames = readAll(s);
            assertEquals(handshake, frames.get(0).getHandshake());
            assertTrue("final frame must be Done", frames.get(frames.size() - 1).hasDone());
        }

        assertTrue("source must be consumed exactly once", source.consumed.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void wrongTokenGetsErrorFrameAndNoSnapshot() throws Exception {
        byte[] expected = tokenOf(2);
        byte[] wrong = tokenOf(3);
        EmptySource source =
                new EmptySource(Handshake.newBuilder().setProtocolVersion(1).build());

        listener = SessionListener.bind(InetAddress.getLoopbackAddress(), 0, ByteString.copyFrom(expected), source);
        listener.start();

        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), listener.port())) {
            writeFrame(
                    s,
                    Envelope.newBuilder()
                            .setAuth(Auth.newBuilder()
                                    .setToken(ByteString.copyFrom(wrong))
                                    .build())
                            .build());

            List<Envelope> frames = readAll(s);
            assertEquals("only an Error frame expected", 1, frames.size());
            assertTrue(frames.get(0).hasError());
            assertEquals(ErrorCode.UNAUTHORIZED, frames.get(0).getError().getCode());
        }

        // Source must never have been consumed — handshake() was never called.
        assertEquals(0, source.handshakeCalls.get());
    }

    @Test
    public void secondConcurrentConnectionIsRefused() throws Exception {
        byte[] token = tokenOf(4);
        BlockingSource source =
                new BlockingSource(Handshake.newBuilder().setProtocolVersion(1).build());

        listener = SessionListener.bind(InetAddress.getLoopbackAddress(), 0, ByteString.copyFrom(token), source);
        listener.start();

        // First connection — authenticates, blocks inside the source so the session stays open.
        Socket first = new Socket(InetAddress.getLoopbackAddress(), listener.port());
        writeFrame(
                first,
                Envelope.newBuilder()
                        .setAuth(Auth.newBuilder()
                                .setToken(ByteString.copyFrom(token))
                                .build())
                        .build());
        assertTrue("first session must reach the source", source.entered.await(2, TimeUnit.SECONDS));

        // Second connection — should be refused with an Error, not hang.
        try (Socket second = new Socket(InetAddress.getLoopbackAddress(), listener.port())) {
            second.setSoTimeout(2000);
            List<Envelope> frames = readAll(second);
            assertEquals(1, frames.size());
            assertTrue(frames.get(0).hasError());
            assertEquals(ErrorCode.BUSY, frames.get(0).getError().getCode());
        }

        source.release.countDown();
        first.close();
    }

    @Test
    public void deliveredSnapshotFiresOnSnapshotDelivered() throws Exception {
        byte[] token = tokenOf(5);
        EmptySource source =
                new EmptySource(Handshake.newBuilder().setProtocolVersion(1).build());
        CountDownLatch delivered = new CountDownLatch(1);

        listener = SessionListener.bind(
                InetAddress.getLoopbackAddress(),
                0,
                ByteString.copyFrom(token),
                source,
                delivered::countDown,
                () -> {});
        listener.start();

        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), listener.port())) {
            writeFrame(
                    s,
                    Envelope.newBuilder()
                            .setAuth(Auth.newBuilder()
                                    .setToken(ByteString.copyFrom(token))
                                    .build())
                            .build());
            readAll(s);
        }

        assertTrue("onSnapshotDelivered must fire after Done", delivered.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void wrongTokenDoesNotFireOnSnapshotDelivered() throws Exception {
        AtomicInteger delivered = new AtomicInteger();
        EmptySource source =
                new EmptySource(Handshake.newBuilder().setProtocolVersion(1).build());

        listener = SessionListener.bind(
                InetAddress.getLoopbackAddress(),
                0,
                ByteString.copyFrom(tokenOf(6)),
                source,
                delivered::incrementAndGet,
                () -> {});
        listener.start();

        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), listener.port())) {
            writeFrame(
                    s,
                    Envelope.newBuilder()
                            .setAuth(Auth.newBuilder()
                                    .setToken(ByteString.copyFrom(tokenOf(7)))
                                    .build())
                            .build());
            readAll(s); // EOF ⇒ server's handleClient has finished
        }

        assertEquals(0, delivered.get());
    }

    @Test
    public void midStreamSourceFailureDoesNotFireOnSnapshotDelivered() throws Exception {
        byte[] token = tokenOf(8);
        AtomicInteger delivered = new AtomicInteger();
        Handshake handshake = Handshake.newBuilder().setProtocolVersion(1).build();
        SnapshotSource failing =
                () -> List.of(Envelope.newBuilder().setHandshake(handshake).build(), (Envelope) null).stream()
                        .map(frame -> {
                            if (frame == null) throw new IllegalStateException("boom");
                            return frame;
                        })
                        .iterator();

        listener = SessionListener.bind(
                InetAddress.getLoopbackAddress(),
                0,
                ByteString.copyFrom(token),
                failing,
                delivered::incrementAndGet,
                () -> {});
        listener.start();

        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), listener.port())) {
            writeFrame(
                    s,
                    Envelope.newBuilder()
                            .setAuth(Auth.newBuilder()
                                    .setToken(ByteString.copyFrom(token))
                                    .build())
                            .build());
            List<Envelope> frames = readAll(s);
            assertTrue(
                    "stream must end with Error", frames.get(frames.size() - 1).hasError());
        }

        assertEquals(0, delivered.get());
    }

    @Test
    public void closeWithoutActiveSessionTerminatesImmediately() throws Exception {
        AtomicInteger terminated = new AtomicInteger();
        listener = SessionListener.bind(
                InetAddress.getLoopbackAddress(),
                0,
                ByteString.copyFrom(tokenOf(9)),
                new EmptySource(Handshake.newBuilder().build()),
                () -> {},
                terminated::incrementAndGet);
        listener.start();

        listener.close();
        listener.close();

        assertEquals("terminated exactly once", 1, terminated.get());
    }

    @Test
    public void closeDuringActiveSessionTerminatesOnlyAfterSessionFinishes() throws Exception {
        byte[] token = tokenOf(10);
        BlockingSource source =
                new BlockingSource(Handshake.newBuilder().setProtocolVersion(1).build());
        CountDownLatch terminated = new CountDownLatch(1);

        listener = SessionListener.bind(
                InetAddress.getLoopbackAddress(),
                0,
                ByteString.copyFrom(token),
                source,
                () -> {},
                terminated::countDown);
        listener.start();

        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), listener.port())) {
            writeFrame(
                    s,
                    Envelope.newBuilder()
                            .setAuth(Auth.newBuilder()
                                    .setToken(ByteString.copyFrom(token))
                                    .build())
                            .build());
            assertTrue(source.entered.await(2, TimeUnit.SECONDS));

            listener.close();
            assertEquals("session still streaming — source must stay open", 1, terminated.getCount());

            source.release.countDown();
            readAll(s);
        }

        assertTrue("terminated once the session finished", terminated.await(2, TimeUnit.SECONDS));
    }

    private static byte[] tokenOf(int seed) {
        byte[] b = new byte[32];
        for (int i = 0; i < 32; i++) b[i] = (byte) (seed + i);
        return b;
    }

    private static void writeFrame(Socket s, Envelope envelope) throws IOException {
        OutputStream out = new BufferedOutputStream(s.getOutputStream());
        EnvelopeFraming.writeFrame(out, envelope);
        out.flush();
    }

    private static List<Envelope> readAll(Socket s) throws IOException {
        List<Envelope> frames = new ArrayList<>();
        Envelope e;
        try {
            InputStream in = new BufferedInputStream(s.getInputStream());
            while ((e = EnvelopeFraming.readFrame(in)) != null) {
                frames.add(e);
            }
        } catch (IOException ignored) {
            // socket closed by peer — ok.
        }
        return frames;
    }

    private static class EmptySource implements SnapshotSource {
        final Handshake handshake;
        final CountDownLatch consumed = new CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger handshakeCalls =
                new java.util.concurrent.atomic.AtomicInteger(0);

        EmptySource(Handshake h) {
            this.handshake = h;
        }

        @Override
        public Iterator<Envelope> frames() {
            return List.of(handshake).stream()
                    .map(h -> {
                        handshakeCalls.incrementAndGet();
                        consumed.countDown();
                        return Envelope.newBuilder().setHandshake(h).build();
                    })
                    .iterator();
        }
    }

    private static final class BlockingSource implements SnapshotSource {
        final Handshake handshake;
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        BlockingSource(Handshake h) {
            this.handshake = h;
        }

        @Override
        public Iterator<Envelope> frames() {
            return List.of(handshake).stream()
                    .map(h -> {
                        entered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        return Envelope.newBuilder().setHandshake(h).build();
                    })
                    .iterator();
        }
    }
}
