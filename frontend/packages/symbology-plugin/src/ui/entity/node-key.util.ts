// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/** A node's position in the tree, used as both the match-map and expansion key. */
export function nodeKey(
    symbolSet: string,
    entity?: string,
    entityType?: string,
    entitySubType?: string,
) {
    return [symbolSet, entity, entityType, entitySubType].filter(Boolean).join(":");
}
