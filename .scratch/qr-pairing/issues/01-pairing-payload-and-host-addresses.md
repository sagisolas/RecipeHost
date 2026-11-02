# Pairing payload + host addresses

Status: resolved
Blocked by: —
Spec: ../spec.md

## What

- `PairingPayload.toUri(List<InetAddress> hosts, int port, ByteString token)` (pure) in `:mod:common/pairing/` builds `recipehost://pair?v=1&h=<hosts>&p=<port>&t=<token>`.
  - Hosts are comma-separated and keep their input order.
  - IPv6 scope ids are percent-encoded.
  - The token is base64url with no padding.
- `HostAddresses.all()` is a thin adapter: every address of every `NetworkInterface`, unfiltered. The mobile app does the filtering.

## Acceptance

- `PairingPayloadTest` is green and needs no Minecraft runtime.
- `HostAddresses` has no unit test; the smoke test covers it.
- The **Pairing payload** term is added to GLOSSARY.md.
