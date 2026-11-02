package org.sagisolas.recipehost.mod.common.net;

/**
 * Wire-protocol constants shared by every {@link SnapshotSource} implementation
 * and by anything that produces an {@link org.sagisolas.recipehost.protocol.Envelope}.
 */
public final class WireProtocol {

    public static final int PROTOCOL_VERSION = 1;

    private WireProtocol() {}
}
