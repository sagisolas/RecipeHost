package org.sagisolas.recipehost.mod.common;

import net.minecraft.client.multiplayer.ClientLevel;
import okio.ByteString;
import org.sagisolas.recipehost.mod.common.net.SessionListener;
import org.sagisolas.recipehost.mod.common.snapshot.MinecraftSnapshotSource;

import java.io.IOException;
import java.net.InetAddress;

/**
 * DEV ONLY. Binds a {@link SessionListener} on world-join with a fixed port and
 * all-zero token so the mobile app can pair before the QR screen ticket lands.
 * Delete when the QR-screen + listener-lifecycle slice is implemented.
 */
public final class DevHarness {

    public static final int PORT = 25566;
    public static final ByteString TOKEN = ByteString.of(new byte[32]);

    private static SessionListener listener;
    private static MinecraftSnapshotSource source;

    private DevHarness() {}

    public static synchronized void bindOrRebind(ClientLevel level) {
        closeQuietly();
        try {
            source = new MinecraftSnapshotSource(level);
            listener = SessionListener.bind(InetAddress.getByName("0.0.0.0"), PORT, TOKEN, source);
            listener.start();
            System.out.println("[recipehost] dev harness listening on 0.0.0.0:" + PORT);
        } catch (IOException e) {
            System.err.println("[recipehost] dev harness failed to bind: " + e);
        }
    }

    private static void closeQuietly() {
        if (listener != null) {
            try { listener.close(); } catch (IOException ignored) {}
            listener = null;
        }
        if (source != null) {
            source.close();
            source = null;
        }
    }
}
