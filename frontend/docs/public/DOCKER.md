# OpenCOP Web Docker Builds

This document describes the web service image build contract. It is intended to be readable by humans and explicit enough for automation.

## Quick Start

Build the web image from the directory containing `docker-build.sh`:

```bash
./docker-build.sh
```

Or via npm (alias for the same script):

```bash
npm run build
```

Show script usage:

```bash
./docker-build.sh --help
```

## Build Contract

The Vite production bundle is built **inside** Docker. Image builds do not require:

- host Node
- host npm
- host `node_modules`
- a host Vite install

`docker-build.sh` is a thin convenience wrapper around `docker buildx build --load`. Its responsibilities are:

- apply `OPENCOP_IMAGE_TAG`;
- pass build identity into the image (`BUILD_MODE`, `BUILD_GIT_SHA`, `APP_VERSION`, `BUILD_REVISION`, `BUILD_BRANCH`, `BUILD_DATE`);
- derive `APP_VERSION` from explicit `APP_VERSION=X.Y.Z` or a `release/v?X.Y.Z[-YYYY-MM-DD]` branch name;
- warn when `APP_VERSION` is unset so operators know to stamp release builds explicitly;
- apply OCI labels for revision/build date, plus version and branch labels when those values are set;
- forward `BUILD_NODE_OPTIONS` when set (Node heap workaround for the builder stage);
- forward `--clean` / `--no-cache` and `--progress=...` to `docker buildx build`;
- default `--progress=plain` when `CI` is set.

It should not install host Node, run host npm, or copy local `node_modules` into the build context.

## Default Target

The default service set is:

| Module | Image |
| ------ | ----- |
| `opencop-web` | `opencop-web` |

The image is tagged as:

```text
opencop-web:${OPENCOP_IMAGE_TAG}
```

`OPENCOP_IMAGE_TAG` defaults to `0.2.0-SNAPSHOT`, the local fallback tag for the web image.

## Common Commands

Build the web image with Docker cache:

```bash
./docker-build.sh
```

Build without Docker layer cache:

```bash
./docker-build.sh --clean
```

Build with a custom image tag:

```bash
OPENCOP_IMAGE_TAG=phase4-test ./docker-build.sh
```

Stamp a release app version into the image metadata and public `index.html` comment:

```bash
APP_VERSION=0.0.0 ./docker-build.sh
```

Build for local development (uses `.env.development` instead of `.env.default`):

```bash
BUILD_MODE=development ./docker-build.sh
```

Raise the builder-stage Node heap (OOM workaround during `vite build`):

```bash
BUILD_NODE_OPTIONS=--max-old-space-size=6144 ./docker-build.sh
```

Print stable BuildKit output for debugging:

```bash
./docker-build.sh --progress=plain
```

## Raw Docker Commands

Build the web image directly with `docker buildx`:

```bash
APP_VERSION=0.0.0
BUILD_REVISION="$(git rev-parse HEAD 2>/dev/null || echo '')"
BUILD_BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '')"
BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

docker buildx build \
  -f docker/Dockerfile \
  --build-arg BUILD_MODE=default \
  --build-arg BUILD_GIT_SHA="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)" \
  --build-arg APP_VERSION="$APP_VERSION" \
  --build-arg BUILD_REVISION="$BUILD_REVISION" \
  --build-arg BUILD_BRANCH="$BUILD_BRANCH" \
  --build-arg BUILD_DATE="$BUILD_DATE" \
  --label "org.opencontainers.image.version=$APP_VERSION" \
  --label "com.opencop.image.branch=$BUILD_BRANCH" \
  -t opencop-web:0.2.0-SNAPSHOT \
  --load \
  .
```

With the OOM workaround:

```bash
docker buildx build \
  -f docker/Dockerfile \
  --build-arg BUILD_MODE=default \
  --build-arg BUILD_GIT_SHA="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)" \
  --build-arg BUILD_NODE_OPTIONS=--max-old-space-size=6144 \
  -t opencop-web:0.2.0-SNAPSHOT \
  --load \
  .
```

## Script Options

| Option | Behavior |
| ------ | -------- |
| `--clean`, `--no-cache` | Pass `--no-cache` to `docker buildx build`. |
| `--progress=auto\|plain\|tty\|rawjson` | Pass Docker BuildKit progress mode through to `docker buildx build`. |
| `--help`, `-h` | Print script usage. |

