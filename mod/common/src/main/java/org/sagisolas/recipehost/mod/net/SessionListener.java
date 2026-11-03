package org.sagisolas.recipehost.mod.net;

import okio.ByteString;
import org.sagisolas.recipehost.protocol.Envelope;
import org.sagisolas.recipehost.protocol.EnvelopeFraming;
import org.sagisolas.recipehost.protocol.Error;
import org.sagisolas.recipehost.protocol.ErrorCode;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP listener that accepts a single authenticated {@code Session} at a time.
 * The first frame from the client must be an {@link org.sagisolas.recipehost.protocol.Auth}
 * envelope carrying the 256-bit token shown on the QR screen.
 *
 * <p>Concurrent connection attempts while a session is active receive an
 * {@code Error{BUSY}} and are closed. Mismatched tokens receive
 * {@code Error{UNAUTHORIZED}}.
 */
public final class SessionListener implements Closeable {

    private final ServerSocket serverSocket;
    private final ByteString expectedToken;
    private final SnapshotSource source;
    private final Runnable onSnapshotDelivered;
    private final Runnable onTerminated;
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private final AtomicBoolean sessionActive = new AtomicBoolean(false);
    private volatile Thread acceptThread;
    private volatile boolean closed = false;

    private SessionListener(ServerSocket serverSocket, ByteString expectedToken, SnapshotSource source,
                            Runnable onSnapshotDelivered, Runnable onTerminated) {
        this.serverSocket = serverSocket;
        this.expectedToken = expectedToken;
        this.source = source;
        this.onSnapshotDelivered = onSnapshotDelivered;
        this.onTerminated = onTerminated;
    }

    public static SessionListener bind(InetAddress address, int port, ByteString expectedToken, SnapshotSource source) throws IOException {
        return bind(address, port, expectedToken, source, () -> {}, () -> {});
    }

    /**
     * @param onSnapshotDelivered runs on the session thread once a full {@code Snapshot}
     *                            (through {@code Done}) has been written to an authenticated client.
     * @param onTerminated        runs exactly once, after {@link #close()} and once no session is
     *                            streaming any more — the point at which {@code source} may be released.
     */
    public static SessionListener bind(InetAddress address, int port, ByteString expectedToken, SnapshotSource source,
                                       Runnable onSnapshotDelivered, Runnable onTerminated) throws IOException {
        ServerSocket ss = new ServerSocket(port, 4, address);
        return new SessionListener(ss, expectedToken, source, onSnapshotDelivered, onTerminated);
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    public void start() {
        Thread t = new Thread(this::acceptLoop, "recipehost-session-accept");
        t.setDaemon(true);
        acceptThread = t;
        t.start();
    }

    private void acceptLoop() {
        while (!closed) {
            Socket client;
            try {
                client = serverSocket.accept();
            } catch (IOException e) {
                return;
            }

            if (!sessionActive.compareAndSet(false, true)) {
                sendErrorAndClose(client, ErrorCode.BUSY);
                continue;
            }
            if (closed) {
                // close() ran between accept() and the CAS above and saw no active session,
                // or will see ours: either way the source must not be streamed from now.
                sessionActive.set(false);
                try { client.close(); } catch (IOException ignored) {}
                terminateOnce();
                return;
            }

            Thread session = new Thread(() -> handleClient(client), "recipehost-session");
            session.setDaemon(true);
            session.start();
        }
    }

    private void handleClient(Socket client) {
        try {
            Envelope first = EnvelopeFraming.readFrame(client.getInputStream());
            if (first == null || first.auth == null || !expectedToken.equals(first.auth.token)) {
                writeError(client.getOutputStream(), ErrorCode.UNAUTHORIZED);
                return;
            }
            SnapshotSender.send(client.getOutputStream(), source);
            onSnapshotDelivered.run();
        } catch (IOException ignored) {
            // Client disconnected or socket error — nothing to recover.
        } finally {
            try { client.close(); } catch (IOException ignored) {}
            sessionActive.set(false);
            if (closed) terminateOnce();
        }
    }

    private static void sendErrorAndClose(Socket client, ErrorCode code) {
        try (Socket s = client) {
            writeError(s.getOutputStream(), code);
        } catch (IOException ignored) {}
    }

    private static void writeError(OutputStream out, ErrorCode code) throws IOException {
        Error err = new Error.Builder().code(code).build();
        EnvelopeFraming.writeFrame(out, new Envelope.Builder().error(err).build());
        out.flush();
    }

    @Override
    public void close() throws IOException {
        closed = true;
        try {
            serverSocket.close();
        } finally {
            Thread t = acceptThread;
            if (t != null) t.interrupt();
            if (!sessionActive.get()) terminateOnce();
        }
    }

    private void terminateOnce() {
        if (terminated.compareAndSet(false, true)) onTerminated.run();
    }
}
