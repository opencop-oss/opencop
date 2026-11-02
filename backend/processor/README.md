# OpenCOP Processor

The Processor is the Kafka worker for OpenCOP. It maps incoming records to
GeoJSON, validates them, updates the real-time Redis cache, persists accepted
features to PostgreSQL/PostGIS, handles notifications and background jobs, and
runs historical-track jobs.

The application starts from
[`OpenCopProcessorApp`](src/main/java/com/opencop/processor/OpenCopProcessorApp.java).
Listener topics, consumer groups, concurrency, retries, and batch sizes are
defined under `opencop.messaging-broker.listeners` in
[`application.yml`](src/main/resources/application.yml).

## Processing flows

| Input | Processor path | Result |
| --- | --- | --- |
| `opencop.event.external.raw` | [`RawIngestConsumer`](src/main/java/com/opencop/processor/technical/spring/consumer/RawIngestConsumer.java) calls [`MapRawToGeoHandler`](src/main/java/com/opencop/processor/business/usecase/event/MapRawToGeoHandler.java). | Decoded and mapped features are published to `opencop.geo.mapped`; record-local failures go to the raw dead-letter path. |
| `opencop.geo.mapped` | [`GeoValidationConsumer`](src/main/java/com/opencop/processor/technical/spring/consumer/GeoValidationConsumer.java) calls [`ValidateGeoFeature`](src/main/java/com/opencop/processor/business/usecase/event/ValidateGeoFeature.java). | Valid features are published to `opencop.geo.validated`. |
| `opencop.geo.validated` | [`RealTimeConsumer`](src/main/java/com/opencop/processor/technical/spring/consumer/RealTimeConsumer.java) calls [`RealTimeGeoFeatureHandler`](src/main/java/com/opencop/processor/business/usecase/event/RealTimeGeoFeatureHandler.java). | Current feature state is written to Redis and cache notifications are published. |
| `opencop.geo.validated` | [`PersistenceConsumer`](src/main/java/com/opencop/processor/technical/spring/consumer/PersistenceConsumer.java) maps and stores records independently of the real-time consumer. | Accepted features are stored in PostgreSQL/PostGIS; invalid records use the persistence dead-letter path. |
| `opencop.notification` | [`NotificationConsumer`](src/main/java/com/opencop/processor/technical/spring/consumer/NotificationConsumer.java) calls [`UserNotificationHandler`](src/main/java/com/opencop/processor/business/usecase/notification/UserNotificationHandler.java). | User notifications are delivered through the configured notification gateway. |
| `opencop.jobs` | [`GeoCacheHydrationConsumer`](src/main/java/com/opencop/processor/technical/spring/consumer/GeoCacheHydrationConsumer.java) dispatches through `JobHandlerRegistry`. | Supported background jobs, including cache hydration, are executed. |
| `opencop.history.jobs` | [`HistoricalTracksConsumer`](src/main/java/com/opencop/processor/technical/spring/consumer/HistoricalTracksConsumer.java) calls [`HistoricalJobIngressService`](src/main/java/com/opencop/processor/technical/gateway/history/HistoricalJobIngressService.java). | Historical jobs are admitted, queued, executed, and tracked in Redis. |

[`ByodSourceConsumer`](src/main/java/com/opencop/processor/technical/spring/config/byod/ByodSourceConsumer.java)
reads source definitions from `opencop.byod.sources`, and
[`ByodConsumerManager`](src/main/java/com/opencop/processor/technical/spring/config/byod/ByodConsumerManager.java)
creates and removes the corresponding source consumers. The
`opencop.byod.runtime` and `opencop.byod.consumer` settings in `application.yml`
control status updates, polling, retry, and container limits.

## Runtime dependencies and configuration

Processor configuration uses Spring properties and the optional
`/run/secrets/` config tree declared in `application.yml`.

- Kafka is required for every processing flow. Set
  `opencop.messaging-broker.provider` with `MESSAGING_BROKER_PROVIDER` to
  `local` or `azure-event-hub`. Provider settings use the
  `opencop.messaging-broker.properties` prefix defined by
  [`LocalProviderProperties`](../sdk/src/main/java/com/opencop/core/sdk/technical/gateway/broker/kafka/properties/LocalProviderProperties.java)
  and
  [`AzureProviderProperties`](../sdk/src/main/java/com/opencop/core/sdk/technical/gateway/broker/kafka/properties/AzureProviderProperties.java).
- PostgreSQL/PostGIS persistence uses `opencop.db.url`, `opencop.db.username`,
  `opencop.db.password`, and `opencop.db.driver`, bound through
  `spring.datasource` in `application.yml`. It stores accepted features,
  notifications, and BYOD runtime observations.
- Redis uses `redis.host`, `redis.port`, `redis.password`, and `redis.ssl`.
  It stores the real-time cache and historical job state and carries cache,
  notification, and history pub/sub events.
- Blob access uses `cloud.blob.container-name`, `cloud.blob.account-name`,
  `cloud.blob.account-key`, and `cloud.blob.endpoint`. Raw import mapping and
  oversized dead-letter payload storage use the blob client in
  [`MapRawToGeoHandler`](src/main/java/com/opencop/processor/business/usecase/event/MapRawToGeoHandler.java)
  and
  [`DeadLetterPayloadStore`](src/main/java/com/opencop/processor/technical/spring/dlt/DeadLetterPayloadStore.java).
- Historical worker limits and TTLs use the `opencop.history.execution`
  prefix in `application.yml`.

The service listens on port `8080` by default. Spring Boot Actuator exposes
`GET /actuator/health`; `management.endpoints.web.exposure.include` in
`application.yml` limits web exposure to that endpoint.

## Build, test, and run

The project compiles for Java 25 as configured by
[`opencop-parent/pom.xml`](../opencop-parent/pom.xml). There is no root Maven
aggregator, so install the SDK before building this module:

```bash
(cd sdk && ../mvnw install)
(cd processor && ../mvnw test)
(cd processor && ../mvnw package)
```

With Kafka, PostgreSQL/PostGIS, Redis, blob, and provider properties supplied,
run the worker with:

```bash
cd processor
../mvnw spring-boot:run
```
