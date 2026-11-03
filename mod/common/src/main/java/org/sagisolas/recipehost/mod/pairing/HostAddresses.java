package org.sagisolas.recipehost.mod.pairing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The local addresses a phone could use to reach this machine. Drops only addresses no
 * phone can ever use; the mobile app still picks the reachable one among the rest.
 */
public final class HostAddresses {

    /** One address together with the flags of the interface it belongs to. */
    public record Candidate(InetAddress address, boolean up, boolean loopback, boolean pointToPoint, boolean virtual) {}

    private static final Logger LOG = LoggerFactory.getLogger(HostAddresses.class);

    private HostAddresses() {}

    /** Thin, untested adapter: enumerates the machine's interfaces and hands them to {@link #select}. */
    public static List<InetAddress> all() {
        try {
            List<Candidate> candidates = new ArrayList<>();
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                boolean up = nif.isUp();
                boolean loopback = nif.isLoopback();
                boolean pointToPoint = nif.isPointToPoint();
                boolean virtual = nif.isVirtual();
                for (InetAddress address : Collections.list(nif.getInetAddresses())) {
                    candidates.add(new Candidate(address, up, loopback, pointToPoint, virtual));
                }
            }
            return select(candidates);
        } catch (SocketException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Drops loopback and link-local addresses and addresses on down, point-to-point or
     * virtual interfaces; orders IPv4 before IPv6, each in input order. If nothing is
     * left, returns every address unfiltered so the payload never has an empty host list.
     */
    public static List<InetAddress> select(List<Candidate> candidates) {
        List<InetAddress> usable = candidates.stream()
                .filter(HostAddresses::usable)
                .map(Candidate::address)
                .sorted(Comparator.comparing(a -> !(a instanceof Inet4Address))) // stable: IPv4 first
                .toList();
        if (!usable.isEmpty()) return usable;
        LOG.warn("[recipehost] no usable host address found; pairing payload lists all {} unfiltered", candidates.size());
        return candidates.stream().map(Candidate::address).toList();
    }

    private static boolean usable(Candidate c) {
        InetAddress a = c.address();
        return c.up() && !c.loopback() && !c.pointToPoint() && !c.virtual()
                && !a.isLoopbackAddress() && !a.isLinkLocalAddress();
    }
}
