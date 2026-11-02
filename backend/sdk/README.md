# OpenCOP SDK

The SDK is the shared Java library used by the OpenCOP services. It contains
cross-module models and contracts as well as reusable implementations for
mapping, messaging, persistence, security, and external storage.

Despite its name, this is not an externally consumed client SDK: it is shared
only among the OpenCOP services and bundled plugins, and currently has no
external consumers. Do not preserve API shapes or binary compatibility here for
hypothetical third-party consumers.

## Main packages

- [`model`](src/main/java/com/opencop/core/sdk/model) contains the shared
  connection, data-source, history, job, mapping, media, notification, push,
  runtime-feature, visibility, and watchbox data structures.
- [`business`](src/main/java/com/opencop/core/sdk/business) contains reusable
  provider-neutral policies, factories, and models.
- [`ports`](src/main/java/com/opencop/core/sdk/ports) contains contracts used at
  application and integration boundaries, including brokers, caches, decoders,
  persistence, mappers, secrets, sockets, and media stores.
- [`technical`](src/main/java/com/opencop/core/sdk/technical) contains reusable
  adapters and framework integration. Services choose which of these
  components to import.

## Payloads and mapping

The mapping engine can extract records from CSV, JSON, XML, and KML and apply
the configured transformation operations before building mapped features. Its
main entry point is
[`MapperFacade`](src/main/java/com/opencop/core/sdk/technical/mapper/MapperFacade.java);
the extractor and transformer implementations are under
[`technical/mapper/engine`](src/main/java/com/opencop/core/sdk/technical/mapper/engine).
The checked-in KML/KMZ mapper profile is
[`kml+kmz.v1.json`](src/main/resources/profiles/mappers/kml+kmz.v1.json).

## Integration support

The SDK provides ports and selected implementations for common service
integrations:

- Kafka broker publication and provider configuration under
  [`technical/gateway/broker`](src/main/java/com/opencop/core/sdk/technical/gateway/broker);
- Redis-backed cache and historical-job operations under
  [`technical/gateway/cache`](src/main/java/com/opencop/core/sdk/technical/gateway/cache)
  and
  [`technical/gateway/history`](src/main/java/com/opencop/core/sdk/technical/gateway/history);
- PostgreSQL repositories under
  [`technical/gateway/db`](src/main/java/com/opencop/core/sdk/technical/gateway/db);
- Azure Blob Storage adapters under
  [`technical/gateway/blob`](src/main/java/com/opencop/core/sdk/technical/gateway/blob)
  and
  [`technical/gateway/media`](src/main/java/com/opencop/core/sdk/technical/gateway/media);
  and
- Vault KV v2 secret storage under
  [`technical/gateway/secrets`](src/main/java/com/opencop/core/sdk/technical/gateway/secrets).

Database schema changes shared by the services are versioned as
[`Flyway migrations`](src/main/resources/db/migration).

## Security

[`technical/security`](src/main/java/com/opencop/core/sdk/technical/security)
contains the shared OpenCOP principal, JWT authority conversion, audience
validation, Spring Security wiring, and Redis session configuration. The
services supply their own admission and authorization behavior around these
building blocks.

## Legacy Processor plugin API compatibility

The types under
[`ports/plugin`](src/main/java/com/opencop/core/sdk/ports/plugin) remain part of
the SDK as a compatibility surface for consumers of the legacy annotated
Processor plugin API. That API is separate from startup integration capability
providers, which contribute ordinary Spring beans through the contracts under
[`business/model/integration/capability`](src/main/java/com/opencop/core/sdk/business/model/integration/capability).
This repository does not currently provide a runtime that discovers or deploys
external Processor plugins.

## Connection mapping declarations

Integration providers contribute `ConnectionMappingContribution` beans through
the `CONNECTION_MAPPING` capability. Core indexes each declaration by the exact
integration ID, schema version, and protocol. A declaration lists permitted
mapper ID and framing ID pairs; mapper versions are resolved separately from
the integration schema. TAK schema version 1 permits its CoT mapper with its
CoT delimiter for TCP. A provider that declares a connection protocol must
register one mapping rule for that protocol.
An identity-only integration with no connection protocol needs no mapping rule.

## Build and test

The module compiles for Java 25, as configured by
[`opencop-parent/pom.xml`](../opencop-parent/pom.xml). Run its test suite with
the repository Maven wrapper:

```bash
../mvnw test
```
