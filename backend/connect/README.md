# OpenCOP Connect

Connect runs the network workers that receive data from configured sources and
forward it to the OpenCOP processing pipeline. It does not own the HTTP API for
creating or managing those sources.

## Runtime role

Each Connect instance gets an ID from
[`InstanceIdentity`](../sdk/src/main/java/com/opencop/core/sdk/helper/InstanceIdentity.java).
It then:

1. Reads enabled socket sources assigned to that instance from PostgreSQL.
2. Reconciles those rows with the connectors running in the current process.
3. Frames incoming TCP or UDP data according to the framing in the source's connection spec,
   or reassembles KLV units from an RTSP stream.
4. Publishes each complete payload to the
   `opencop.event.external.raw` Kafka topic with `opencop-source-id` and
   `opencop-object-id` headers.
5. Records worker health and source runtime status in PostgreSQL.
6. Receives outbound payloads from its instance-specific Redis channel and
   writes them to the matching connector.

The wiring for these steps is in
[`AppConfig`](src/main/java/com/opencop/api/connect/technical/spring/endpoint/AppConfig.java).
The desired-state loop is implemented by
[`DesiredOwnershipReconciler`](src/main/java/com/opencop/api/connect/business/reconciliation/DesiredOwnershipReconciler.java),
and inbound publication is implemented by
[`RawBytesEventHandler`](src/main/java/com/opencop/api/connect/business/usecase/event/RawBytesEventHandler.java).

Connect exposes `/actuator/health`. Its
[`SecurityFilterChain`](src/main/java/com/opencop/api/connect/technical/spring/endpoint/AppConfig.java)
denies other HTTP requests.

## Supported sources

[`ConnectorRegistry`](src/main/java/com/opencop/api/connect/technical/gateway/socket/ConnectorRegistry.java)
selects a connector for these implemented transport and security combinations:

| Transport | Security | Implementation |
| --- | --- | --- |
| TCP | None | [`TcpConnectorFactory`](src/main/java/com/opencop/api/connect/technical/gateway/socket/factory/tcp/TcpConnectorFactory.java) |
| TCP | mTLS | [`TcpConnectorFactory`](src/main/java/com/opencop/api/connect/technical/gateway/socket/factory/tcp/TcpConnectorFactory.java) |
| UDP | None | [`UdpConnectorFactory`](src/main/java/com/opencop/api/connect/technical/gateway/socket/factory/udp/UdpConnectorFactory.java) |
| RTSP or RTSPS carrying KLV | None | [`RtspKlvConnectorFactory`](src/main/java/com/opencop/api/connect/technical/gateway/socket/factory/rtsp/RtspKlvConnectorFactory.java) |

The SDK can represent TLS-only and DTLS settings, but Connect has no connector
factory for either combination. `ConnectorRegistry` rejects a source when no
factory supports its transport and security settings.

Each connector factory builds whatever framing its transport needs from the
connection spec. TCP factories use the delimiter, binary length-prefix, or
pass-through mode selected by the spec's framing;
[`TcpFramingResolver`](src/main/java/com/opencop/api/connect/technical/gateway/resolver/TcpFramingResolver.java)
contains the exact mapping and enforces the configured maximum message size
where applicable. UDP delivers each datagram whole as one message, and RTSP
sources use their own KLV packet reassembly.

For mTLS, Connect loads PKCS#12 client and trust material
through the SDK's
[`ConnectionStore`](../sdk/src/main/java/com/opencop/core/sdk/ports/gateway/secrets/ConnectionStore.java).
[`MtlsTcpSessions`](src/main/java/com/opencop/api/connect/technical/gateway/socket/factory/tcp/MtlsTcpSessions.java)
performs the TLS handshake, applies configured protocol and cipher restrictions,
and checks the hostname when the source enables that option.

## External dependencies

A running service uses:

- PostgreSQL for desired source ownership, worker health, restart epochs, and
  runtime observations. The datasource keys are under
  `spring.datasource` in
  [`application.yml`](src/main/resources/application.yml).
- Kafka or Azure Event Hubs for raw payload publication. Select the provider
  and its settings with `opencop.messaging-broker.provider` and
  `opencop.messaging-broker.properties`, as defined by the SDK's
  [`LocalProviderProperties`](../sdk/src/main/java/com/opencop/core/sdk/technical/gateway/broker/kafka/properties/LocalProviderProperties.java)
  and
  [`AzureProviderProperties`](../sdk/src/main/java/com/opencop/core/sdk/technical/gateway/broker/kafka/properties/AzureProviderProperties.java).
- Redis for instance-specific outbound push messages. Connection settings use
  the `spring.data.redis` keys in
  [`application.yml`](src/main/resources/application.yml).
- Vault KV v2 for mTLS key-store references. Configure
  `opencop.secrets.vault.provider=kv` plus `addr`, `mount`, and `token-file`;
  these values are bound by
  [`VaultSecretsProperties`](../sdk/src/main/java/com/opencop/core/sdk/technical/gateway/secrets/vault/kv/VaultSecretsProperties.java).
- The remote TCP, UDP, or RTSP sources assigned to this Connect instance.

## Connect settings

The checked-in defaults live in
[`application.yml`](src/main/resources/application.yml). Most `opencop.connect`
settings bind to
[`ConnectProperties`](src/main/java/com/opencop/api/connect/technical/config/ConnectProperties.java).
They cover:

- reconciliation timing and recovery backoff;
- periodic full source restarts;
- runtime-status expiry;
- behavior during PostgreSQL desired-state outages;
- PostgreSQL notification-listener pool and shutdown timing;
- TCP read timeout, keepalive, and message-size limits; and
- RTSP connection, read, transport, and first-KLV timeouts.

The separate `opencop.connect.nudge.enabled` switch controls the PostgreSQL
notification-listener bean directly in
[`AppConfig`](src/main/java/com/opencop/api/connect/technical/spring/endpoint/AppConfig.java).

Set `OPENCOP_INSTANCE_ID` when a stable explicit worker ID is needed. Otherwise,
[`InstanceIdentity`](../sdk/src/main/java/com/opencop/core/sdk/helper/InstanceIdentity.java)
uses the container hostname in Docker or creates a process-local ID.

## Build and test

The module compiles for Java 25, as configured by
[`opencop-parent/pom.xml`](../opencop-parent/pom.xml). Use the repository Maven
wrapper from this directory:

```bash
../mvnw test
```

Run the focused TCP component suite, including its real localhost TCP and mTLS
peers, with:

```bash
../mvnw -Dskip.unit.tests=true -Dit.test=TcpComponentIT verify
```

The broader component-test conventions are documented in the
[`component/support` guide](src/test/java/com/opencop/api/connect/component/support/README.md).
