#!/usr/bin/env bash
# Copyright 2026 NexTech Solutions LLC
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

# Keep the historical image tag as a backward-compatible default.
OPENCOP_IMAGE_TAG="${OPENCOP_IMAGE_TAG:-0.2.0-SNAPSHOT}"
BUILD_MODE="${BUILD_MODE:-default}"

# Allow callers to override the buildx invocation.
DOCKER_BUILDX="${DOCKER_BUILDX:-docker buildx}"

CACHE_FLAG=""        # set to --no-cache by --clean / --no-cache
PROGRESS_FLAG=""     # set by --progress=... or by CI default
EXTRA_ARGS=()        # any other args forwarded to docker buildx build

usage() {
  cat <<EOF
Usage:
  $0 [--clean|--no-cache] [--progress=auto|plain|tty|rawjson] [extra docker buildx args...]

Builds opencop-web:\$OPENCOP_IMAGE_TAG using docker/Dockerfile from this directory.
Thin wrapper around \`docker buildx build --load\`.

Environment overrides:
  OPENCOP_IMAGE_TAG   Image tag (backward-compatible default: 0.2.0-SNAPSHOT)
  BUILD_MODE          Vite mode -> .env.<mode> (default: default)
  APP_VERSION         Explicit strict X.Y.Z app version stamp. If unset, the
                      script derives from release/v?X.Y.Z[-YYYY-MM-DD] branches.
  BUILD_NODE_OPTIONS  NODE_OPTIONS for the builder stage
                      (e.g. --max-old-space-size=6144 for OOM workaround)
  DOCKER_BUILDX       Override buildx invocation (default: "docker buildx")
  CI                  When set, defaults --progress=plain for stable build logs

Options:
  --clean, --no-cache         Pass --no-cache to docker buildx build.
  --progress=MODE             Forward BuildKit progress mode (auto|plain|tty|rawjson).
  --help, -h                  Print this help.

Examples:
  # Default build
  $0

  # Verbose / debug build
  $0 --progress=plain

  # No-cache build
  $0 --clean

  # OOM workaround
  BUILD_NODE_OPTIONS=--max-old-space-size=6144 $0

  # Custom tag and mode
  OPENCOP_IMAGE_TAG=my-branch BUILD_MODE=development $0

  # Stamp a release appVersion into image metadata and index.html
  APP_VERSION=0.0.0 $0
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --help|-h)
      usage
      exit 0
      ;;
    --clean|--no-cache)
      CACHE_FLAG="--no-cache"
      shift
      ;;
    --progress=*)
      PROGRESS_FLAG="$1"
      shift
      ;;
    --progress)
      echo "ERROR: --progress requires =MODE form (e.g. --progress=plain)" >&2
      exit 1
      ;;
    *)
      EXTRA_ARGS+=("$1")
      shift
      ;;
  esac
done

# CI default: stable plain text logs unless the caller already set --progress.
if [[ -z "$PROGRESS_FLAG" && -n "${CI:-}" ]]; then
  PROGRESS_FLAG="--progress=plain"
fi

BUILD_GIT_SHA="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
BUILD_REVISION="$(git rev-parse HEAD 2>/dev/null || echo "")"
BUILD_BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")"
if [[ "${BUILD_BRANCH}" == "HEAD" || -z "${BUILD_BRANCH}" ]]; then
  BUILD_BRANCH="${CI_COMMIT_BRANCH:-}"
  [[ -n "${BUILD_BRANCH}" ]] && echo ">>> Branch      : ${BUILD_BRANCH} (from CI_COMMIT_BRANCH)"
fi
APP_VERSION_RESOLVED="$(APP_VERSION="${APP_VERSION:-}" scripts/derive-app-version.sh "${BUILD_BRANCH}")"
BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

if [[ -z "${APP_VERSION_RESOLVED}" ]]; then
  echo ">>> WARNING: appVersion is UNSET (branch='${BUILD_BRANCH:-none}'). Set APP_VERSION=X.Y.Z to stamp a version." >&2
fi

EXTRA_BUILD_ARGS=()
if [[ -n "${BUILD_NODE_OPTIONS:-}" ]]; then
  EXTRA_BUILD_ARGS+=(--build-arg "BUILD_NODE_OPTIONS=${BUILD_NODE_OPTIONS}")
fi

BUILD_METADATA_ARGS=(
  --build-arg "APP_VERSION=${APP_VERSION_RESOLVED}"
  --build-arg "BUILD_REVISION=${BUILD_REVISION}"
  --build-arg "BUILD_BRANCH=${BUILD_BRANCH}"
  --build-arg "BUILD_DATE=${BUILD_DATE}"
)

# Label strategy: image TAGS are the primary identity surface; these labels are
# supplementary, retag-proof provenance, stamped here because CI and on-host
# builds both route through this script. Docker cannot remove labels inherited
# from a base image (only override them key-by-key), and the ecosystem norm is
# to tolerate them, so `docker inspect` may also show base-image labels (e.g.
# Caddy's own org.opencontainers.image.* block on the web image, including its
# `version` on builds where OpenCOP stamps none). Known, accepted limitation:
# an OpenCOP-stamped key always overrides the inherited value, and OpenCOP
# emits version/branch labels only when the value exists (never empty strings).
LABEL_ARGS=()
if [[ -n "${APP_VERSION_RESOLVED}" ]]; then
  LABEL_ARGS+=(--label "org.opencontainers.image.version=${APP_VERSION_RESOLVED}")
fi
if [[ -n "${BUILD_BRANCH}" ]]; then
  LABEL_ARGS+=(--label "com.opencop.image.branch=${BUILD_BRANCH}")
fi

echo ">>> Building opencop-web:${OPENCOP_IMAGE_TAG} (mode=${BUILD_MODE}, sha=${BUILD_GIT_SHA})"
echo ">>> App Version : ${APP_VERSION_RESOLVED:-<unset>}"
echo ">>> Revision    : ${BUILD_REVISION:-<unset>}"
echo ">>> Branch      : ${BUILD_BRANCH:-<unset>}"
echo ">>> Build Date  : ${BUILD_DATE}"

# shellcheck disable=SC2086 # DOCKER_BUILDX may legitimately be "docker buildx"
$DOCKER_BUILDX build \
  ${CACHE_FLAG:+$CACHE_FLAG} \
  ${PROGRESS_FLAG:+$PROGRESS_FLAG} \
  --build-arg "BUILD_MODE=${BUILD_MODE}" \
  --build-arg "BUILD_GIT_SHA=${BUILD_GIT_SHA}" \
  ${BUILD_METADATA_ARGS[@]+"${BUILD_METADATA_ARGS[@]}"} \
  ${EXTRA_BUILD_ARGS[@]+"${EXTRA_BUILD_ARGS[@]}"} \
  ${LABEL_ARGS[@]+"${LABEL_ARGS[@]}"} \
  -t "opencop-web:${OPENCOP_IMAGE_TAG}" \
  -f docker/Dockerfile \
  --load \
  ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"} \
  .
