# Websocket Component Test Conventions

This package contains the shared foundation for WebSocket-facing component tests in `websocket`.

These conventions are the baseline for new suites. The support tree here is intentionally small at first; expand it as coverage grows, but keep the same responsibilities and suite shape.

## Test lane boundaries

- `component/**` is for Spring component tests that exercise the real WebSocket handshake, session lifecycle, handler flow, and registry behavior inside the `websocket` module.
- `integration/**` is reserved for environment-backed tests that depend on deployed Redis, external identity/session infrastructure, or other non-local runtime services.
- Websocket component tests should use a real servlet container on a random local port and connect through a real WebSocket client.
- Component tests should exercise the real `WebSocketConfig`, `SecurityConfig`, `OCMPWebSocketHandler`, `SessionExpiryKiller`, and `WsSessionRegistry` wiring for the flow under test.
- Do not mock `OCMPWebSocketHandler`, `WsSessionRegistry`, `SessionExpiryKiller`, the Spring WebSocket container, or the client session just to make the test easier to write.
- Use dev-mode handshake support for component tests unless the explicit goal is to verify authenticated session handoff from Spring Session.
- Only mock true external seams outside the websocket module contract, such as IAM access lookups, Redis-backed listener inputs, initial event replay, or other infrastructure that belongs to another module or deployed runtime.
- Topic-subscription authorization tests in dev mode should use same-character UUIDs when the goal is to pass the handler's dev-mode access shortcut without introducing IAM or database setup.

## WebSocket suite template

Keep WebSocket suites grouped in this order when applicable:

1. `Startup and wiring`
2. `Happy path`
3. `Subscription authorization edge cases`
4. `Invalid frame or destination handling`
5. `User delivery and broadcast flow`
6. `Disconnect, expiry, and cleanup`
7. `Other edge cases`

The goal is not to force every suite into every section. The goal is to keep the suite shape predictable as coverage grows.

At minimum, each socket-facing suite should cover:

- A successful handshake through the real `/ws` endpoint.
- The observable subscription or delivery behavior that defines the contract for the handler flow under test.
- Registry side effects relevant to the scenario, such as session registration, topic membership, or cleanup on close.
- At least one failure or edge case around invalid destinations, unauthorized subscriptions, malformed frames, or disconnect cleanup when those behaviors exist.

Tests should verify the implemented contract even when the runtime behavior is surprising. Do not rewrite tests to fit an assumed STOMP-style design if the custom socket protocol behaves differently; either assert the current behavior or change production code deliberately.
When unit and component tests cover the same protocol rule, keep them aligned on the same externally observable outcome. If production behavior changes, update both lanes and this guidance together rather than letting one suite preserve an outdated assumption.
Malformed-frame tests are protocol-contract tests, not resilience folklore: if the handler closes with `BAD_DATA`, both unit and component coverage should assert that close and the resulting registry cleanup.

## Deterministic async readiness

- Do not use a fixed sleep to infer that scheduling, cancellation, close, or registry cleanup occurred. Await the concrete state transition or externally observable outcome; use a timeout only to bound that wait.
- A successful client connection or visible registry entry proves only that specific state unless the production contract makes publication contingent on downstream setup. Otherwise, when a scenario depends on asynchronous setup such as expiry-task publication, await that prerequisite explicitly before sending the frame that replaces or cancels it.
- For rescheduling behavior, capture the published initial task `F0`, perform the real protocol stimulus, await a distinct replacement `F1`, and then assert the contract on both tasks, such as `F0` cancelled and `F1` still live.
- Use a real short deadline when expiry and cleanup are themselves the behavior under test, but install it only after any initial scheduling barrier. Do not combine an artificial deadline with a sleep merely to infer that another action rescheduled it.
- Prefer public outcomes such as frames, close status, and registry state. When registry visibility depends on downstream lifecycle setup, establish that setup before publishing the session rather than exposing scheduler internals solely for a test.

## Support responsibilities

- [`AbstractWebSocketFixtureIT`](./AbstractWebSocketFixtureIT.java) owns topology-neutral database, socket, seed, lifecycle, and async wait helpers; it does not select or reset Redis topology.
- [`AbstractWebSocketComponentIT`](./AbstractWebSocketComponentIT.java) owns the shared Spring bootstrapping and standalone Redis properties/reset used by the existing component subclasses.
- Cluster component suites own their single cluster fixture/property registration and clean only their test-owned source keys.
- [`WebSocketComponentTestApplication`](./WebSocketComponentTestApplication.java) owns the minimal Spring application used by component suites.
- [`WebSocketComponentTestConfig`](./WebSocketComponentTestConfig.java) owns only the external seam replacements needed to keep the component lane local and deterministic.
- `fixture/` owns reusable client connections, protocol frame builders, and recording handlers.
- `assertion/` should own repeated frame, registry, or close-status assertions once multiple suites need them.
- Domain-specific probes or setup for a single flow should live beside the suite that owns them, not in the generic shared support root.

## Fixture conventions

Every fixture helper should follow the same lifecycle-oriented API:

- `reset()` closes open client sessions and clears buffered test state.
- `connect...()` opens a real client connection to the local test server.
- `subscribe...()`, `unsubscribe...()`, `heartbeat()`, or other protocol helpers build reusable socket frames.
- `sameCharUuid(...)` returns deterministic dev-mode-authorized source identifiers when tests need them.

Fixture helpers should not perform assertions.

Prefer real client sessions and real binary protocol frames over mocking the handler internals.

## Assertion conventions

- Assertion helpers should verify repeated frame shapes, close status behavior, registry membership, or cleanup side effects.
- They should stay small and composable.
- They should not hide connection setup, client sends, or await logic that is important to understanding the scenario.

Tests should still read like scenarios; helpers should remove duplication, not hide behavior.
