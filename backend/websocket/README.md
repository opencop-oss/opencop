# OpenCOP WebSocket

The WebSocket service delivers live events, cached event replay, historical
results, and notifications to authenticated OpenCOP users. It uses a raw
WebSocket endpoint and binary OCMP Protobuf frames.

## Endpoint and framing

[`WebSocketConfig`](src/main/java/com/opencop/websocket/technical/spring/config/WebSocketConfig.java)
registers the endpoint at `/ws`. The endpoint accepts the origins listed in
`websocket.allowed-origins`; its checked-in default is
`http://localhost:3000,http://localhost:8086`.

Each application frame is a serialized
[`SocketMessage`](../sdk/src/main/resources/opencop/core/proto/websocket/message.proto).
The schema defines:

- a frame type;
- a destination string;
- string headers;
- a byte payload; and
- a `compressed` flag.

When the server sets `compressed=true`, the payload bytes are gzip-compressed
by
[`OCMPMessagingTemplate`](src/main/java/com/opencop/websocket/technical/spring/websocket/OCMPMessagingTemplate.java).

## Authentication

The handshake uses the authenticated Spring Session created by the OpenCOP
login flow. The client sends the `OPENCOP_BFF` session cookie, and the service
loads that session from Redis using the settings in
[`application.yml`](src/main/resources/application.yml).

[`SessionSecurityContextInterceptor`](src/main/java/com/opencop/websocket/technical/spring/security/interceptor/SessionSecurityContextInterceptor.java)
rejects the handshake when the session, security context, or authenticated
principal is missing. It passes the existing principal to
[`PrincipalHandshakeHandler`](src/main/java/com/opencop/websocket/technical/spring/websocket/PrincipalHandshakeHandler.java);
the WebSocket service does not perform a separate token-validation flow.

The `opencop.dev.mode` setting creates a local test principal when no session
is present. This mode bypasses normal authentication and is not suitable for a
deployed service.

## Client control frames

[`OCMPWebSocketHandler`](src/main/java/com/opencop/websocket/technical/spring/websocket/OCMPWebSocketHandler.java)
implements these client frame types:

| Type | Behavior |
| --- | --- |
| `SUBSCRIBE` | Adds one exact supported destination. Duplicate subscriptions are no-ops. |
| `UNSUBSCRIBE` | Removes one exact supported destination. Unsubscribing from an inactive destination is a no-op. |
| `HEARTBEAT` | Renews the session's OCMP heartbeat deadline. |
| `SEND` | Is rejected with `SEND_NOT_SUPPORTED`; clients cannot publish application data through this endpoint. |
| `CONNECT`, `ERROR`, `UNKNOWN`, or an unrecognized type | Is rejected with `UNSUPPORTED_FRAME_TYPE`; authentication occurs during the WebSocket handshake. |

`SUBSCRIBE` and `UNSUBSCRIBE` accept only:

- `/user/queue/history`;
- `/user/queue/messages`;
- `/user/queue/notification`; and
- `/topic/queue/event/{uuid}` or `/topic/watchbox/{uuid}`, where `{uuid}` is the canonical lowercase,
  hyphenated representation returned by `UUID.toString()`.

Both frame types require empty headers and payload and `compressed=false`.
`HEARTBEAT` additionally requires an empty destination. Any unused known field
produces `INVALID_FRAME`. `/user/queue/event/init`, `/user/queue/errors`, broad
`/user/**` or `/topic/**` prefixes, and noncanonical event UUIDs are not part of
the protocol.

## Protocol errors and limits

Recoverable protocol rejections are sent directly to the socket that submitted
the invalid operation. They are binary `SocketMessage` frames with:

- type `ERROR` (`6`);
- an empty top-level destination and `compressed=false`;
- `code` and server-derived `operation` headers;
- an optional `rejected-destination` header for bounded destination failures or
  denied subscriptions; and
- a fixed generic reason in the payload.

