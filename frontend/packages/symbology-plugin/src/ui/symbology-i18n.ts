// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { TFunction } from "i18next";

/**
 * A `t` bound to both namespaces symbology reads: its own `symbology` bundle
 * (catalog labels and the wizard's strings, prefixed `symbology:`) and the host's
 * `translation` for shared vocabulary like `common.*`.
 *
 * Named here so the helpers that take `t` as a parameter declare the same binding
 * their callers have — and so the day symbology's bundle ships from the plugin,
 * this one alias is what changes rather than every signature.
 */
export type SymbologyTFunction = TFunction<["translation", "symbology"]>;
