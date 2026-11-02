package org.sagisolas.recipehost.mod.pairing;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;

/**
 * Every address of every local network interface, unfiltered and in enumeration
 * order. The mobile app picks the reachable one — it knows its own IP and subnet.
 */
public final class HostAddresses {

    private HostAddresses() {}

    public static List<InetAddress> all() throws SocketException {
        return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                .flatMap(NetworkInterface::inetAddresses)
                .toList();
    }
}
