// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Layer } from "@deck.gl/core";
import { ScatterplotLayer } from "@deck.gl/layers";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Polygon, type DraftObject } from "@opencop/elements";
import { TerrainOutlineExtension } from "@opencop/layers";
import { vertexControlPoints } from "@opencop/tools";
import { act, render } from "~/test";
import { resetInteractionForTest, setSessionForTest } from "~/test/interaction";
import { useRegisterLayers } from "./DeckLayerContext";
import { DECK_PRIORITY_EDIT_HANDLES } from "./deck-layer-priorities";
import { EditHandlesLayer } from "./EditHandlesLayer";
import { useMapStore } from "./core/map.store";
import type { TerrainConfig } from "./core/slices/terrain/terrain.slice";
import { draftChanged } from "./interaction-runtime/session/commands";
import { getInteractionState, type Session } from "./interaction-runtime/session/interaction.store";

vi.mock("./DeckLayerContext", () => ({
    useRegisterLayers: vi.fn(),
}));

const TERRAIN: TerrainConfig = {
    elevationUrl: "elevation",
    textureUrl: "texture",
    meshMaxError: 4,
    elevationDecoder: { rScaler: 1, gScaler: 1, bScaler: 1, offset: 0 },
};

const shape = () => Polygon.create({ positions: [0, 0, 1, 0, 1, 1] });

function drawing(draft: DraftObject, placed: boolean): Session {
    return {
        kind: "draw",
        descriptorId: "test:unregistered",
        epoch: 1,
        params: {},
        custody: false,
        locked: placed ? 1 : 0,
        draft: { object: draft, placed },
    } as Session;
}

function editing(product: DraftObject): Session {
    return {
        kind: "edit",
        descriptorId: null,
        epoch: 1,
        product,
        ref: { objectId: product.key } as never,
        custody: false,
        params: {},
        isNew: false,
        sub: { hotspot: null, dragging: null },
    } as Session;
}

function lastRegistration() {
    const calls = vi.mocked(useRegisterLayers).mock.calls;
    const [id, layers, priority] = calls[calls.length - 1] ?? [];
    return { id, layers: (layers ?? []) as Layer[], priority };
}

const handles = () => lastRegistration().layers[0] as ScatterplotLayer;

afterEach(() => {
    act(() => {
        resetInteractionForTest();
        useMapStore.getState().resetTerrain();
    });
    vi.mocked(useRegisterLayers).mockClear();
});

describe("EditHandlesLayer", () => {
    it("registers no layers at the edit-handles priority while idle", () => {
        render(<EditHandlesLayer />);

        expect(lastRegistration()).toEqual({
            id: "edit-handles",
            layers: [],
            priority: DECK_PRIORITY_EDIT_HANDLES,
        });
    });

    it("registers nothing for a draft that is not placed yet", () => {
        setSessionForTest(drawing(shape(), false));
        render(<EditHandlesLayer />);

        expect(lastRegistration().layers).toEqual([]);
    });

    it("draws the control points of a placed draft and of an edited element", () => {
        const drawn = shape();
        setSessionForTest(drawing(drawn, true));
        render(<EditHandlesLayer />);

        expect(handles()).toBeInstanceOf(ScatterplotLayer);
        expect(handles().props.data).toEqual(vertexControlPoints(drawn));
        expect(handles().props.pickable).toBe(false);

        const edited = Polygon.create({ positions: [5, 5, 6, 5, 6, 6, 5, 6] });
        act(() => setSessionForTest(editing(edited)));
        expect(handles().props.data).toEqual(vertexControlPoints(edited));
    });

    it("stops drawing when the session ends", () => {
        setSessionForTest(editing(shape()));
        render(<EditHandlesLayer />);

        act(() => resetInteractionForTest());

        expect(lastRegistration().layers).toEqual([]);
    });

    it("floats the handles over terrain under a terrain-only id", () => {
        setSessionForTest(editing(shape()));
        render(<EditHandlesLayer />);

        expect(handles().id).toBe("edit-handles");
        expect(handles().props.extensions).toEqual([]);
        expect(handles().props).not.toHaveProperty("terrainDrawMode");

        act(() => useMapStore.getState().enableTerrain(TERRAIN));

        expect(handles().id).toBe("edit-handles-terrain");
        expect(handles().props.extensions).toHaveLength(1);
        expect(handles().props.extensions[0]).toBeInstanceOf(TerrainOutlineExtension);
        expect((handles().props as { terrainDrawMode?: string }).terrainDrawMode).toBe("offset");
    });

    it("redraws the handles of a draft mutated in place when the session rev bumps", () => {
        const product = shape();
        setSessionForTest(editing(product));
        render(<EditHandlesLayer />);
        const before = handles();

        product.geometry.setPos([2, 2], 0);
        act(() => draftChanged());

        expect(handles()).not.toBe(before);
        expect(handles().props.data).toEqual(vertexControlPoints(product));
        expect(handles().props.updateTriggers).toEqual({
            getPosition: getInteractionState().rev,
        });
    });
});
