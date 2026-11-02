// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Layer } from "@deck.gl/core";
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, render } from "~/test";
import { useRegisterLayers } from "~/features/map/DeckLayerContext";
import { DECK_PRIORITY_SELECTION_BOUNDARY } from "~/features/map/deck-layer-priorities";
import { MARCHING_ANTS_EXTENSION } from "~/features/map/extensions";
import { SelectionBoundaryLayer } from "./SelectionBoundaryLayer";
import { useAreaSelectionStore } from "./area-selection.store";

vi.mock("~/features/map/DeckLayerContext", () => ({
    useRegisterLayers: vi.fn(),
}));

const RING: [number, number][] = [
    [-104.99, 39.74],
    [-104.98, 39.74],
    [-104.98, 39.75],
];

function lastRegisteredLayers(): Layer[] {
    const calls = vi.mocked(useRegisterLayers).mock.calls;
    return (calls[calls.length - 1]?.[1] ?? []) as Layer[];
}

afterEach(() => {
    act(() => useAreaSelectionStore.getState().clear());
    vi.mocked(useRegisterLayers).mockClear();
});

describe("SelectionBoundaryLayer", () => {
    it("registers nothing when there is no selection", () => {
        render(<SelectionBoundaryLayer />);
        expect(lastRegisteredLayers()).toHaveLength(0);
    });

    it("draws the ring of the current lasso selection, closed", () => {
        act(() => useAreaSelectionStore.getState().select([], RING));
        render(<SelectionBoundaryLayer />);

        const layers = lastRegisteredLayers();
        expect(layers).toHaveLength(1);
        // Closed: the operator selected an area, so the path returns to its start.
        const path = (layers[0].props as { data: Array<{ path: [number, number][] }> }).data[0]
            .path;
        expect(path).toHaveLength(RING.length + 1);
        expect(path[path.length - 1]).toEqual(RING[0]);
    });

    it("registers at the lasso priority with the marching-ants extension", () => {
        act(() => useAreaSelectionStore.getState().select([], RING));
        render(<SelectionBoundaryLayer />);

        const calls = vi.mocked(useRegisterLayers).mock.calls;
        expect(calls[calls.length - 1]?.[2]).toBe(DECK_PRIORITY_SELECTION_BOUNDARY);
        expect(lastRegisteredLayers()[0].props.extensions).toContain(MARCHING_ANTS_EXTENSION);
    });

    it("stops drawing when the selection is cleared", () => {
        act(() => useAreaSelectionStore.getState().select([], RING));
        render(<SelectionBoundaryLayer />);
        expect(lastRegisteredLayers()).toHaveLength(1);

        act(() => useAreaSelectionStore.getState().clear());

        expect(lastRegisteredLayers()).toHaveLength(0);
    });
});