When `CI` is set, the script defaults to `--progress=plain` so build logs are stable text instead of compact terminal UI output. An explicit `--progress=...` option overrides that default.

## Build Args

The `docker/Dockerfile` accepts the following build args, all forwarded by `docker-build.sh`:

| Arg | Required | Default | Purpose |
| --- | -------- | ------- | ------- |
| `BUILD_MODE` | yes | `default` (script default) | Vite mode -> selects `.env.<mode>` (`default`, `development`, ...) |
| `BUILD_GIT_SHA` | no | `unknown` | Git short SHA shown in the admin footer |
| `APP_VERSION` | no | unset | Strict `X.Y.Z` app version baked into `index.html` and exposed as `import.meta.env.VITE_APP_VERSION` |
| `BUILD_REVISION` | no | unset | Full source revision for OCI metadata |
| `BUILD_BRANCH` | no | unset | Source branch for OCI metadata |
| `BUILD_DATE` | no | unset | RFC 3339 UTC image build timestamp for OCI metadata |
| `BUILD_NODE_OPTIONS` | no | `--max-old-space-size=4096` | `NODE_OPTIONS` for the builder stage |

`NODE_ENV` inside the builder is always `production`; `BUILD_MODE` only selects which `.env.<mode>` file Vite reads.

The Dockerfile sets `org.opencontainers.image.revision` and `org.opencontainers.image.created` from `BUILD_REVISION` and `BUILD_DATE`. `docker-build.sh` adds `org.opencontainers.image.version` and `com.opencop.image.branch` only when the corresponding values are non-empty, so unset builds do not get empty-string version or branch labels. The final stage inherits `caddy:2.11-alpine`, and Docker cannot remove labels inherited from a base image, so `docker inspect` also shows Caddy's own `org.opencontainers.image.*` labels (including Caddy's `version` on builds where OpenCOP stamps none). That is a known, accepted limitation: image tags and the labels above are the OpenCOP identity surfaces, and an OpenCOP-stamped key always overrides the inherited value. The generated `index.html` contains a public-safe `opencop-web` comment with the short build SHA and, when set, `appVersion`.

**Browser title:** The `<title>` is set via `VITE_APP_TITLE` in Vite env files. The committed `.env` provides the default (`OpenCOP`); `.env.default` overrides it for `--mode default`. To customize the title for a specific deployment without editing committed files, create a gitignored `.env.default.local` — Vite loads it automatically when `BUILD_MODE=default` (the normal build):

```dotenv
# .env.default.local  (gitignored — never commit this)
VITE_APP_TITLE=OpenCOP Demo
```

**SECURITY:** `VITE_*` values are baked into the JS bundle and shipped to the browser in plain text. Never put secrets (tokens, passwords, API keys) in `.env.*` or `.env.*.local` files.

## Troubleshooting

If Docker says `buildx` is missing, verify Buildx directly:

```bash
docker buildx version
```

If Buildx is installed in a nonstandard location, set:

```bash
DOCKER_BUILDX=/path/to/docker-buildx ./docker-build.sh
```

If the script is not executable:

```bash
chmod +x docker-build.sh
```

If a build fails and the output is too compact, rerun with plain progress:

```bash
./docker-build.sh --progress=plain
```

If `vite build` runs out of memory inside the builder stage, raise the Node heap:

```bash
BUILD_NODE_OPTIONS=--max-old-space-size=6144 ./docker-build.sh
```

## Agent Notes

For the safest local web image rebuild, run:

```bash
./docker-build.sh
```

For a build failure that needs inspection, rerun with stable text output:

```bash
./docker-build.sh --progress=plain
```

Before changing script behavior, preserve these invariants:

- no host Node dependency for Docker image builds;
- no host npm dependency for Docker image builds (other than the optional `npm run build` alias, which only invokes the script);
- `docker-build.sh` remains a wrapper around `docker buildx build --load`;
- `OPENCOP_IMAGE_TAG`, `BUILD_MODE`, `APP_VERSION`, build metadata args, `BUILD_NODE_OPTIONS`, `--clean`, `--progress=...`, and the `CI`-aware progress default remain supported.
- `VITE_APP_TITLE` is read from Vite env files (`.env`, `.env.default`, `.env.default.local`), not from a Docker build-arg. Pass overrides via a gitignored `.env.default.local` — do not add a Docker `ARG`/`ENV` path for Vite vars.
