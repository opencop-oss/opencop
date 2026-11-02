// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it } from "vitest";
import { useSelectedTerrainStore } from "./selected-terrain.store";

function actions() {
    return useSelectedTerrainStore.getState().actions;
}

function state() {
    const { terrainId, textureId } = useSelectedTerrainStore.getState();
    return { terrainId, textureId };
}

describe("selected-terrain store", () => {
    beforeEach(() => {
        actions().reset();
    });

    describe("reconcile", () => {
        it("keeps selections whose tiles are still offered", () => {
            actions().setTerrainId("terrain-1");
            actions().setTextureId("texture-1");

            actions().reconcile(["terrain-1", "texture-1", "other"]);

            expect(state()).toEqual({ terrainId: "terrain-1", textureId: "texture-1" });
        });

        it("drops a terrain whose tile has been disabled, leaving the texture alone", () => {
            actions().setTerrainId("terrain-1");
            actions().setTextureId("texture-1");

            actions().reconcile(["texture-1"]);

            expect(state()).toEqual({ terrainId: undefined, textureId: "texture-1" });
        });

        it("drops both when neither tile is offered any more", () => {
            actions().setTerrainId("terrain-1");
            actions().setTextureId("texture-1");

            actions().reconcile([]);

            expect(state()).toEqual({ terrainId: undefined, textureId: undefined });
        });

        it("returns the identical state when nothing changes, so subscribers do not re-render", () => {
            actions().setTerrainId("terrain-1");
            const before = useSelectedTerrainStore.getState();

            actions().reconcile(["terrain-1"]);

            expect(useSelectedTerrainStore.getState()).toBe(before);
        });

        it("is a no-op when nothing is selected", () => {
            actions().reconcile([]);

            expect(state()).toEqual({ terrainId: undefined, textureId: undefined });
        });
    });
});
