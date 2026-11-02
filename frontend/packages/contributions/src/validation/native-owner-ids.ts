// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The owner ids the host's native plugins hold. No plugin may load under one, so an
 * owner id in a log line or a registry entry names exactly one owner. Declared beside
 * the rules, so checking a manifest needs no host.
 */
export const NATIVE_OWNER_IDS: readonly string[] = [
    "opencop.shapes",
    "opencop.lasso",
    "opencop.media-icons",
    "opencop.measure",
    "opencop.watchbox",
];
