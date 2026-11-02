# Connect Component Test Conventions

This package contains the shared foundation for socket-facing component tests in `connect`.

## Test lane boundaries

- `component/**` is for component tests that exercise the real connection lifecycle inside the `connect` module.
- `integration/**` is reserved for environment-backed tests that depend on external Kafka, Redis, TAK, cloud, or other deployed services.
- Connect component tests should use real local TCP or UDP sockets for the connection under test.
- Do not mock `SocketConnector`, `SessionOpener`, `Socket`, `ServerSocket`, `DatagramSocket`, or the remote peer behavior behind the connection.
- Component tests should exercise the real `ConnectorRegistry`, connector factories, framing resolution, and `ConnectionManager` orchestration for the flow under test.
- Do not mock internal connect orchestration just to make the test easier to write.
- Only mock true external seams outside the connect contract, such as Kafka publication, Redis command publication, or blob/secret lookup.
- Framing resolution in component tests should read the stored connection spec through the component-test database.
- Prefer localhost peers with ephemeral ports over shared fixed ports so suites remain isolated and parallel-safe.

## Connection suite template

Keep connection suites grouped in this order when applicable:

1. `Startup and wiring`
2. `Happy path TCP`
3. `Happy path UDP`
4. `Commands and outbound flow`
5. `Disconnect, stop, and restart flow`
6. `Framing and protocol edge cases`
7. `Other edge cases`

The goal is not to force every suite into every section. The goal is to keep the suite shape predictable as coverage grows.

At minimum, each connection-management suite should cover:

- The main happy path for the transport being exercised.
- Observable state transitions relevant to the transport and scenario, including at least the connected state and the terminal state under test.
- Message ingestion through a real local peer.
- Stop and disconnect behavior.
- Command or outbound send behavior when the connector supports writes.
- At least one edge case around framing, oversize payloads, idle timeouts, socket closure, or duplicate lifecycle operations.

Tests should verify the implemented contract even when the runtime behavior is surprising. Do not rewrite tests to fit an assumed design if the connector or manager behaves differently; either assert the current behavior or change production code deliberately.

## Support responsibilities

- [`AbstractSocketComponentIT`](./AbstractSocketComponentIT.java) stays narrow and only owns reusable socket-fixture lifecycle and common timeout defaults.
- [`RecordingConnectorListener`](./RecordingConnectorListener.java) captures connector callbacks for assertions without hiding control flow.
- Suites should use `RecordingConnectorListener` when verifying lifecycle sequencing or terminal-state callbacks.
- `fixture/` owns localhost TCP and UDP peers plus default connection specs and payload builders.
- `assertion/` owns repeated lifecycle and payload assertions.
- Domain-specific probes or setup for a single connection family should live beside the suite that owns them, not in the generic shared support root.

## Fixture conventions

Every fixture helper should follow the same lifecycle-oriented API:

- `reset()` closes sockets, clears buffered state, and releases ephemeral ports.
- `start...()` creates and starts a localhost peer for the scenario.
- `tcpSpec...()` or `udpSpec...()` returns a reusable `ConnectionSpec`.
- `default...()` returns reusable payload or command defaults.

Fixture helpers should not perform assertions.

Prefer real localhost peers and real framing over fake connector internals.

## Assertion conventions

- Assertion helpers should verify repeated connector states, payload delivery, errors, and restart requests.
- They should stay small and composable.
- They should not hide connection startup, message sends, or other control flow that is important to understanding the scenario.

Tests should still read like scenarios; helpers should remove duplication, not hide behavior.
