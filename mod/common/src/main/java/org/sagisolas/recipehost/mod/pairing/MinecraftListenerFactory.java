/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.pairing;

import com.google.protobuf.ByteString;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.InetAddress;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.sagisolas.recipehost.mod.net.SessionListener;
import org.sagisolas.recipehost.mod.snapshot.MinecraftSnapshotSource;
import org.sagisolas.recipehost.mod.snapshot.RecipeSource;
import org.sagisolas.recipehost.mod.snapshot.RenderThread;
import org.slf4j.Logger;

/** Binds a real {@link SessionListener} on all interfaces, serving the current client level. */
public final class MinecraftListenerFactory implements PairingController.ListenerFactory {

    private static final Logger LOG = LogUtils.getLogger();

    private final Function<ClientLevel, RecipeSource> recipeSources;
    private final RenderThread renderThread;

    /** @param recipeSources gives the recipe source for the level being paired */
    public MinecraftListenerFactory(Function<ClientLevel, RecipeSource> recipeSources, RenderThread renderThread) {
        this.recipeSources = recipeSources;
        this.renderThread = renderThread;
    }

    @Override
    public PairingController.Listener bind(ByteString token, Runnable onSnapshotDelivered) throws IOException {
        // Looked up per bind: Minecraft.getInstance() may not be set yet when loaders run init.
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) throw new IOException("no client level — open pairing from in-world only");

        MinecraftSnapshotSource source = new MinecraftSnapshotSource(recipeSources.apply(level), renderThread);
        SessionListener listener;
        int[] port = {0}; // known only after bind; read by the termination log
        try {
            // Source is released only once the listener is closed AND no session is streaming,
            // so a grace-period unbind never pulls GL resources out from under an active Snapshot.
            listener = SessionListener.bind(
                    InetAddress.getByName("0.0.0.0"), 0, token, source, onSnapshotDelivered, () -> {
                        source.close();
                        LOG.info("[recipehost] listener on port {} terminated; snapshot source released", port[0]);
                    });
        } catch (IOException e) {
            source.close();
            throw e;
        }
        port[0] = listener.port();
        listener.start();
        LOG.info("[recipehost] listener started on 0.0.0.0:{}", port[0]);

        return new PairingController.Listener() {
            @Override
            public int port() {
                return listener.port();
            }

            @Override
            public void close() throws IOException {
                listener.close();
            }
        };
    }
}
