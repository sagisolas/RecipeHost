package org.sagisolas.recipehost.data.pairing

import okio.ByteString
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PairingPayloadTest {
    // 32 zero bytes → base64url "AAAA…A" (43 chars, padding stripped), as the mod writes it.
    private val zeroToken: ByteString = ByteArray(32).toByteString()
    private val zeroTokenB64 = "A".repeat(43)

    @Test
    fun singleHost() {
        val payload = PairingPayload.parse("recipehost://pair?v=1&h=192.168.1.5&p=51234&t=$zeroTokenB64").getOrThrow()

        assertEquals(PairingPayload(hosts = listOf("192.168.1.5"), port = 51234, token = zeroToken), payload)
    }

    @Test
    fun severalHostsKeepTheirOrder() {
        val payload =
            PairingPayload
                .parse(
                    "recipehost://pair?v=1&h=10.0.0.2,127.0.0.1,192.168.1.5&p=1&t=$zeroTokenB64",
                ).getOrThrow()

        assertEquals(listOf("10.0.0.2", "127.0.0.1", "192.168.1.5"), payload.hosts)
    }

    @Test
    fun ipv6ScopeIdIsPercentDecoded() {
        // Exactly what the mod's PairingPayload.toUri writes for 2001:db8::1 and fe80::1%1.
        val payload =
            PairingPayload
                .parse(
                    "recipehost://pair?v=1&h=2001:db8:0:0:0:0:0:1,fe80:0:0:0:0:0:0:1%251&p=1&t=$zeroTokenB64",
                ).getOrThrow()

        assertEquals(listOf("2001:db8:0:0:0:0:0:1", "fe80:0:0:0:0:0:0:1%1"), payload.hosts)
    }

    @Test
    fun ipv6NamedScopeIdIsPercentDecoded() {
        val payload =
            PairingPayload
                .parse(
                    "recipehost://pair?v=1&h=fe80:0:0:0:0:0:0:1%25en0&p=1&t=$zeroTokenB64",
                ).getOrThrow()

        assertEquals(listOf("fe80:0:0:0:0:0:0:1%en0"), payload.hosts)
    }

    private fun uriWithToken(t: String) = "recipehost://pair?v=1&h=10.0.0.2&p=1&t=$t"

    @Test
    fun tokenTooShortIsBadToken() {
        // 42 chars decode to 31 bytes.
        assertIs<PairingPayloadException.BadToken>(PairingPayload.parse(uriWithToken("A".repeat(42))).exceptionOrNull())
    }

    @Test
    fun tokenTooLongIsBadToken() {
        // 44 chars decode to 33 bytes.
        assertIs<PairingPayloadException.BadToken>(PairingPayload.parse(uriWithToken("A".repeat(44))).exceptionOrNull())
    }

    @Test
    fun tokenNotBase64UrlIsBadToken() {
        // '+' and '/' belong to standard base64, not base64url; padding is never written by the mod.
        // A last char with its 2 unused low bits set ("B") is non-canonical and also rejected.
        for (t in listOf(
            "+".repeat(43),
            "/".repeat(43),
            "A".repeat(42) + "=",
            "A".repeat(42) + "!",
            "A".repeat(42) + "B",
        )) {
            assertIs<PairingPayloadException.BadToken>(PairingPayload.parse(uriWithToken(t)).exceptionOrNull(), t)
        }
    }

    @Test
    fun otherVersionIsUnsupported() {
        val error = PairingPayload.parse("recipehost://pair?v=2&h=10.0.0.2&p=1&t=$zeroTokenB64").exceptionOrNull()

        assertEquals("2", assertIs<PairingPayloadException.UnsupportedVersion>(error).version)
    }

    private fun assertMalformed(uri: String) {
        assertIs<PairingPayloadException.MalformedUri>(PairingPayload.parse(uri).exceptionOrNull(), uri)
    }

    @Test
    fun missingHostsIsMalformed() {
        assertMalformed("recipehost://pair?v=1&p=1&t=$zeroTokenB64")
    }

    @Test
    fun missingPortIsMalformed() {
        assertMalformed("recipehost://pair?v=1&h=10.0.0.2&t=$zeroTokenB64")
    }

    @Test
    fun missingTokenIsMalformed() {
        assertMalformed("recipehost://pair?v=1&h=10.0.0.2&p=1")
    }

    @Test
    fun missingVersionIsMalformed() {
        assertMalformed("recipehost://pair?h=10.0.0.2&p=1&t=$zeroTokenB64")
    }

    @Test
    fun wrongSchemeIsMalformed() {
        assertMalformed("https://pair?v=1&h=10.0.0.2&p=1&t=$zeroTokenB64")
    }

    @Test
    fun wrongHostIsMalformed() {
        assertMalformed("recipehost://unpair?v=1&h=10.0.0.2&p=1&t=$zeroTokenB64")
    }

    @Test
    fun portOutsideRangeIsMalformed() {
        for (port in listOf("0", "65536", "-1", "+80", "http", "")) {
            assertMalformed("recipehost://pair?v=1&h=10.0.0.2&p=$port&t=$zeroTokenB64")
        }
    }

    @Test
    fun brokenPercentEscapeIsMalformed() {
        assertMalformed("recipehost://pair?v=1&h=fe80::1%2&p=1&t=$zeroTokenB64")
        assertMalformed("recipehost://pair?v=1&h=fe80::1%zz1&p=1&t=$zeroTokenB64")
        assertMalformed("recipehost://pair?v=1&h=fe80::1%+1&p=1&t=$zeroTokenB64")
        assertMalformed("recipehost://pair?v=1&h=fe80::1%-1&p=1&t=$zeroTokenB64")
    }

    @Test
    fun emptyHostIsMalformed() {
        assertMalformed("recipehost://pair?v=1&h=&p=1&t=$zeroTokenB64")
        assertMalformed("recipehost://pair?v=1&h=10.0.0.2,,10.0.0.3&p=1&t=$zeroTokenB64")
    }

    @Test
    fun schemeIsCaseInsensitive() {
        assertEquals(
            51234,
            PairingPayload.parse("RecipeHost://pair?v=1&h=10.0.0.2&p=51234&t=$zeroTokenB64").getOrThrow().port,
        )
    }

    @Test
    fun fragmentIsNotPartOfTheToken() {
        assertEquals(
            zeroToken,
            PairingPayload.parse("recipehost://pair?v=1&h=10.0.0.2&p=1&t=$zeroTokenB64#x").getOrThrow().token,
        )
    }
}
