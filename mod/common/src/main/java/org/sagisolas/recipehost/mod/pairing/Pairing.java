package org.sagisolas.recipehost.mod.pairing;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import okio.ByteString;
import org.sagisolas.recipehost.mod.net.SessionListener;
import org.sagisolas.recipehost.mod.snapshot.MinecraftSnapshotSource;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production wiring of {@link PairingController} against Minecraft and real sockets.
 * Thin adapters only — the lifecycle logic lives (and is tested) in the controller.
 */
public final class Pairing {

    private static final Logger LOG = LogUtils.getLogger();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "recipehost-pairing-timer");
        t.setDaemon(true);
        return t;
    });

    private static final PairingController CONTROLLER = new PairingController(
            Pairing::bindListener,
            (task, delay) -> {
                var future = TIMER.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
                return () -> future.cancel(false);
            },
            Pairing::newToken,
            Pairing::hostAddresses);

    private Pairing() {}

    /** Binds a fresh listener and returns the pairing payload URI for the QR screen. */
    public static String open() throws IOException {
        String uri = CONTROLLER.open();
        LOG.info("[recipehost] pairing open: {}", uri);
        return uri;
    }

    public static void screenClosed() {
        CONTROLLER.screenClosed();
    }

    public static void worldLeft() {
        CONTROLLER.worldLeft();
    }

    private static PairingController.Listener bindListener(ByteString token, Runnable onSnapshotDelivered) throws IOException {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) throw new IOException("no client level — open pairing from in-world only");

        MinecraftSnapshotSource source = new MinecraftSnapshotSource(level);
        SessionListener listener;
        try {
            // Source is released only once the listener is closed AND no session is streaming,
            // so a grace-period unbind never pulls GL resources out from under an active Snapshot.
            listener = SessionListener.bind(InetAddress.getByName("0.0.0.0"), 0, token, source,
                    onSnapshotDelivered, source::close);
        } catch (IOException e) {
            source.close();
            throw e;
        }
        listener.start();

        return new PairingController.Listener() {
            @Override public int port() { return listener.port(); }
            @Override public void close() throws IOException { listener.close(); }
        };
    }

    private static ByteString newToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return ByteString.of(b);
    }

    private static List<InetAddress> hostAddresses() {
        try {
            return HostAddresses.all();
        } catch (SocketException e) {
            throw new UncheckedIOException(e);
        }
    }
}
