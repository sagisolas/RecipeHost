package org.sagisolas.recipehost.mod.pairing;

import okio.ByteString;

import java.security.SecureRandom;
import java.util.function.Supplier;

/** Fresh 256-bit {@code Token}s from a {@link SecureRandom}. */
public final class SecureTokens implements Supplier<ByteString> {

    private final SecureRandom random = new SecureRandom();

    @Override
    public ByteString get() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return ByteString.of(b);
    }
}
