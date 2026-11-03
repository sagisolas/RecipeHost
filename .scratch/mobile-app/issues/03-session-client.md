# SessionClient on ktor-network: hosts tried in turn, Auth, retry and error rules

Status: ready-for-agent
Blocked by: 02
Spec: ../spec.md

## What

- Add `kotlinx-coroutines-core` and `ktor-network` to `commonMain`.
- `data/transport/SessionClient` is an instance constructed with a `SocketConnector`. The `SocketConnector` interface has a ktor implementation plus a fake for tests.
  - `open(payload): Flow<Envelope>`:
    - tries `payload.hosts` **one at a time, in order**, with a 1.5 s connect timeout each;
    - on the first host that connects, writes `Auth{token}`;
    - emits incoming frames until `Done`, then closes.
  - Add a code comment explaining why hosts are tried one at a time: `SessionListener` claims the session when it accepts the TCP connection, before Auth arrives, so parallel attempts can lose to their own `BUSY`.
- Failures are typed, as `sealed class ScanFailure`:
  - **Network**: every host unreachable, an I/O error, or EOF before `Done`. These get **one** automatic retry of the whole `open` with the same payload. The listener stays up after a drop (ADR 0001).
  - **Domain**: `Error{UNAUTHORIZED}`, `Error{BUSY}`, `Error{SOURCE_FAILURE}`, or a `Handshake.protocol_version` other than 1. These get no retry.
- Put the retry policy in a separate `RetryingSessionClient`, or as a parameter, so it can be tested on its own.

## Acceptance

`SessionClientTest` uses a fake connector and `kotlinx-coroutines-test` virtual time. It checks that:
- hosts are tried in order, with a timeout moving on to the next host;
- `Auth` is the first frame written;
- envelopes are passed through until `Done`;
- one retry happens after a mid-stream drop, a second drop surfaces a `Network` failure, and frames from the failed attempt never reach the consumer as a complete stream;
- there are no retries on UNAUTHORIZED, BUSY or SOURCE_FAILURE;
- a version mismatch is reported as a domain failure.
