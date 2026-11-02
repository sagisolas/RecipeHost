package org.sagisolas.recipehost.mod.net;

import org.sagisolas.recipehost.protocol.Handshake;
import org.sagisolas.recipehost.protocol.IconBatch;
import org.sagisolas.recipehost.protocol.ItemBatch;
import org.sagisolas.recipehost.protocol.RecipeBatch;

import java.util.Iterator;

/**
 * Pulls the data a {@link SnapshotSender} streams to the mobile app.
 * Pure — no Minecraft types in the signatures, so test fakes can live in
 * {@code :mod:common}'s test source set without a Minecraft runtime.
 */
public interface SnapshotSource {
    Handshake handshake();
    Iterator<ItemBatch> itemBatches();
    Iterator<RecipeBatch> recipeBatches();
    Iterator<IconBatch> iconBatches();
}
