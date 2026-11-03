package org.sagisolas.recipehost.mod.pairing;

import okio.ByteString;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Owns the listener lifecycle behind the QR screen (ADR 0001): bind on open with a
 * fresh {@code Token}; unbind on screen close + grace, on snapshot delivery, or on
 * world leave.
 */
public final class PairingController {

    static final Duration GRACE = Duration.ofSeconds(60);

    public interface Listener extends Closeable {
        int port();
    }

    public interface ListenerFactory {
        Listener bind(ByteString token, Runnable onSnapshotDelivered) throws IOException;
    }

    public interface Cancellable {
        void cancel();
    }

    public interface Scheduler {
        Cancellable schedule(Runnable task, Duration delay);
    }

    private final ListenerFactory factory;
    private final Scheduler scheduler;
    private final Supplier<ByteString> tokens;
    private final Supplier<List<InetAddress>> hosts;

    private Listener current;
    private Cancellable pendingUnbind;
    /** Bumped per {@link #open()}, so callbacks from an earlier pairing can be told apart. */
    private int generation;

    public PairingController(ListenerFactory factory, Scheduler scheduler,
                             Supplier<ByteString> tokens, Supplier<List<InetAddress>> hosts) {
        this.factory = factory;
        this.scheduler = scheduler;
        this.tokens = tokens;
        this.hosts = hosts;
    }

    public synchronized String open() throws IOException {
        unbind();
        ByteString token = tokens.get();
        int gen = ++generation;
        current = factory.bind(token, () -> unbindIfCurrent(gen));
        return PairingPayload.toUri(hosts.get(), current.port(), token);
    }

    public synchronized void screenClosed() {
        cancelPendingUnbind();
        int gen = generation;
        // Guarded by generation: cancel() can't stop a timer that's already running.
        pendingUnbind = scheduler.schedule(() -> unbindIfCurrent(gen), GRACE);
    }

    public synchronized void worldLeft() {
        unbind();
    }

    private synchronized void unbindIfCurrent(int gen) {
        if (gen == generation) unbind();
    }

    private synchronized void unbind() {
        cancelPendingUnbind();
        if (current == null) return;
        try { current.close(); } catch (IOException ignored) {}
        current = null;
    }

    private void cancelPendingUnbind() {
        if (pendingUnbind != null) {
            pendingUnbind.cancel();
            pendingUnbind = null;
        }
    }
}
