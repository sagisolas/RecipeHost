package org.sagisolas.recipehost.mod.net;

import org.sagisolas.recipehost.protocol.Envelope;

import java.util.Iterator;

/**
 * The `Snapshot` a {@link SnapshotSender} streams to the mobile app, as its frames in wire order:
 * the {@code Handshake}, then the batches. The sender adds the closing {@code Done} or
 * {@code Error}. Pulling a frame may block (rendering) or throw (a failing source).
 * Pure — no Minecraft types in the signature, so test fakes can live in {@code :mod:common}'s test
 * source set without a Minecraft runtime.
 */
@FunctionalInterface
public interface SnapshotSource {
    Iterator<Envelope> frames();
}
