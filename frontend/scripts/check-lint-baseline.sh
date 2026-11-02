#!/bin/bash
# Copyright 2026 NexTech Solutions LLC
# SPDX-License-Identifier: Apache-2.0

set -e

echo "🔍 Running ESLint..."

# In CI, ESLINT_CODE_QUALITY_REPORT names the GitLab Code Quality report path.
# eslint-formatter-gitlab writes that report file AND prints readable output, so
# select it when the var is set. Locally (unset) keep the plain lint output.
if [ -n "${ESLINT_CODE_QUALITY_REPORT:-}" ]; then
    npm run lint -- --format gitlab
else
    npm run lint
fi
