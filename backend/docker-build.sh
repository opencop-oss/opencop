#!/usr/bin/env bash
# Copyright 2026 NexTech Solutions LLC
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

# Defaults
# opencop-base carries shared OpenCOP Maven artifacts. Rebuild it with
# --build-base after changing opencop-parent or sdk; service-only
# builds reuse the current opencop-base:latest.
BUILD_BASE=0
SEQUENTIAL=0
CACHE_ARGS=()
PROGRESS_ARGS=()
MAX_JOBS=${MAX_JOBS:-5}  # you can override with env if you want
OPENCOP_IMAGE_TAG=${OPENCOP_IMAGE_TAG:-0.2.0-SNAPSHOT}
SHARED_MODULE_DIRS=(opencop-parent sdk)
DEFAULT_MODULE_SPECS=(
  websocket:opencop-websocket
  core:opencop-core
  processor:opencop-processor
  connect:opencop-connect
)

MODULE_SPECS=()  # entries like "websocket:opencop-websocket"
BUILDX_CMD=()
START_TIME=$SECONDS
ARG_COUNT=$#

if [[ -n "${CI:-}" ]]; then
  PROGRESS_ARGS=(--progress=plain)
fi

usage() {
  cat <<EOF
Usage:
  $0 [options] [module:image ...]

Purpose:
  Thin wrapper around docker buildx build --load for OpenCOP Java images.
  It can rebuild the shared opencop-base:latest image, map module:image specs
  to service images, apply OPENCOP_IMAGE_TAG, and build multiple requested
  services in parallel by default.

Examples:
  # Build base + all local Java services in parallel using Docker cache
  $0

  # Build base image only
  $0 --build-base

  # Build the default set without Docker layer cache
  $0 --clean

  # Print stable BuildKit output for debugging
  $0 --progress=plain core:opencop-core

  # Build a single service using the current local opencop-base:latest
  $0 core:opencop-core

  # Build base + selected services in parallel
  $0 --build-base \\
    websocket:opencop-websocket \\
    core:opencop-core \\
    processor:opencop-processor \\
    connect:opencop-connect

  # Build base + selected services sequentially
  $0 --sequential --build-base \\
    websocket:opencop-websocket \\
    core:opencop-core

  # Build with a non-default image tag
  OPENCOP_IMAGE_TAG=my-tag $0 --build-base core:opencop-core

  # Stamp a release appVersion into image metadata and /v1/app-metadata
  APP_VERSION=0.0.0 $0 core:opencop-core

Notes:
  - with no args, builds opencop-base:latest plus: ${DEFAULT_MODULE_SPECS[*]}
  - module is the folder name (e.g. websocket)
  - image is the Docker image name prefix (e.g. opencop-websocket)
  - use --build-base after shared module changes: ${SHARED_MODULE_DIRS[*]}
  - service-only builds reuse the current local opencop-base:latest image
  - Docker image tag defaults to OPENCOP_IMAGE_TAG=${OPENCOP_IMAGE_TAG}
  - APP_VERSION=X.Y.Z stamps a release appVersion; otherwise release branches
    named release/v?X.Y.Z[-YYYY-MM-DD] are parsed conservatively
  - Multiple service builds run in parallel by default
  - Parallelism is limited by MAX_JOBS env (default: ${MAX_JOBS})
  - --sequential builds requested services one at a time
  - --progress=plain|auto|tty|rawjson passes through to docker buildx build
  - CI defaults to --progress=plain for stable logs
  - --clean/--no-cache passes --no-cache to docker buildx build. This bypasses
    Docker layer cache and also gives the builds an empty BuildKit Maven
    repository cache, which replaces the old one, so Maven downloads every
    dependency again. It does not remove images or make service-only builds
    fresh after shared-module changes.
  - MAVEN_REPO_CACHE_DIR=/path keeps the Maven repository in that folder
    between runs, for CI jobs whose Docker daemon starts empty: the folder is
    copied into the BuildKit Maven cache before the builds and back out after
    them, without com/opencop. A cache problem only costs a cold build.
EOF
}

