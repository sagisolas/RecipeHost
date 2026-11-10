package org.sagisolas.recipehost.mod.pairing;

import com.google.protobuf.ByteString;
import java.net.InetAddress;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the pairing payload URI shown as a QR code on the QR screen:
 * {@code recipehost://pair?v=1&h=<hosts>&p=<port>&t=<token>}.
 */
public final class PairingPayload {

    private PairingPayload() {}

    public static String toUri(List<InetAddress> hosts, int port, ByteString token) {
        String tokenB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(token.toByteArray());
        String hostList =
                hosts.stream().map(h -> h.getHostAddress().replace("%", "%25")).collect(Collectors.joining(","));
        return "recipehost://pair?v=1&h=" + hostList + "&p=" + port + "&t=" + tokenB64;
    }
}
