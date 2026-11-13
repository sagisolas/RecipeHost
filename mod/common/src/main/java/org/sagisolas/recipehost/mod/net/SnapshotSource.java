/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.net;

import java.util.Iterator;
import org.sagisolas.recipehost.protocol.Envelope;

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
