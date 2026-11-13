/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.pairing;

import static org.junit.Assert.assertEquals;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class HostAddressesTest {

    @Test
    public void firstInGameMacListReducesToTheLanIpv4() throws Exception {
        List<HostAddresses.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < 8; i++) candidates.add(tunnel("fe80::" + (i + 1) + "%" + (i + 10)));
        candidates.add(up("fe80::a1%20")); // awdl0
        candidates.add(up("fe80::a2%21")); // llw0
        candidates.add(up("fe80::a3%22")); // en0
        candidates.add(up("192.168.1.5")); // en0
        candidates.add(loopback("fe80::1%1")); // lo0
        candidates.add(loopback("::1"));
        candidates.add(loopback("127.0.0.1"));

        assertEquals(List.of(addr("192.168.1.5")), HostAddresses.select(candidates));
    }

    @Test
    public void ipv4ComesBeforeIpv6EachInEnumerationOrder() throws Exception {
        List<InetAddress> selected =
                HostAddresses.select(List.of(up("2001:db8::2"), up("192.168.1.5"), up("2001:db8::1"), up("10.0.0.7")));

        assertEquals(
                List.of(addr("192.168.1.5"), addr("10.0.0.7"), addr("2001:db8::2"), addr("2001:db8::1")), selected);
    }

    @Test
    public void uniqueLocalIpv6IsKept() throws Exception {
        assertEquals(List.of(addr("fd12:3456:789a::1")), HostAddresses.select(List.of(up("fd12:3456:789a::1"))));
    }

    @Test
    public void allUnusableFallsBackToTheFullList() throws Exception {
        List<HostAddresses.Candidate> candidates = List.of(
                loopback("127.0.0.1"),
                up("fe80::1%3"),
                tunnel("10.8.0.2"),
                new HostAddresses.Candidate(addr("192.168.1.5"), false, false, false, false),
                new HostAddresses.Candidate(addr("172.17.0.1"), true, false, false, true));

        assertEquals(
                List.of(
                        addr("127.0.0.1"),
                        addr("fe80::1%3"),
                        addr("10.8.0.2"),
                        addr("192.168.1.5"),
                        addr("172.17.0.1")),
                HostAddresses.select(candidates));
    }

    private static InetAddress addr(String host) throws Exception {
        return InetAddress.getByName(host);
    }

    private static HostAddresses.Candidate up(String host) throws Exception {
        return new HostAddresses.Candidate(addr(host), true, false, false, false);
    }

    private static HostAddresses.Candidate loopback(String host) throws Exception {
        return new HostAddresses.Candidate(addr(host), true, true, false, false);
    }

    private static HostAddresses.Candidate tunnel(String host) throws Exception {
        return new HostAddresses.Candidate(addr(host), true, false, true, false);
    }
}
