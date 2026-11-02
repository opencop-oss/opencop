// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { useMapManager } from "~/features/map/MapManagerContext";
import { useHasAreaSelection } from "~/features/selection/area-selection.store";
import { useMapbarStore } from "~/features/mapbar/mapbar.store";
import { SelectionPanel } from "~/features/selection/SelectionPanel";
import { EventsListSearchStart } from "./EventsListSearchStart";
import { selectFromList } from "./events-navigation.store";
import { EVENTS_PANEL_ID } from "./events-panel.const";

interface EventListProps {
    query: string;
    onQueryChange: (query: string) => void;
}

/** Dispatches between the area-selection list and the search list. Also the
 *  wiring point between
 *  SelectionPanel and the mapbar/events stores it needs: SelectionPanel takes
 *  onFocus/onClose as props rather than reading those stores directly, so it
 *  stays independently testable. */
export function EventList({ query, onQueryChange }: EventListProps): ReactElement {
    const hasAreaSelection = useHasAreaSelection();
    const mapManager = useMapManager();
    const closePanel = useMapbarStore((s) => s.closePanel);

    if (hasAreaSelection) {
        return (
            <SelectionPanel
                onFocus={(element) => selectFromList(mapManager, element, "selection")}
                onClose={closePanel}
                finalFocusEl={() =>
                    document.querySelector<HTMLElement>(`[data-panel-toggle="${EVENTS_PANEL_ID}"]`)
                }
            />
        );
    }

    return <EventsListSearchStart query={query} onQueryChange={onQueryChange} />;
}
