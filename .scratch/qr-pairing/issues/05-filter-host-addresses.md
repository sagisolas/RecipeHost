# Filter unusable host addresses out of the pairing payload

Status: done
Blocked by: —
Spec: ../spec.md

## Problem

`HostAddresses.all()` puts every address of every interface into the **Pairing payload**. On the first in-game test (Fabric, macOS), the URI listed 15 hosts:

- 13 IPv6 link-local addresses (`fe80::…%utun0…7`, `%awdl0`, `%llw0`, `%en0`, `%lo0`);
- IPv6 and IPv4 loopback (`::1%lo0`, `127.0.0.1`);
- the one address a phone can actually reach: the LAN IPv4 on `en0`.

That makes the QR code very dense and hard for a phone camera to scan from a monitor, and it moves the URI closer to the QR capacity limit. If it goes over, `QrCode.encodeText` throws and pairing fails.

This narrows an earlier decision ("the mod does no filtering; the app picks the reachable host"). The app still chooses among the remaining hosts. The mod only drops addresses that no phone can ever use.

## What

Split `HostAddresses` into a pure filter plus the existing thin `NetworkInterface` adapter.

- **Drop:**
  - loopback addresses (`127.0.0.0/8`, `::1`);
  - IPv6 link-local addresses (`fe80::/10`): unusable from the phone, because the scope id names an interface on the PC;
  - interfaces that are down;
  - point-to-point and virtual interfaces (`isPointToPoint()` / `isVirtual()`), which covers macOS `utun*` VPN/tunnel interfaces without matching on names.
- **Keep:** IPv4 site-local and public addresses; IPv6 global and unique-local addresses (`fc00::/7`).
- **Order:** IPv4 first, then IPv6, each in enumeration order. Most phones pair over IPv4 LAN.
- **No usable address left:** fall back to the unfiltered list, so pairing never ends up with an empty `h=`, and log a WARN.
- **Open question:** whether `awdl0`/`llw0` (Apple Wireless Direct) are already excluded by the rules above. Check on macOS before adding any name-based rule.
  - **Answered (macOS, 2026-11-10):** `awdl0`/`llw0` report up, not point-to-point, not virtual, but carry only `fe80::` addresses, so the link-local rule drops them. No name-based rule needed. Running the adapter on that machine leaves only the en0 LAN IPv4.

## Testing

- Seam to agree before writing tests: `HostAddresses.select(List<Candidate>)`, where `Candidate = (InetAddress, up, loopback, pointToPoint, virtual)`.
- It catches mistakes in the filtering, the ordering and the fallback, deterministically. It misses `NetworkInterface` quirks on real machines; the in-game check covers those.
- Suggested cases:
  - the 15-host list from the first test reduces to the single LAN IPv4;
  - IPv4 comes before global IPv6;
  - a ULA IPv6 is kept;
  - an all-unusable list falls back to the full list.

## Docs to update

- ADR 0001: "every host address of the machine (unfiltered…)" becomes "usable host addresses (loopback, link-local and tunnel interfaces excluded)".
- GLOSSARY **Pairing payload**: same change.
- `spec.md`: the `HostAddresses` bullet.
