#!/usr/bin/env bash
# Copyright 2026 NexTech Solutions LLC
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

# derive-app-version.sh
# Derives the appVersion from APP_VERSION env or branch name.
# Output: version string on stdout, or nothing when unset.
# Warnings go to stderr.

app_version="${APP_VERSION:-}"
branch="${1:-}"

# If APP_VERSION is non-empty
if [[ -n "${app_version}" ]]; then
  if [[ "${app_version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "${app_version}"
    exit 0
  else
    echo "WARNING: APP_VERSION='${app_version}' is not strict X.Y.Z; appVersion will be UNSET (no branch fallback)" >&2
    exit 0
  fi
fi

# Else if branch matches release/v?X.Y.Z(-YYYY-MM-DD)?
if [[ -n "${branch}" ]]; then
  if [[ "${branch}" =~ ^release/v?([0-9]+\.[0-9]+\.[0-9]+)(-[0-9]{4}-[0-9]{2}-[0-9]{2})?$ ]]; then
    echo "${BASH_REMATCH[1]}"
    exit 0
  fi
fi

# Unset
exit 0
