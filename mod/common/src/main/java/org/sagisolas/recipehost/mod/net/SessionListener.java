/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.net;

import com.google.protobuf.ByteString;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.sagisolas.recipehost.protocol.Envelope;
import org.sagisolas.recipehost.protocol.EnvelopeFraming;
import org.sagisolas.recipehost.protocol.Error;
import org.sagisolas.recipehost.protocol.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP listener that serves the {@code Snapshot} to authenticated clients. The first frame from the
 * client must be an {@link org.sagisolas.recipehost.protocol.Auth} envelope carrying the 256-bit
 * token shown on the QR screen; a mismatched token receives {@code Error{UNAUTHORIZED}}.
 *
 * <p>One client is served at a time, and the newest authenticated client wins. A connection that
 * authenticates while another session is still streaming replaces it: the older socket is closed,
 * and its stream ends at its next write. A reconnect after a drop the mod has not noticed yet (a
 * half-open socket, or a session blocked in the source) is therefore served, not refused. A
 * connection that has not authenticated holds nothing.
 */
public final class SessionListener implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(SessionListener.class);

    /** How long a new connection may take to send its {@code Auth} frame. */
    private static final int AUTH_TIMEOUT_MILLIS = 5_000;

    /** Connections that may wait for their {@code Auth} at once; more are closed on accept. */
    private static final int MAX_PENDING_AUTH = 8;

    /** Bytes an {@code Auth} frame may take: its 32-byte token plus framing, with room to spare. */
    private static final int MAX_AUTH_FRAME_BYTES = 256;

    private final ServerSocket serverSocket;
    private final ByteString expectedToken;
    private final SnapshotSource source;
    private final Runnable onSnapshotDelivered;
    private final Runnable onTerminated;
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    /** Bounds the threads and memory that connections can take before they authenticate. */
    private final Semaphore pendingAuth = new Semaphore(MAX_PENDING_AUTH);
    /** Guards {@link #current}, {@link #streaming} and the {@link #closed} check before a session starts. */
    private final Object lock = new Object();
    /** The newest authenticated client's socket, or null. */
    private Socket current;
    /** Sessions still pulling from {@link #source}, replaced ones included. */
    private int streaming;

    private volatile Thread acceptThread;
    private volatile boolean closed = false;

    private SessionListener(
            ServerSocket serverSocket,
            ByteString expectedToken,
            SnapshotSource source,
            Runnable onSnapshotDelivered,
            Runnable onTerminated) {
        this.serverSocket = serverSocket;
        this.expectedToken = expectedToken;
        this.source = source;
        this.onSnapshotDelivered = onSnapshotDelivered;
        this.onTerminated = onTerminated;
    }

    public static SessionListener bind(InetAddress address, int port, ByteString expectedToken, SnapshotSource source)
            throws IOException {
        return bind(address, port, expectedToken, source, () -> {}, () -> {});
    }

    /**
     * @param onSnapshotDelivered runs on the session thread once a full {@code Snapshot}
     *                            (through {@code Done}) has been written to an authenticated client.
     * @param onTerminated        runs exactly once, after {@link #close()} and once no session is
     *                            streaming any more — the point at which {@code source} may be released.
     */
    public static SessionListener bind(
            InetAddress address,
            int port,
            ByteString expectedToken,
            SnapshotSource source,
            Runnable onSnapshotDelivered,
            Runnable onTerminated)
            throws IOException {
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
            if (!pendingAuth.tryAcquire()) {
                closeQuietly(client);
                continue;
            }
            Thread session = new Thread(() -> handleClient(client), "recipehost-session");
            session.setDaemon(true);
            session.start();
        }
    }

    private void handleClient(Socket client) {
        try (client) {
            Envelope first;
            try {
                client.setSoTimeout(AUTH_TIMEOUT_MILLIS);
                first = EnvelopeFraming.readFrame(
                        new BufferedInputStream(new LimitedInputStream(client.getInputStream(), MAX_AUTH_FRAME_BYTES)));
            } finally {
                pendingAuth.release();
            }
            OutputStream out = new BufferedOutputStream(client.getOutputStream());
            if (first == null
                    || !first.hasAuth()
                    || !expectedToken.equals(first.getAuth().getToken())) {
                writeError(out, ErrorCode.UNAUTHORIZED);
                return;
            }
            client.setSoTimeout(0);
            if (!beginSession(client)) return;
            try {
                SnapshotSender.send(out, source);
                onSnapshotDelivered.run();
            } catch (IOException e) {
                logStreamEnd(client, e);
            } finally {
                endSession(client);
            }
        } catch (IOException ignored) {
            // Disconnected or timed out before authenticating: nothing was started.
        }
    }

    /** Makes {@code client} the current session, closing the one it replaces. False once closed. */
    private boolean beginSession(Socket client) {
        Socket replaced;
        synchronized (lock) {
            if (closed) return false;
            replaced = current;
            current = client;
            streaming++;
        }
        // Its stream ends at its next write, and logStreamEnd says it was replaced.
        if (replaced != null) closeQuietly(replaced);
        return true;
    }

    private void endSession(Socket client) {
        boolean last;
        synchronized (lock) {
            if (current == client) current = null;
            streaming--;
            last = closed && streaming == 0;
        }
        if (last) terminateOnce();
    }

    private void logStreamEnd(Socket client, IOException e) {
        if (!(e instanceof SnapshotSender.ClientGone)) return; // source failure: SnapshotSender logged it
        boolean replaced;
        synchronized (lock) {
            replaced = current != client;
        }
        String why = replaced ? "replaced by a newer connection" : "client disconnected";
        LOG.info("[recipehost] Snapshot to {} ended, {}: {}", client.getRemoteSocketAddress(), why, e.getMessage());
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static void writeError(OutputStream out, ErrorCode code) throws IOException {
        Error err = Error.newBuilder().setCode(code).build();
        EnvelopeFraming.writeFrame(out, Envelope.newBuilder().setError(err).build());
        out.flush();
    }

    @Override
    public void close() throws IOException {
        boolean idle;
        synchronized (lock) {
            closed = true;
            idle = streaming == 0;
        }
        try {
            serverSocket.close();
        } finally {
            Thread t = acceptThread;
            if (t != null) t.interrupt();
            if (idle) terminateOnce();
        }
    }

    private void terminateOnce() {
        if (terminated.compareAndSet(false, true)) onTerminated.run();
    }

    /** Ends the stream after {@code limit} bytes, so a frame can't claim more than that. */
    private static final class LimitedInputStream extends FilterInputStream {
        private int remaining;

        LimitedInputStream(InputStream in, int limit) {
            super(in);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = super.read();
            if (b >= 0) remaining--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int n = super.read(b, off, Math.min(len, remaining));
            if (n > 0) remaining -= n;
            return n;
        }
    }
}
