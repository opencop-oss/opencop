#!/bin/bash
# Copyright 2026 NexTech Solutions LLC
# SPDX-License-Identifier: Apache-2.0

set -e

# Run TypeScript check and Vite production build
# Note: We use vite:build instead of build because npm run build is
# configured for Docker builds (used in deployment). CI just needs to
# verify the code compiles and bundles correctly.
echo "📦 Running TypeScript check and Vite production build..."

# The Vite production build exceeds Node's default ~2GB heap in CI (this direct
# `vite:build` path doesn't go through the Dockerfile, which sets the heap for
# its builder stage). Match the Dockerfile's default so the bundle fits in
# memory; respect an externally provided NODE_OPTIONS if one is already set.
export NODE_OPTIONS="${NODE_OPTIONS:---max-old-space-size=4096}"
npm run vite:build
