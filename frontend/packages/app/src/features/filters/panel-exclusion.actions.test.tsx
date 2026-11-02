// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook } from "~/test";
import { useFilterPanelStore } from "./filter-panel.store";
import { usePanelExclusionActions } from "./panel-exclusion.actions";

const { hideElements, disable } = vi.hoisted(() => ({ hideElements: vi.fn(), disable: vi.fn() }));

vi.mock("@opencop/ingest", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@opencop/ingest")>()),
    useElementActions: () => ({ hideSource: hideElements }),
    useSourceActions: () => ({ disable }),
}));

describe("usePanelExclusionActions", () => {
    beforeEach(() => {
        hideElements.mockClear();
        disable.mockClear();
        useFilterPanelStore.setState({ hiddenSources: [] });
        useFilterPanelStore.setState({ excludedIds: [] });
    });

    it("excluding lists the ids and turns each source off", () => {
        const { result } = renderHook(() => usePanelExclusionActions());

        result.current.setExcluded(["a", "b"], true);

        expect(useFilterPanelStore.getState().excludedIds).toEqual(["a", "b"]);
        expect(useFilterPanelStore.getState().hiddenSources).toEqual(["a", "b"]);
        expect(disable).toHaveBeenCalledWith("a");
        expect(disable).toHaveBeenCalledWith("b");
        expect(hideElements).toHaveBeenCalledTimes(2);
    });

    it("does not toggle off a source that is already off", () => {
        useFilterPanelStore.setState({ hiddenSources: ["a"] });
        const { result } = renderHook(() => usePanelExclusionActions());

        result.current.setExcluded(["a", "b"], true);

        expect(useFilterPanelStore.getState().hiddenSources).toEqual(["a", "b"]);
        expect(disable).toHaveBeenCalledTimes(1);
        expect(disable).toHaveBeenCalledWith("b");
    });

    it("adding back leaves the source off", () => {
        const { result } = renderHook(() => usePanelExclusionActions());
        result.current.setExcluded(["a"], true);
        disable.mockClear();

        result.current.setExcluded(["a"], false);

        expect(useFilterPanelStore.getState().excludedIds).toEqual([]);
        expect(useFilterPanelStore.getState().hiddenSources).toEqual(["a"]);
        expect(disable).not.toHaveBeenCalled();
    });
});
