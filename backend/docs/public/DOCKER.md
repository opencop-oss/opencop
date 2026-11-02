# Local Docker Builds

The repository uses `docker-build.sh` as a wrapper around
`docker buildx build --load`. Maven runs inside the build containers, so image
builds do not require a host JDK, host Maven installation, local Maven
artifacts, or `~/.m2/settings.xml`.

Docker and the Docker Buildx CLI plugin must be available.

## Build all services

From the repository root, run:

```bash
./docker-build.sh
```

With no arguments, the script rebuilds `opencop-base:latest` and builds the four
service images in parallel:

| Module | Image |
| --- | --- |
| `core` | `opencop-core` |
| `connect` | `opencop-connect` |
| `processor` | `opencop-processor` |
| `websocket` | `opencop-websocket` |

The default service tag is `0.2.0-SNAPSHOT`. Show the complete command help
with:

```bash
./docker-build.sh --help
```

## Select build targets

Service targets use `module:image` form.

Build only Core with the current local base image:

```bash
./docker-build.sh core:opencop-core
```

Rebuild the base image before building Core:

```bash
./docker-build.sh --build-base core:opencop-core
```

Build only the base image:

```bash
./docker-build.sh --build-base
```

Build selected services one at a time:

```bash
./docker-build.sh --sequential \
  core:opencop-core \
  processor:opencop-processor
```

## Tags and build identity

Set `OPENCOP_IMAGE_TAG` to change the tag on every requested service image:

```bash
OPENCOP_IMAGE_TAG=local-test ./docker-build.sh core:opencop-core
```

Set `APP_VERSION` to a strict `X.Y.Z` value to stamp the application version:

```bash
APP_VERSION=1.2.3 ./docker-build.sh core:opencop-core
```

When `APP_VERSION` is unset, the
[`derive-app-version.sh`](../../scripts/derive-app-version.sh)
helper accepts a `release/X.Y.Z`, `release/vX.Y.Z`, or matching dated release
branch. Other branch names leave the application version unset and produce a
warning.

The script also passes the Git SHA, full revision, detected branch, and UTC
build date to the service image.

## Cache and shared modules

`opencop-base:latest` contains the installed `opencop-parent` and `sdk`
artifacts used by service builds. Rebuild the base after either directory
changes:

```bash
./docker-build.sh --build-base core:opencop-core
```

A service-only build deliberately reuses the current local base image. The
script warns when it sees uncommitted shared-module changes.

Use `--clean` or `--no-cache` to bypass Docker layer cache:

```bash
./docker-build.sh --clean
```

This still allows BuildKit cache mounts, including the container-side Maven
repository, to be reused. It does not remove images or refresh a stale base
image for a service-only build.

## Parallel builds and logs

Multiple service targets run in parallel by default. Set `MAX_JOBS` to a
positive integer to limit concurrent builds:

```bash
MAX_JOBS=2 ./docker-build.sh
```

Parallel runs write one log per service under a timestamped
`build-logs/<timestamp>/` directory. Before a parallel run, the script removes
directories older than the newest five.

Use `--sequential` when the machine cannot comfortably run several builders at
once.

## Troubleshooting

Verify Buildx:

```bash
docker buildx version
```

If Buildx is installed outside the locations checked by the script, set
`DOCKER_BUILDX`:

```bash
DOCKER_BUILDX=/path/to/docker-buildx ./docker-build.sh core:opencop-core
```

For stable, detailed BuildKit output, run a single target with plain progress:

```bash
./docker-build.sh --progress=plain core:opencop-core
```

For parallel builds, inspect the service log named in a `[FAIL]` message.

If a service build behaves as though shared Java changes are missing, rebuild
with `--build-base`. If Docker layer cache itself is suspect, add `--clean`.
