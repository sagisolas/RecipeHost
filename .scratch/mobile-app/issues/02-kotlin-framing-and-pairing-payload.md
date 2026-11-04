# Kotlin EnvelopeFraming with shared golden vectors; PairingPayload.parse

Status: done
Spec: ../spec.md

## What

- `:shared` `data/transport/EnvelopeFraming` (commonMain): a varint length prefix followed by the `Envelope` bytes, the same format as the Java version.
  - It works on okio `BufferedSource` / `BufferedSink`, which arrive through Wire, so the socket layer can adapt to it.
  - A clean EOF before the first varint byte returns `null`. EOF mid-frame throws.
- Golden vectors: a few `Envelope`s with their exact frame bytes written as hex:
  - an empty `Done`;
  - `Auth` with a 32-byte token;
  - an `ItemBatch` larger than 127 bytes, so the length needs a two-byte varint.
  
  The same hex literals go into the Java `EnvelopeFramingTest` (`:protocol`) and a Kotlin `EnvelopeFramingTest` (`commonTest`). Both encode the envelopes and must match the hex, and both decode the hex back to the same envelopes.
- `data/pairing/PairingPayload.parse(uri: String): Result<PairingPayload>` (pure):
  - It reads `v=1`, `h` (comma-separated, order kept, percent-decoded so `%25` becomes `%` for IPv6 scope ids), `p` (1–65535) and `t` (base64url without padding, exactly 32 bytes).
  - It returns typed failures: `UnsupportedVersion`, `MalformedUri`, `BadToken`.
  - The scheme and host must be `recipehost://pair`.

## Acceptance

- The golden vector tests pass on both sides.
- `PairingPayloadTest` covers:
  - a single host, and several hosts keeping their order;
  - an IPv6 address with a scope id;
  - a token that is too short, too long, or not base64url;
  - `v=2`;
  - a missing `h`, `p` or `t`;
  - the wrong scheme.
