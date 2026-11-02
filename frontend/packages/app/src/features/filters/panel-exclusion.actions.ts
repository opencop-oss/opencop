// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useElementActions, useSourceActions } from "@opencop/ingest";
import { useCallback } from "react";
import { useFilterPanelStore } from "./filter-panel.store";

/**
 * Removes layers / data sources from the map panel, or adds them back. Removing also toggles the
 * source off so nothing stays on the map that the user can no longer reach from the panel.
 * Adding back leaves the source off; the user re-enables it from the panel.
 *
 * Deliberately does not use `useFilterPanelActions`: that hook subscribes to every source's sync
 * state, which would re-render each drawer button and table page on any ingest change.
 */
export function usePanelExclusionActions(): {
    setExcluded(ids: readonly string[], excluded: boolean): void;
} {
    // `hideSource` sets the hidden flag that `showSource` later clears; `disable` stops the stream
    // and purges the source's elements. Both are applied, as `useFilterPanelActions` does.
    // `hideSource` is deprecated, with no replacement yet.
    const { hideSource: hideElements } = useElementActions();
    const { disable } = useSourceActions();

    const setExcluded = useCallback(
        (ids: readonly string[], excluded: boolean) => {
            useFilterPanelStore.getState().setExcludedIds(ids, excluded);
            if (!excluded) return;

            const { hiddenSources, setHiddenSources } = useFilterPanelStore.getState();
            ids.filter((id) => !hiddenSources.includes(id)).forEach((id) => {
                setHiddenSources((prev) => [...prev, id]);
                hideElements(id);
                disable(id);
            });
        },
        [hideElements, disable],
    );

    return { setExcluded };
}