# --- Arg parsing ---
if [[ ${ARG_COUNT} -eq 0 ]]; then
  BUILD_BASE=1
  MODULE_SPECS=("${DEFAULT_MODULE_SPECS[@]}")
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build-base)
      BUILD_BASE=1
      shift
      ;;
    --sequential)
      SEQUENTIAL=1
      shift
      ;;
    --clean|--no-cache)
      CACHE_ARGS=(--no-cache)
      shift
      ;;
    --progress=auto|--progress=plain|--progress=tty|--progress=rawjson)
      PROGRESS_ARGS=("$1")
      shift
      ;;
    --progress=*)
      echo "ERROR: unsupported progress mode: ${1#--progress=}" >&2
      echo "       expected one of: auto, plain, tty, rawjson" >&2
      usage
      exit 1
      ;;
    --progress)
      if [[ $# -lt 2 ]]; then
        echo "ERROR: --progress requires one of: auto, plain, tty, rawjson" >&2
        usage
        exit 1
      fi
      case "$2" in
        auto|plain|tty|rawjson)
          PROGRESS_ARGS=("--progress=$2")
          shift 2
          ;;
        *)
          echo "ERROR: unsupported progress mode: $2" >&2
          echo "       expected one of: auto, plain, tty, rawjson" >&2
          usage
          exit 1
          ;;
      esac
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    --*)
      echo "ERROR: unknown option: $1" >&2
      usage
      exit 1
      ;;
    -*)
      echo "ERROR: unknown option: $1" >&2
      usage
      exit 1
      ;;
    *)
      # Expect module:image
      MODULE_SPECS+=("$1")
      shift
      ;;
  esac
done

