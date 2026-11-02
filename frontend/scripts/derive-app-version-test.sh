#!/usr/bin/env bash
# Copyright 2026 NexTech Solutions LLC
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

# derive-app-version-test.sh
# Vector table test for derive-app-version.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DERIVE_SCRIPT="${SCRIPT_DIR}/derive-app-version.sh"

failed=0
total=0

run_test() {
  local desc="$1"
  local expected="$2"
  local app_version="$3"
  local branch="$4"

  total=$((total + 1))
  local actual
  actual=$(APP_VERSION="${app_version}" "${DERIVE_SCRIPT}" "${branch}" 2>/dev/null || true)

  if [[ "${actual}" == "${expected}" ]]; then
    echo "PASS: ${desc}"
  else
    echo "FAIL: ${desc}"
    echo "      expected: '${expected}'"
    echo "      actual:   '${actual}'"
    failed=$((failed + 1))
  fi
}

# Vector table (input → expected stdout)
run_test "APP_VERSION='' branch='release/0.3.0-2026-07-02'" "0.3.0" "" "release/0.3.0-2026-07-02"
run_test "APP_VERSION='' branch='release/v0.3.0-2026-07-02'" "0.3.0" "" "release/v0.3.0-2026-07-02"
run_test "APP_VERSION='' branch='release/0.3.0'" "0.3.0" "" "release/0.3.0"
run_test "APP_VERSION='' branch='main'" "" "" "main"
run_test "APP_VERSION='' branch='HEAD'" "" "" "HEAD"
run_test "APP_VERSION='' branch=''" "" "" ""
run_test "APP_VERSION='' branch='release/0.3.0-rc1'" "" "" "release/0.3.0-rc1"
run_test "APP_VERSION='0.3.0' branch='main'" "0.3.0" "0.3.0" "main"
run_test "APP_VERSION='0.3' branch='release/0.4.0-2026-08-01'" "" "0.3" "release/0.4.0-2026-08-01"
run_test "APP_VERSION='v0.3.0' branch=''" "" "v0.3.0" ""

echo ""
if [[ ${failed} -eq 0 ]]; then
  echo "ALL VECTORS PASS"
  exit 0
else
  echo "${failed} vector(s) failed"
  exit 1
fi
