package org.sagisolas.recipehost.data.pairing

import io.ktor.http.URLDecodeException
import io.ktor.http.URLParserException
import io.ktor.http.Url
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.sagisolas.recipehost.data.pairing.PairingPayloadException.BadToken
import org.sagisolas.recipehost.data.pairing.PairingPayloadException.MalformedUri
import org.sagisolas.recipehost.data.pairing.PairingPayloadException.UnsupportedVersion
import kotlin.io.encoding.Base64

/**
 * The pairing data the companion mod encodes in its QR code as
 * `recipehost://pair?v=1&h=<hosts>&p=<port>&t=<token>` (see the mod's
 * `PairingPayload.toUri`).
 */
internal data class PairingPayload(
    /** Host addresses in the mod's order, to be tried one at a time. */
    val hosts: List<String>,
    val port: Int,
    val token: ByteString,
) {
    companion object {
        private const val SCHEME = "recipehost"
        private const val HOST = "pair"
        private const val SUPPORTED_VERSION = "1"
        private const val TOKEN_BYTES = 32
        private val PORT_RANGE = 1..65535
        private val BASE64URL_NO_PADDING = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        private val DIGITS = Regex("[0-9]+")

        fun parse(uri: String): Result<PairingPayload> {
            val params = try {
                Url(uri).takeIf { it.protocol.name == SCHEME && it.host == HOST }?.parameters
                    ?: return fail(MalformedUri("expected a $SCHEME://$HOST URI"))
            } catch (e: URLParserException) {
                return fail(MalformedUri(e.message ?: "unparseable URI"))
            } catch (e: URLDecodeException) {
                return fail(MalformedUri(e.message ?: "bad percent escape"))
            }

            val version = params["v"] ?: return fail(MalformedUri("missing v"))
            if (version != SUPPORTED_VERSION) return fail(UnsupportedVersion(version))

            val rawHosts = params["h"] ?: return fail(MalformedUri("missing h"))
            val hosts = rawHosts.split(',')
            if (hosts.any { it.isEmpty() }) return fail(MalformedUri("empty host in h"))

            val rawPort = params["p"] ?: return fail(MalformedUri("missing p"))
            val port = rawPort.takeIf { DIGITS.matches(it) }?.toIntOrNull()?.takeIf { it in PORT_RANGE }
                ?: return fail(MalformedUri("p out of range: $rawPort"))

            val rawToken = params["t"] ?: return fail(MalformedUri("missing t"))
            val token = decodeToken(rawToken) ?: return fail(BadToken())

            return Result.success(PairingPayload(hosts, port, token))
        }

        private fun fail(error: PairingPayloadException): Result<PairingPayload> = Result.failure(error)

        /**
         * base64url without padding that decodes to exactly [TOKEN_BYTES] bytes, or `null`. The
         * stdlib decoder is strict: it rejects other alphabets, padding and non-zero pad bits.
         */
        private fun decodeToken(value: String): ByteString? {
            val bytes = try {
                BASE64URL_NO_PADDING.decode(value)
            } catch (_: IllegalArgumentException) {
                return null
            }
            return bytes.takeIf { it.size == TOKEN_BYTES }?.toByteString()
        }
    }
}

/** Why [PairingPayload.parse] rejected a URI. */
internal sealed class PairingPayloadException(message: String) : Exception(message) {
    /** The payload's `v` is not one this app understands. */
    class UnsupportedVersion(val version: String) : PairingPayloadException("unsupported pairing payload version $version")

    /** Not a `recipehost://pair` URI, or `v`, `h` or `p` is missing or invalid. */
    class MalformedUri(reason: String) : PairingPayloadException("malformed pairing URI: $reason")

    /** The token is not base64url without padding, or does not decode to 32 bytes. */
    class BadToken : PairingPayloadException("pairing token must be 32 bytes of base64url without padding")
}
