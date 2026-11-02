// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The set of source ids the broadcast-target field accepts.
 *
 * @param includeCurrentSource Whether the event's existing source is itself a
 *   valid value. True for any event that already lives remotely — the field
 *   displays that source, so it has to validate, even when it is a data-source
 *   id that appears in no layer list. This is about what the *field* may hold,
 *   not about whether the user may change it.
 */
export function getAllowedEventSourceIds(
    layerIds: readonly string[],
    currentSource: string,
    includeCurrentSource: boolean,
): string[] {
    if (!includeCurrentSource || !currentSource || currentSource === "local") {
        return [...layerIds];
    }

    return Array.from(new Set([...layerIds, currentSource]));
}
