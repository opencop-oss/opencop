# Copyright 2026 NexTech Solutions LLC
# SPDX-License-Identifier: Apache-2.0

# OpenCOP Web - Multi-stage build for serving static files with Caddy
#
# BUILD MODES (required):
#   BUILD_MODE determines which .env.[mode] file Vite uses.
#   Environment variables are baked into the JS bundle at build time.
#   NODE_ENV is always 'production' (optimized build) regardless of mode.
#
#   default      -> .env.default      (relative URLs)
#   development  -> .env.development  (localhost URLs)
#
# USAGE:
#   # Default-mode image
#   TAG=0.2.0-SNAPSHOT
#   docker build \
#     --build-arg BUILD_MODE=default \
#     --build-arg BUILD_GIT_SHA=$(git rev-parse --short HEAD) \
#     --build-arg BUILD_REVISION=$(git rev-parse HEAD) \
#     --build-arg BUILD_BRANCH=$(git rev-parse --abbrev-ref HEAD) \
#     --build-arg BUILD_DATE=$(date -u +%Y-%m-%dT%H:%M:%SZ) \
#     -t opencop-web:$TAG -f docker/Dockerfile .
#
#   # Development-mode image
#   docker build \
#     --build-arg BUILD_MODE=development \
#     --build-arg BUILD_GIT_SHA=$(git rev-parse --short HEAD) \
#     -t opencop-web:dev -f docker/Dockerfile .
#
#   # Optional: raise Node heap for the builder if a host needs more headroom
#   docker build \
#     --build-arg BUILD_MODE=default \
#     --build-arg BUILD_GIT_SHA=$(git rev-parse --short HEAD) \
#     --build-arg BUILD_NODE_OPTIONS=--max-old-space-size=6144 \
#     -t opencop-web:$TAG -f docker/Dockerfile .


######################################################################################
## Builder
## Bundles files to /app/dist but uses node_modules we don't want in the final image
######################################################################################

# The pinned Node release bundles an npm version satisfying the package engine
# requirement without a runtime self-upgrade.
FROM node:24.22.1-alpine AS build

WORKDIR /app

COPY package*.json ./
COPY .npmrc ./
COPY patches ./patches
COPY packages/ui/src/theme packages/ui/src/theme
COPY scripts/check-npm-lock-policy.js scripts/check-npm-lock-policy.js
COPY scripts/check-npm-lock-policy.allowlist.json scripts/check-npm-lock-policy.allowlist.json

RUN node scripts/check-npm-lock-policy.js
RUN npm ci

COPY . .

ARG BUILD_MODE
ARG BUILD_GIT_SHA=unknown
ARG APP_VERSION=
ARG BUILD_NODE_OPTIONS=--max-old-space-size=4096
ENV BUILD_GIT_SHA=${BUILD_GIT_SHA}
ENV APP_VERSION=${APP_VERSION}
ENV NODE_OPTIONS=${BUILD_NODE_OPTIONS}

# Vite will read .env and .env.[BUILD_MODE]
RUN npm exec vite -- build --mode ${BUILD_MODE}

# Vite copies public/ into dist/ keeping each file's mode from the build context,
# so a checkout made under a restrictive umask (e.g. 077) yields 0600 files that
# Caddy, running as `nobody`, answers with 403 — enablements.json among them.
RUN chmod -R a+rX dist

############################################################
## File Server - only needs Caddy and the built files
############################################################

FROM caddy:2.11-alpine

ARG APP_VERSION=
ARG BUILD_REVISION=
ARG BUILD_BRANCH=
ARG BUILD_DATE=
LABEL org.opencontainers.image.revision=${BUILD_REVISION} org.opencontainers.image.created=${BUILD_DATE}

COPY docker/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /usr/share/caddy/

# Create data/config dirs with proper permissions for non-root user
# (Caddy's TLS subsystem initializes even when auto_https is off)
RUN mkdir -p /data/caddy /config/caddy && \
    chown -R nobody:nobody /data /config

USER nobody

# Port exposed for deployments with optional upstream TLS termination.
EXPOSE 80

CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile"]
