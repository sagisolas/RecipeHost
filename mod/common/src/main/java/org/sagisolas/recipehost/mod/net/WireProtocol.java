/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.net;

/**
 * Wire-protocol constants shared by every {@link SnapshotSource} implementation
 * and by anything that produces an {@link org.sagisolas.recipehost.protocol.Envelope}.
 */
public final class WireProtocol {

    public static final int PROTOCOL_VERSION = 4;

    private WireProtocol() {}
}