Protocol errors do not use or require an error subscription. In particular,
the server does not emit the former `SEND / /user/queue/errors` envelope.
Server-initiated Watchbox subscription revocation uses the same envelope with
code `SUBSCRIPTION_REVOKED`, operation `SUBSCRIBE`, and the revoked topic in
`rejected-destination`; it is not a client protocol violation and does not
consume the socket's violation budget.

Malformed binary Protobuf closes the socket with `BAD_DATA`. Text application
messages within the configured size bound produce `UNSUPPORTED_MESSAGE_TYPE`.
The complete inbound application-message limit is configured by
`websocket.max-inbound-message-size` and defaults to `64KB` (65,536 bytes). A
message above that limit closes only the offending socket with status `1009`
and does not count as a protocol violation.

The setting accepts explicit-unit values from `1KB` through `2147483647B`,
inclusively. Values above the `64KB` default are expert-only transport
configuration: the WebSocket container may retain a complete text or
fragmented binary message up to this limit for each active socket before the
application handler runs. Raise it only after assessing available heap,
expected connection concurrency, and the largest legitimate client message;
an unnecessarily large value increases service-wide memory-exhaustion risk.

| Property | Environment variable | Default | Accepted values | Lifecycle and invalid values |
| --- | --- | --- | --- | --- |
| `websocket.max-inbound-message-size` | `WEBSOCKET_MAX_INBOUND_MESSAGE_SIZE` | `64KB` | Explicit-unit Spring `DataSize` from `1KB` through `2147483647B`, inclusively; values above `64KB` are expert-only. | Startup-only and immutable; invalid values prevent startup; restart to change. |
| `websocket.protocol-violation-limit` | `WEBSOCKET_PROTOCOL_VIOLATION_LIMIT` | `10` | Integer from `1` through `10,000`, inclusively. | Startup-only and immutable; invalid values prevent startup; restart to change. |
| `websocket.protocol-violation-window` | `WEBSOCKET_PROTOCOL_VIOLATION_WINDOW` | `60s` | Explicit-unit Spring duration from `1s` through `1h`, inclusively; bare numbers are invalid. | Startup-only and immutable; invalid values prevent startup; restart to change. |

Protocol violations are tracked independently per socket. The checked-in
defaults allow nine recoverable violations in a rolling 60-second window. The
tenth sends no binary error and closes that socket with exact status and reason
`1008 / Protocol limit exceeded`. Reconnecting starts with an empty violation
history; neither close invalidates the authenticated account or another socket
for the same user.

A subscription the server denies counts against that same budget. Denials draw
down the one per-socket allowance shared with every other recoverable
rejection, so repeated attempts to subscribe to topics the user cannot read
close the socket on the same code and reason as any other violation. Below the
threshold a denial still returns `SUBSCRIBE_DENIED` with its
`rejected-destination` header, so a compliant client can correlate the failed
operation as before. Valid operations neither increment nor reset the counter.

## Server destinations

Normal event and user delivery requires a subscription. The retained handlers
use:

| Destination | Content and source |
| --- | --- |
| `/topic/queue/event/{uuid}` | Live and cached feature events. [`RealTimeEventsListener`](src/main/java/com/opencop/websocket/technical/spring/listener/RealTimeEventsListener.java) publishes live updates, and [`WebSocketEventListener`](src/main/java/com/opencop/websocket/technical/gateway/websocket/WebSocketEventListener.java) starts cached replay after subscription. |
| `/topic/watchbox/{uuid}` | Committed Watchbox transition alerts delivered through the transactional outbox and Redis alert channel. |
| `/user/queue/messages` | Start and finish commands around cached replay, produced by [`RetrieveAllEventsHandler`](src/main/java/com/opencop/websocket/business/usecase/event/RetrieveAllEventsHandler.java). |
| `/user/queue/notification` | User notifications produced by [`UserNotificationHandler`](src/main/java/com/opencop/websocket/business/usecase/notification/UserNotificationHandler.java). |
| `/user/queue/history` | Historical job status and chunk data produced by [`UserHistoryEventHandler`](src/main/java/com/opencop/websocket/business/usecase/history/UserHistoryEventHandler.java) and [`UserHistoryChunkSender`](src/main/java/com/opencop/websocket/business/usecase/history/UserHistoryChunkSender.java). |

