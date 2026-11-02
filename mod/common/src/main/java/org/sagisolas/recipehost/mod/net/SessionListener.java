package org.sagisolas.recipehost.mod.net;

import okio.ByteString;
import org.sagisolas.recipehost.protocol.Envelope;
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
    private final AtomicBoolean sessionActive = new AtomicBoolean(false);
    private volatile Thread acceptThread;
    private volatile boolean closed = false;

    private SessionListener(ServerSocket serverSocket, ByteString expectedToken, SnapshotSource source) {
        this.serverSocket = serverSocket;
        this.expectedToken = expectedToken;
        this.source = source;
    }

    public static SessionListener bind(InetAddress address, int port, ByteString expectedToken, SnapshotSource source) throws IOException {
        ServerSocket ss = new ServerSocket(port, 4, address);
        return new SessionListener(ss, expectedToken, source);
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
        } catch (IOException ignored) {
            // Client disconnected or socket error — nothing to recover.
        } finally {
            try { client.close(); } catch (IOException ignored) {}
            sessionActive.set(false);
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
        serverSocket.close();
        Thread t = acceptThread;
        if (t != null) t.interrupt();
    }
}
