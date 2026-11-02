// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useState } from "react";

/**
 * A user-overridable choice from a fixed set — a display unit, or any other drawer-local
 * selection. Returns the effective value and a setter; the override is dropped (falling back to
 * `fallback`) whenever `resetKey` changes, e.g. a different element is picked or the
 * measurement/angle-unit setting switches.
 *
 * @param resetKey Identity of what the override belongs to; a change discards the override.
 * @param fallback Value in effect until the user overrides it, and after a reset.
 */
export function useUnitState<U extends string>(
    resetKey: string,
    fallback: U,
): [U, (value: U) => void] {
    const [choice, setChoice] = useState<{ key: string; value: U } | null>(null);
    const value = choice?.key === resetKey ? choice.value : fallback;
    return [value, (next) => setChoice({ key: resetKey, value: next })];
}