Subscriptions to `/topic/queue/event/{uuid}` normally require access to an
active source or layer.
[`LiveEventAccessPolicy`](src/main/java/com/opencop/websocket/business/usecase/event/LiveEventAccessPolicy.java)
performs that authorization before the registry adds the subscription. In
`opencop.dev.mode`, `OCMPWebSocketHandler` instead accepts the same-character
UUIDs used by local component tests.

Subscriptions to `/topic/watchbox/{uuid}` have no dev-mode bypass. They require
visibility of every layer in the active Watchbox at admission, and the delivery
path periodically re-authorizes the registered audience before broadcasting an
alert. A denied admission consumes the normal protocol-violation budget; a
server-initiated revocation does not.

## Heartbeats

The service uses two heartbeat mechanisms:

- `WebSocketConfig` sends a standard WebSocket Ping to every open session every
  20 seconds.
- `OCMPWebSocketHandler` starts a 20-second OCMP heartbeat deadline when a
  connection opens. Each client `HEARTBEAT` frame replaces it with a new
  25-second deadline.

If the OCMP deadline expires,
[`SessionExpiryKiller`](src/main/java/com/opencop/websocket/technical/spring/websocket/SessionExpiryKiller.java)
closes the connection with status `4403`. A failed server ping also removes and
closes the session.

## Runtime dependencies

The service uses:

- Redis for Spring Session, live-event cache reads, historical chunk delivery,
  and the live-event, notification, and history channels configured by
  [`CacheConfig`](src/main/java/com/opencop/websocket/technical/spring/config/CacheConfig.java);
- PostgreSQL for source and layer access checks wired by
  [`AppConfig`](src/main/java/com/opencop/websocket/technical/spring/config/AppConfig.java);
  and
- the OpenCOP SDK for the OCMP schema, shared models, Redis support, security
  principal, IAM services, and persistence adapters.

PostgreSQL and Redis connection properties are defined in
[`application.yml`](src/main/resources/application.yml). Its
[`SecurityConfig`](src/main/java/com/opencop/websocket/technical/spring/config/SecurityConfig.java)
permits `/actuator/health` and the `/ws` handshake and denies all remaining
HTTP requests.

## Clustered geo cleanup

In cluster mode, the regular Janitor discovers sources through a shared directory
without scanning the keyspace. A separate repair job periodically scans for
source-local markers and restores directory entries lost independently of the
source data, such as during a failover.

Missing directory membership delays stale index cleanup; live reads and payload
TTLs still work. Any later write in the source restores its membership, so repair
is a backstop for sources that remain quiet. It supplements the regular Janitor
and can run infrequently because it addresses deferred housekeeping.

Repair scans are paced, but their cost grows with the Redis keyspace. Choose a
schedule appropriate to the deployment. Configuration defaults and the disable
option are defined in
[`GeoDirectoryRepairProperties`](src/main/java/com/opencop/websocket/technical/gateway/cache/repair/GeoDirectoryRepairProperties.java).

Standalone mode does not run directory repair. Switching `redis.mode` changes
the geo key namespace and does not migrate existing state.

## Build and test

The module compiles for Java 25, as configured by
[`opencop-parent/pom.xml`](../opencop-parent/pom.xml). Run its unit suite with
the repository Maven wrapper:

```bash
../mvnw test
```

The
[`component/support` guide](src/test/java/com/opencop/websocket/component/support/README.md)
documents the real-handshake, session-lifecycle, protocol, and external-seam
conventions used by the component suites.
