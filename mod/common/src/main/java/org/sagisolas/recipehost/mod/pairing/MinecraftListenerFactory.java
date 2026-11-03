package org.sagisolas.recipehost.mod.pairing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import okio.ByteString;
import org.sagisolas.recipehost.mod.net.SessionListener;
import org.sagisolas.recipehost.mod.snapshot.MinecraftSnapshotSource;

import java.io.IOException;
import java.net.InetAddress;

/** Binds a real {@link SessionListener} on all interfaces, serving the current client level. */
public final class MinecraftListenerFactory implements PairingController.ListenerFactory {

    @Override
    public PairingController.Listener bind(ByteString token, Runnable onSnapshotDelivered) throws IOException {
        // Looked up per bind: Minecraft.getInstance() may not be set yet when loaders run init.
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
}
