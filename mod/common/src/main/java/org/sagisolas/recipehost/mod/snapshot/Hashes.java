/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Content keys: stable across Scans for the same bytes. */
public final class Hashes {

    private static final int KEY_BYTES = 16;

    private Hashes() {}

    /** SHA-256 of {@code bytes}, truncated to 128 bits and hex-encoded (32 characters). */
    public static String truncatedSha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest, 0, KEY_BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JVM ships SHA-256", e);
        }
    }
}