if [[ ${ARG_COUNT} -gt 0 && ${#MODULE_SPECS[@]} -eq 0 && "${BUILD_BASE}" -eq 0 ]]; then
  BUILD_BASE=1
  MODULE_SPECS=("${DEFAULT_MODULE_SPECS[@]}")
fi

if ! [[ "${MAX_JOBS}" =~ ^[1-9][0-9]*$ ]]; then
  echo "ERROR: MAX_JOBS must be a positive integer, got '${MAX_JOBS}'" >&2
  exit 1
fi

find_buildx_command() {
  if docker buildx version >/dev/null 2>&1; then
    BUILDX_CMD=(docker buildx)
    return
  fi

  local candidate
  for candidate in \
    "${DOCKER_BUILDX:-}" \
    "${HOME:-}/.docker/cli-plugins/docker-buildx" \
    "/Applications/Docker.app/Contents/Resources/cli-plugins/docker-buildx" \
    "/usr/local/lib/docker/cli-plugins/docker-buildx" \
    "/usr/local/libexec/docker/cli-plugins/docker-buildx" \
    "/usr/lib/docker/cli-plugins/docker-buildx" \
    "/usr/libexec/docker/cli-plugins/docker-buildx"
  do
    if [[ -n "${candidate}" && -x "${candidate}" ]]; then
      BUILDX_CMD=("${candidate}")
      return
    fi
  done

  echo "ERROR: docker buildx is required for BuildKit cache mounts, but no buildx command was found." >&2
  echo "       Run 'docker buildx version' to verify Docker Buildx." >&2
  echo "       Make sure Docker Desktop or the Docker daemon is running and that the Buildx CLI plugin is installed." >&2
  echo "       If Buildx is installed in a nonstandard location, set DOCKER_BUILDX=/path/to/docker-buildx." >&2
  exit 1
}

find_buildx_command

print_build_context() {
  echo ">>> Docker build wrapper: docker buildx build --load"
  echo ">>> Image tag: ${OPENCOP_IMAGE_TAG}"

  if [[ ${#CACHE_ARGS[@]} -gt 0 ]]; then
    echo ">>> Cache: Docker layer cache disabled (--no-cache); the Maven repository cache starts empty."
  else
    echo ">>> Cache: Docker layer cache enabled."
  fi

  if [[ ${#PROGRESS_ARGS[@]} -gt 0 ]]; then
    echo ">>> Progress: ${PROGRESS_ARGS[*]}"
  fi

  if [[ "${BUILD_BASE}" -eq 1 ]]; then
    if [[ ${#MODULE_SPECS[@]} -gt 0 ]]; then
      echo ">>> Base image: opencop-base:latest will be rebuilt before requested service builds."
    else
      echo ">>> Base image: opencop-base:latest will be rebuilt."
    fi
  elif [[ ${#MODULE_SPECS[@]} -gt 0 ]]; then
    echo ">>> Base image: service builds will reuse the current local opencop-base:latest."
    echo ">>> WARNING: Use --build-base after changes to ${SHARED_MODULE_DIRS[*]}; service-only builds can otherwise use stale shared artifacts." >&2

    local shared_status
    if shared_status=$(git status --porcelain -- "${SHARED_MODULE_DIRS[@]}" 2>/dev/null) && [[ -n "${shared_status}" ]]; then
      echo ">>> WARNING: Uncommitted shared-module changes detected; rebuild with --build-base for a fresh service image." >&2
      echo "${shared_status}" | sed 's/^/>>>   /' >&2
    fi
  fi
}

print_build_context

# --- Function to build the base image ---
build_base() {
  echo ">>> Building base image opencop-base:latest"

  # The Dockerfile carries a default fallback URL; a value from the environment (CI sets one) wins.
  "${BUILDX_CMD[@]}" build ${CACHE_ARGS[@]+"${CACHE_ARGS[@]}"} ${PROGRESS_ARGS[@]+"${PROGRESS_ARGS[@]}"} \
    ${MAVEN_CENTRAL_FALLBACK_URL:+--build-arg "MAVEN_CENTRAL_FALLBACK_URL=${MAVEN_CENTRAL_FALLBACK_URL}"} \
    -f Dockerfile.opencop-base \
    -t opencop-base:latest \
    --load \
    .
}

# --- Function to build a single service image ---
build_service() {
  local module_dir="$1"   # e.g. websocket
  local image_name="$2"   # e.g. opencop-websocket

  echo ">>> Building service '${image_name}' from module '${module_dir}'"

  local git_sha
  git_sha=$(git rev-parse --short HEAD 2>/dev/null || echo unknown)

  echo ">>>   Image tag   : ${OPENCOP_IMAGE_TAG}"
  echo ">>>   Git SHA     : ${git_sha}"

  local branch
  branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
  if [[ "${branch}" == "HEAD" || -z "${branch}" ]]; then
    branch="${CI_COMMIT_BRANCH:-}"
    [[ -n "${branch}" ]] && echo ">>>   Branch      : ${branch} (from CI_COMMIT_BRANCH)"
  fi
  local app_version
  app_version=$(APP_VERSION="${APP_VERSION:-}" scripts/derive-app-version.sh "${branch}")
  local revision build_date
  revision=$(git rev-parse HEAD 2>/dev/null || echo "")
  build_date=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  if [[ -z "${app_version}" ]]; then
    echo ">>> WARNING: appVersion is UNSET (branch='${branch:-none}'). Set APP_VERSION=X.Y.Z to stamp a version." >&2
  fi

  echo ">>>   App Version : ${app_version:-<unset>}"
  echo ">>>   Revision    : ${revision:-<unset>}"
  echo ">>>   Build Date  : ${build_date}"

  local module_build_args=()
  if [[ "${module_dir}" == "connect" ]]; then
    module_build_args+=(
      --build-arg RUNTIME_STAGE=runtime-glibc
      --build-arg JAVA_NATIVE_ACCESS_OPTIONS=--enable-native-access=ALL-UNNAMED
    )
    # Let Maven resolve the Bytedeco classifier inside the Docker target
    # platform. Override explicitly only for one-off diagnostics; future image
    # publishing should produce proper multi-arch images for cloud and Mac devs.
    if [[ -n "${BYTEDECO_CLASSIFIER:-}" ]]; then
      module_build_args+=(--build-arg "BYTEDECO_CLASSIFIER=${BYTEDECO_CLASSIFIER}")
    fi
  fi

  local build_args=()
  build_args=(
    --build-arg MODULE_DIR="${module_dir}"
    --build-arg BUILD_GIT_SHA="${git_sha}"
    --build-arg BUILD_REVISION="${revision}"
    --build-arg BUILD_BRANCH="${branch}"
    --build-arg BUILD_DATE="${build_date}"
    --build-arg APP_VERSION="${app_version}"
    ${module_build_args[@]+"${module_build_args[@]}"}
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
  if [[ -n "${app_version}" ]]; then
    build_args+=(--label "org.opencontainers.image.version=${app_version}")
  fi
  if [[ -n "${branch}" ]]; then
    build_args+=(--label "com.opencop.image.branch=${branch}")
  fi

  "${BUILDX_CMD[@]}" build ${CACHE_ARGS[@]+"${CACHE_ARGS[@]}"} ${PROGRESS_ARGS[@]+"${PROGRESS_ARGS[@]}"} \
    -f Dockerfile \
    "${build_args[@]}" \
    -t "${image_name}:${OPENCOP_IMAGE_TAG}" \
    --load \
    .
}

prune_old_logs() {
  local root="build-logs"
  mkdir -p "$root"

  # Get directories sorted newest -> oldest
  dirs=()
  while IFS= read -r entry; do
    dirs+=("$entry")
  done < <(ls -1 "$root" | sort -r)

  # If <= 5, nothing to delete
  if (( ${#dirs[@]} <= 5 )); then return; fi

  # Safety: root must not be empty or '/'
  if [[ -z "${root:-}" || "$root" == "/" ]]; then
    echo "ERROR: Log root directory invalid: '$root'"
    exit 1
  fi

  # Remove everything after the first 5
  for ((i=5; i<${#dirs[@]}; i++)); do
    dir_to_delete="${root}/${dirs[$i]}"

    # Ensure deletion target is inside root
    case "$dir_to_delete" in
      "$root"/*) ;;  # OK
      *)
        echo "ERROR: refusing to delete unexpected path: '$dir_to_delete'"
        exit 1
        ;;
    esac

    rm -rf -- "${dir_to_delete:?}"
  done
}

# --- Maven repository cache (opt-in) ---
# The Dockerfiles resolve dependencies into the BuildKit cache mount opencop-maven-repository. A RUN
# step can see that mount but the host cannot, and the mount is lost with the Docker daemon, so the
# host folder is copied into it before the builds and back out when this script exits. Neither copy
# changes the exit status: a cache problem only means the next build runs cold.
# A trailing slash would put the staging folders below inside the cache folder.
MAVEN_REPO_CACHE_DIR=${MAVEN_REPO_CACHE_DIR:-}
MAVEN_REPO_CACHE_DIR=${MAVEN_REPO_CACHE_DIR%/}
MAVEN_REPO_CACHE_IMAGE=alpine:3.20
MAVEN_REPO_CACHE_MOUNT=type=cache,id=opencop-maven-repository,target=/cache,sharing=locked
# A value unique to this run makes the copy steps miss the layer cache, so they always run. Not
# --no-cache: BuildKit gives a --no-cache build an empty cache mount and discards the old one.
MAVEN_REPO_CACHE_RUN="$(date +%s)-$$"

seed_maven_repo_cache() {
  if [[ ! -d "${MAVEN_REPO_CACHE_DIR}" || -z "$(ls -A "${MAVEN_REPO_CACHE_DIR}")" ]]; then
    echo ">>> Maven repository cache: ${MAVEN_REPO_CACHE_DIR} is empty; Maven downloads every dependency."
    return
  fi

  echo ">>> Maven repository cache: seeding from ${MAVEN_REPO_CACHE_DIR}"
  if ! "${BUILDX_CMD[@]}" build ${PROGRESS_ARGS[@]+"${PROGRESS_ARGS[@]}"} \
      --build-arg "MAVEN_REPO_CACHE_RUN=${MAVEN_REPO_CACHE_RUN}" \
      --output type=cacheonly \
      -f - \
      "${MAVEN_REPO_CACHE_DIR}" <<EOF
FROM ${MAVEN_REPO_CACHE_IMAGE}
ARG MAVEN_REPO_CACHE_RUN
RUN --mount=${MAVEN_REPO_CACHE_MOUNT} --mount=type=bind,target=/seed \
    cp -a /seed/. /cache/
EOF
  then
    echo ">>> WARNING: could not seed the Maven repository cache; Maven downloads every dependency." >&2
  fi
}

export_maven_repo_cache() {
  local staging previous
  # A staging folder of its own per run, so concurrent runs on one cache folder cannot clear each
  # other's export.
  staging="$(mktemp -d "${MAVEN_REPO_CACHE_DIR}.export.XXXXXX")" || return 1

  echo ">>> Maven repository cache: exporting to ${MAVEN_REPO_CACHE_DIR}"
  # com/opencop is rebuilt from source in every Maven step, so it is never worth keeping.
  if ! "${BUILDX_CMD[@]}" build ${PROGRESS_ARGS[@]+"${PROGRESS_ARGS[@]}"} \
      --build-arg "MAVEN_REPO_CACHE_RUN=${MAVEN_REPO_CACHE_RUN}" \
      --output "type=local,dest=${staging}" \
      - <<EOF
FROM ${MAVEN_REPO_CACHE_IMAGE} AS export
ARG MAVEN_REPO_CACHE_RUN
RUN --mount=${MAVEN_REPO_CACHE_MOUNT} \
    mkdir /export && cp -a /cache/. /export/ && rm -rf /export/com/opencop
FROM scratch
COPY --from=export /export /
EOF
  then
    echo ">>> WARNING: could not export the Maven repository cache; kept the previous copy." >&2
    rm -rf -- "${staging}"
    return
  fi

  # An empty export means no Maven step ran; keep the previous copy rather than erase it.
  if [[ -z "$(ls -A "${staging}")" ]]; then
    echo ">>> Maven repository cache: nothing to export; kept the previous copy."
    rm -rf -- "${staging}"
    return
  fi

  # Swap through a side folder so a failure leaves the previous copy in place. The checks are
  # explicit because this runs with errexit off (see the EXIT handler below).
  previous="${MAVEN_REPO_CACHE_DIR}.previous"
  rm -rf -- "${previous}"
  if [[ -e "${MAVEN_REPO_CACHE_DIR}" ]] && ! mv -- "${MAVEN_REPO_CACHE_DIR}" "${previous}"; then
    rm -rf -- "${staging}"
    return 1
  fi
  if ! mv -- "${staging}" "${MAVEN_REPO_CACHE_DIR}"; then
    [[ ! -e "${previous}" ]] || mv -- "${previous}" "${MAVEN_REPO_CACHE_DIR}"
    rm -rf -- "${staging}"
    return 1
  fi
  rm -rf -- "${previous}"
}

# The EXIT trap must not replace the build's exit status, and errexit stays on inside a trap.
export_maven_repo_cache_on_exit() {
  local status=$?
  export_maven_repo_cache \
    || echo ">>> WARNING: could not export the Maven repository cache; it may be incomplete." >&2
  exit "${status}"
}

if [[ -n "${MAVEN_REPO_CACHE_DIR}" ]]; then
  if [[ ${#CACHE_ARGS[@]} -gt 0 ]]; then
    # --no-cache builds start from an empty Maven cache mount, so seeding it would be wasted.
    echo ">>> Maven repository cache: not seeding, because --clean empties the Maven cache."
  else
    seed_maven_repo_cache
  fi
  trap export_maven_repo_cache_on_exit EXIT
fi

# --- Build base image if requested ---
if [[ "${BUILD_BASE}" -eq 1 ]]; then
  build_base
fi

# If no modules specified and only --build-base was used, we're done
if [[ ${#MODULE_SPECS[@]} -eq 0 ]]; then
  exit 0
fi

# --- Build services ---
if [[ "${SEQUENTIAL}" -eq 0 && ${#MODULE_SPECS[@]} -gt 1 ]]; then
  prune_old_logs

  LOG_ROOT="./build-logs"
  DATE_TAG=$(date +"%Y%m%d-%H%M%S")
  LOG_DIR="${LOG_ROOT}/${DATE_TAG}"
  mkdir -p "${LOG_DIR}"

  echo ">>> Building services in parallel (MAX_JOBS=${MAX_JOBS})"
  echo ">>> Logs will be stored in ${LOG_DIR}"
  pids=()
  failed=0
  job_count=0

  for spec in "${MODULE_SPECS[@]}"; do
    module_dir="${spec%%:*}"
    image_name="${spec##*:}"
    log_file="${LOG_DIR}/${image_name}.log"

    echo "[Started] ${image_name}"
    (
      if build_service "${module_dir}" "${image_name}" >"${log_file}" 2>&1; then
        echo "[OK] ${image_name}"
      else
        echo "[FAIL] ${image_name} (see ${log_file})"
        exit 1
      fi
    ) &
    pids+=($!)

    ((++job_count))

    # Simple batch limiter: wait every MAX_JOBS jobs
    if (( job_count % MAX_JOBS == 0 )); then
      for pid in "${pids[@]}"; do
        wait "$pid" || failed=1
      done
      pids=()
    fi
  done

  # Wait for any remaining jobs
  for pid in "${pids[@]}"; do
    wait "$pid" || failed=1
  done

  if [[ "$failed" -eq 1 ]]; then
    echo ">>> Some builds FAILED. Check logs in ${LOG_DIR}"
    exit 1
  fi
else
  echo ">>> Building services sequentially"
  for spec in "${MODULE_SPECS[@]}"; do
    module_dir="${spec%%:*}"
    image_name="${spec##*:}"
    echo "[Started] ${image_name}"
    if build_service "${module_dir}" "${image_name}"; then
      echo "[OK] ${image_name}"
    else
      echo "[FAIL] ${image_name}"
      exit 1
    fi
  done
fi

elapsed=$(( SECONDS - START_TIME ))
minutes=$(( elapsed / 60 ))
seconds=$(( elapsed % 60 ))
echo ">>> All builds completed successfully in ${minutes}m ${seconds}s."
