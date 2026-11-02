// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex } from "@chakra-ui/react";
import { useState, type ReactElement } from "react";
import { EventDetail } from "~/features/events/EventDetail";
import { EventList } from "~/features/events/EventList";
import { useEventsNavigationStore } from "~/features/events/events-navigation.store";
import { usePickedElementRef } from "~/features/map/use-picked-element.hook";

const SLIDE_DURATION = "220ms";

const slideAnimation: Record<"forward" | "back", string> = {
    forward: `slideInFromRight ${SLIDE_DURATION} ease-out both`,
    back: `slideInFromLeft ${SLIDE_DURATION} ease-out both`,
};

/**
 * The Events panel: a list (search results, or the area selection's working
 * set) or the selected element's detail.
 *
 * It owns no key. The panel is the presentation of a selection and follows
 * it — auto-opened by a pick or a lasso, it closes when Escape clears what
 * opened it; opened by hand, it returns to its list. Escape itself belongs to
 * the interaction runtime's ladder (`interaction-rules.md` Q8).
 */
export function EventsPanel(): ReactElement {
    const mode = useEventsNavigationStore((s) => s.mode);
    const slideDirection = useEventsNavigationStore((s) => s.slideDirection);
    const selectedRef = usePickedElementRef();
    // Deliberately local, not a store: search terms should be fresh each time
    // the panel is opened (see docs/best-practices/zustand/index.md "What to
    // persist"). This component stays mounted across list <-> detail (mapbar
    // always renders it under the single "events" id), so the query survives
    // that round trip without needing to live elsewhere.
    const [searchQuery, setSearchQuery] = useState("");

    // `mode` is the source of truth for navigation *intent* (e.g. "go back to
    // the list" despite a selection still lingering) but content still keys
    // off the real selection: `mode` can lag a render behind the selection
    // clearing (e.g. an empty-map click clears both, but only the reactive
    // sync effect updates `mode`), and EventList/SelectionPanel already
    // self-correct from the real lasso state. So detail only shows when both
    // agree.
    const showDetail = selectedRef !== null && mode === "detail";

    // Slide is Events' own internal list<->detail transition, not a generic
    // panel-open effect — scoped here (not InfoPanel) so opening any other
    // mapbar panel is never affected by a leftover slideDirection.
    return (
        <Flex
            key={showDetail ? "detail" : "list"}
            w="full"
            h="full"
            minH="0"
            animation={slideDirection ? slideAnimation[slideDirection] : undefined}
        >
            {showDetail ? (
                <EventDetail />
            ) : (
                <EventList query={searchQuery} onQueryChange={setSearchQuery} />
            )}
        </Flex>
    );
}
