// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { getElementKey, type Element } from "@opencop/elements";

/**
 * Drop elements whose exact store keys were deleted. Returns `null` when
 * nothing matched, so a caller can skip the write entirely.
 *
 * Matching is on `element.key` (`source|id`) — exact, so two historical
 * versions of one object prune independently. This is deliberately **not**
 * `getElementKey` (`source|objectId`), which would collapse them into one.
 */
export function pruneDeletedElements(
    elements: readonly Element[],
    deletedKeys: ReadonlySet<string>,
): Element[] | null {
    const remaining = elements.filter((element) => !deletedKeys.has(element.key));
    return remaining.length === elements.length ? null : remaining;
}

/**
 * Forget checked entries with no surviving element behind them.
 *
 * Checked state is keyed by `getElementKey` (`source|objectId`), not by the
 * exact key {@link pruneDeletedElements} filters on, so two historical versions
 * of one object share a single tick. The two key functions must stay different;
 * matching both the same way silently merges or splits rows.
 */
export function pruneCheckedKeys(
    checkedKeys: ReadonlySet<string>,
    remaining: readonly Element[],
): Set<string> {
    const remainingKeys = new Set(remaining.map(getElementKey));
    return new Set([...checkedKeys].filter((key) => remainingKeys.has(key)));
}
