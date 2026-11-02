// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, type ReactNode } from "react";
import { useMapManager } from "./MapManagerContext";
import { useStackedObjectSelectionStore } from "./stacked-object-selection.store";
import { useAreaSelectionStore } from "~/features/selection/area-selection.store";

/**
 * Removes deleted elements from both selection surfaces. Subscribes only
 * while a snapshot is active and matches the ElementStore's exact keys.
 *
 * Deletions only — additions and updates deliberately do not reshape an open
 * snapshot (a deleted row points at nothing; a stale field is cosmetic in a
 * transient surface). Stale expiry also arrives as a store `remove`
 * (ElementStore.sweep), so an element aging out prunes the snapshot and can
 * close the selector.
 */
export function StackedElementsEffect(): ReactNode {
    const mapManager = useMapManager();
    const prunePopover = useStackedObjectSelectionStore((s) => s.removeDeleted);
    const pruneAreaSelection = useAreaSelectionStore((s) => s.removeDeleted);

    useEffect(() => {
        const sub = mapManager.onStore("remove", (elements) => {
            const keys = new Set(elements.map((element) => element.key));
            prunePopover(keys);
            pruneAreaSelection(keys);
        });
        return () => sub.unsub();
    }, [mapManager, prunePopover, pruneAreaSelection]);

    return null;
}
