package org.sagisolas.recipehost.mod.common.net;

import org.sagisolas.recipehost.protocol.Handshake;
import org.sagisolas.recipehost.protocol.IconBatch;
import org.sagisolas.recipehost.protocol.ItemBatch;
import org.sagisolas.recipehost.protocol.RecipeBatch;

import java.util.Collections;
import java.util.Iterator;

/**
 * Placeholder {@link SnapshotSource} that yields no items, recipes, or icons.
 * Used before the Minecraft-backed source is wired so the mod can still bind
 * the listener, hand the handshake to the mobile app, and terminate the stream.
 */
public final class StubSnapshotSource implements SnapshotSource {

    public static final int PROTOCOL_VERSION = 1;

    private final String minecraftVersion;
    private final String locale;

    public StubSnapshotSource(String minecraftVersion, String locale) {
        this.minecraftVersion = minecraftVersion;
        this.locale = locale;
    }

    @Override
    public Handshake handshake() {
        return new Handshake.Builder()
                .protocol_version(PROTOCOL_VERSION)
                .minecraft_version(minecraftVersion)
                .locale(locale)
                .build();
    }

    @Override public Iterator<ItemBatch> itemBatches()     { return Collections.<ItemBatch>emptyIterator(); }
    @Override public Iterator<RecipeBatch> recipeBatches() { return Collections.<RecipeBatch>emptyIterator(); }
    @Override public Iterator<IconBatch> iconBatches()     { return Collections.<IconBatch>emptyIterator(); }
}
