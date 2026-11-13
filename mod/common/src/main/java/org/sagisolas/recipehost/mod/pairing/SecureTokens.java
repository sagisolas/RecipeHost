/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.pairing;

import com.google.protobuf.ByteString;
import java.security.SecureRandom;
import java.util.function.Supplier;

/** Fresh 256-bit {@code Token}s from a {@link SecureRandom}. */
public final class SecureTokens implements Supplier<ByteString> {

    private final SecureRandom random = new SecureRandom();

    @Override
    public ByteString get() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return ByteString.copyFrom(b);
    }
}
