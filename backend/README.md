# OpenCOP Java

This repository contains the Java services and shared library for OpenCOP, a
Common Operating Picture platform. Each service is its own Maven project and
inherits shared build settings from `opencop-parent`; there is no aggregator
POM at the repository root.

## Repository layout

### `core`

Core is the HTTP application for OpenCOP APIs, OIDC admission, authorization,
and application orchestration.

### `connect`

Connect reconciles assigned network sources, runs TCP, UDP, and RTSP/KLV
connectors, and forwards complete payloads to the processing pipeline.

- [Connect guide](connect/README.md)

### `processor`

Processor consumes Kafka records, decodes and maps supported payloads, persists
results, and runs historical-job workers.

- [Processor guide](processor/README.md)

### `websocket`

WebSocket delivers live, cached, historical, and notification data through the
raw OCMP Protobuf endpoint.

- [WebSocket guide](websocket/README.md)

### `sdk`

The SDK supplies shared models, policies, factories, ports, codecs, mapping,
security, persistence, and integration support used by the services.

- [SDK guide](sdk/README.md)

### `opencop-parent`

The parent POM centralizes Java 25, dependency versions, Maven plugin versions,
test controls, formatting, and toolchain settings inherited by the modules.
Its contract is defined in
[`opencop-parent/pom.xml`](opencop-parent/pom.xml).

## Local Java builds

The Maven projects compile for Java 25. Use the checked-in Maven wrapper and
run commands against an individual module. The module guides list their
verified test commands.

When the parent or SDK is not already present in the local Maven repository,
install them first:

```bash
./mvnw -f opencop-parent/pom.xml install
./mvnw -f sdk/pom.xml install
```

You can then run a service command from its directory, for example:

```bash
cd processor
../mvnw test
```

## Local Docker images

Docker image builds run Maven inside Docker and do not require a host JDK,
Maven installation, or Maven settings file.

Build the shared base image and all four service images:

```bash
./docker-build.sh
```

See the [Docker build guide](docs/public/DOCKER.md) for individual targets, cache
behavior, image tags, logs, and troubleshooting.
