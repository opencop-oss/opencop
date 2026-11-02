// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useState } from "react";
import { installBuiltInSubjects } from "~/features/contributions/install-built-in-subjects";

/**
 * Installs the native plugins when the authenticated app starts, before any
 * route renders: it must precede the routes among its siblings, so its render
 * installs before theirs reads a registry. Unmounting disposes the installation.
 *
 * The render-phase install is idempotent, so a discarded render or a repeat is a
 * no-op; the effect owns the lifetime, so a StrictMode remount reinstalls.
 */
export function NativeSubjectInstaller(): null {
    useState(installBuiltInSubjects);
    useEffect(() => installBuiltInSubjects(), []);
    return null;
}
