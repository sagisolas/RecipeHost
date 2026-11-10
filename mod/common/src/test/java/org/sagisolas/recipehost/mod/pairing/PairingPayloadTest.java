package org.sagisolas.recipehost.mod.pairing;

import static org.junit.Assert.assertEquals;

import com.google.protobuf.ByteString;
import java.net.InetAddress;
import java.util.List;
import org.junit.Test;

public class PairingPayloadTest {

    // 32 zero bytes → base64url "AAAA…A" (43 chars, padding stripped)
    private static final ByteString ZERO_TOKEN = ByteString.copyFrom(new byte[32]);
    private static final String ZERO_TOKEN_B64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Test
    public void singleIpv4Host() throws Exception {
        String uri = PairingPayload.toUri(List.of(InetAddress.getByName("192.168.1.5")), 51234, ZERO_TOKEN);

        assertEquals("recipehost://pair?v=1&h=192.168.1.5&p=51234&t=" + ZERO_TOKEN_B64, uri);
    }

    @Test
    public void multipleHostsAreCommaSeparatedInInputOrder() throws Exception {
        String uri = PairingPayload.toUri(
                List.of(
                        InetAddress.getByName("10.0.0.2"),
                        InetAddress.getByName("127.0.0.1"),
                        InetAddress.getByName("192.168.1.5")),
                1,
                ZERO_TOKEN);

        assertEquals("recipehost://pair?v=1&h=10.0.0.2,127.0.0.1,192.168.1.5&p=1&t=" + ZERO_TOKEN_B64, uri);
    }

    @Test
    public void tokenIsBase64UrlWithoutPadding() throws Exception {
        // fb ff bf encodes to "-_-_" in base64url ("+/+/" in standard base64).
        // 10 repeats + trailing fb ff → "-_8=" whose "=" must be stripped.
        byte[] raw = new byte[32];
        for (int i = 0; i < 32; i++) raw[i] = (byte) new int[] {0xfb, 0xff, 0xbf}[i % 3];

        String uri = PairingPayload.toUri(List.of(InetAddress.getByName("10.0.0.2")), 1, ByteString.copyFrom(raw));

        assertEquals("recipehost://pair?v=1&h=10.0.0.2&p=1&t=" + "-_-_".repeat(10) + "-_8", uri);
    }

    @Test
    public void ipv6HostsKeepColonsAndPercentEncodeScopeId() throws Exception {
        String uri = PairingPayload.toUri(
                List.of(InetAddress.getByName("2001:db8::1"), InetAddress.getByName("fe80::1%1")), 1, ZERO_TOKEN);

        assertEquals(
                "recipehost://pair?v=1&h=2001:db8:0:0:0:0:0:1,fe80:0:0:0:0:0:0:1%251&p=1&t=" + ZERO_TOKEN_B64, uri);
    }
}
