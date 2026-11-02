// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it, vi } from "vitest";
import { PointGeometry, type Element } from "@opencop/elements";
import type { MapManager } from "~/features/map/MapManager";
import {
    focusElementFromList,
    selectFromList,
    useEventsNavigationStore,
} from "./events-navigation.store";

function resetStore() {
    useEventsNavigationStore.setState({
        mode: "search",
        listOrigin: null,
        slideDirection: null,
        autoOpenDismissed: false,
        selectionAutoDismissed: false,
        listPickPending: false,
        listFocusTarget: null,
    });
}

beforeEach(resetStore);

function fakeElement(objectId: string): Element {
    return {
        objectId,
        source: "test-source",
        key: `test-source\0${objectId}`,
        geometry: new PointGeometry([5, 6]),
    } as unknown as Element;
}

function fakeMapManager() {
    return { selectElement: vi.fn() } as unknown as MapManager & {
        selectElement: ReturnType<typeof vi.fn>;
    };
}

describe("selectionCleared", () => {
    it("falls back to a list and clears slideDirection on a passive clear (mode still 'detail')", () => {
        // A raw pick (e.g. from a map click, not a list) leaves mode "detail"
        // with slideDirection "forward" — an empty-map click or similar then
        // clears the selection without any explicit list navigation call.
        useEventsNavigationStore.getState().select("selection");
        expect(useEventsNavigationStore.getState().mode).toBe("detail");

        useEventsNavigationStore.getState().selectionCleared();

        const { mode, listOrigin, slideDirection } = useEventsNavigationStore.getState();
        expect(mode).toBe("selection");
        expect(listOrigin).toBeNull();
        expect(slideDirection).toBeNull();
    });

    it("preserves a slideDirection already set by an explicit showList() call in the same tick", () => {
        // Mirrors EventDetail's back-to-list breadcrumb: showList() runs first
        // (moving mode off "detail" and choosing the slide direction), then
        // the selection clears synchronously after, firing this action.
        useEventsNavigationStore.getState().select("search");
        useEventsNavigationStore.getState().showList("search");
        expect(useEventsNavigationStore.getState().slideDirection).toBe("back");

        useEventsNavigationStore.getState().selectionCleared();

        const { mode, listOrigin, slideDirection } = useEventsNavigationStore.getState();
        expect(mode).toBe("search");
        expect(listOrigin).toBeNull();
        expect(slideDirection).toBe("back");
    });
});

describe("rawPickLanded", () => {
    it("opens detail with no slide and no breadcrumb, clearing any stale list-driven state", () => {
        // Leftover from an earlier list-driven detail on a different element.
        useEventsNavigationStore.getState().select("search");

        useEventsNavigationStore.getState().rawPickLanded();

        const { mode, listOrigin, slideDirection, listPickPending } =
            useEventsNavigationStore.getState();
        expect(mode).toBe("detail");
        expect(listOrigin).toBeNull();
        expect(slideDirection).toBeNull();
        expect(listPickPending).toBe(false);
    });
});

describe("showList — slides only when actually leaving a detail view", () => {
    it("does not slide when opening/revealing a list from closed", () => {
        useEventsNavigationStore.setState({ mode: "search" });

        useEventsNavigationStore.getState().showList("selection");

        expect(useEventsNavigationStore.getState().slideDirection).toBeNull();
    });

    it("slides back when the current mode is detail", () => {
        useEventsNavigationStore.setState({ mode: "detail" });

        useEventsNavigationStore.getState().showList("selection");

        expect(useEventsNavigationStore.getState().slideDirection).toBe("back");
    });
});

describe("showDetail — always a reveal, never a slide", () => {
    it("never sets a slide direction, even with one already in flight", () => {
        useEventsNavigationStore.setState({ slideDirection: "back" });

        useEventsNavigationStore.getState().showDetail();

        const { mode, slideDirection } = useEventsNavigationStore.getState();
        expect(mode).toBe("detail");
        expect(slideDirection).toBeNull();
    });
});

describe("dismissAllAutoOpen", () => {
    it("atomically dismisses both point-pick and lasso auto-open channels", () => {
        useEventsNavigationStore.getState().dismissAllAutoOpen();

        const { autoOpenDismissed, selectionAutoDismissed } = useEventsNavigationStore.getState();
        expect(autoOpenDismissed).toBe(true);
        expect(selectionAutoDismissed).toBe(true);
    });
});

describe("selectFromList", () => {
    it("records the list origin and delegates the raw pick to MapManager", () => {
        const mapManager = fakeMapManager();
        const element = fakeElement("e1");

        selectFromList(mapManager, element, "search");

        expect(mapManager.selectElement).toHaveBeenCalledWith(element);
        const { mode, listOrigin, slideDirection } = useEventsNavigationStore.getState();
        expect(mode).toBe("detail");
        expect(listOrigin).toBe("search");
        expect(slideDirection).toBe("forward");
    });

    it("keeps MapManager's own call unaware of the origin — only the store records it", () => {
        const mapManager = fakeMapManager();
        const element = fakeElement("e2");

        selectFromList(mapManager, element, "selection");

        // MapManager.selectElement takes a single argument — the origin never
        // reaches it, it's recorded in events-navigation.store beforehand.
        expect(mapManager.selectElement).toHaveBeenCalledOnce();
        expect(mapManager.selectElement).toHaveBeenCalledWith(element);
        expect(useEventsNavigationStore.getState().listOrigin).toBe("selection");
    });
});

describe("areaSelectionCleared", () => {
    it("forgets a detail's selection origin and nothing else", () => {
        useEventsNavigationStore.getState().select("selection");

        useEventsNavigationStore.getState().areaSelectionCleared();

        const { mode, listOrigin, slideDirection } = useEventsNavigationStore.getState();
        expect(mode).toBe("detail");
        expect(listOrigin).toBeNull();
        expect(slideDirection).toBe("forward");
    });

    it("falls the selection list back to search", () => {
        useEventsNavigationStore.getState().areaSelected();

        useEventsNavigationStore.getState().areaSelectionCleared();

        expect(useEventsNavigationStore.getState().mode).toBe("search");
    });

    it("leaves a search-origin detail alone", () => {
        useEventsNavigationStore.getState().select("search");

        useEventsNavigationStore.getState().areaSelectionCleared();

        const { mode, listOrigin } = useEventsNavigationStore.getState();
        expect(mode).toBe("detail");
        expect(listOrigin).toBe("search");
    });
});

describe("focusElementFromList", () => {
    it("records the focused element key, selects via MapManager, and pans the camera — without touching mode/listOrigin", () => {
        useEventsNavigationStore.setState({ mode: "selection", listOrigin: null });
        const mapManager = fakeMapManager();
        const element = fakeElement("e3");
        const flyTo = vi.fn();
        const fitBounds = vi.fn();

        focusElementFromList(mapManager, element, { flyTo, fitBounds });

        expect(mapManager.selectElement).toHaveBeenCalledWith(element);
        expect(flyTo).toHaveBeenCalledOnce();
        expect(fitBounds).not.toHaveBeenCalled();
        const { mode, listOrigin, listFocusTarget } = useEventsNavigationStore.getState();
        expect(mode).toBe("selection");
        expect(listOrigin).toBeNull();
        expect(listFocusTarget).toBe(element.key);
    });
});
