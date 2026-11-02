// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { PickingInfo } from "@deck.gl/core";
import { ScatterplotLayer } from "@deck.gl/layers";
import { describe, expect, it } from "vitest";
import { Point } from "@opencop/elements";
import type { DeckPick } from "../../contract";
import { isStorePick, pickTargetOf } from "./pick-target";

const layer = new ScatterplotLayer({ id: "scene", data: [] });
const region = { type: "regions", key: "a" };

const pick = (on: unknown, ref?: DeckPick["ref"]): DeckPick => ({
    picking: { x: 3, y: 4, layer: on, object: {} } as unknown as PickingInfo,
    picks: [],
    ref,
});

describe("pickTargetOf", () => {
    it("answers a hit drawn for a store's object with the store's id and the object's ref", () => {
        expect(pickTargetOf(pick(layer, region), undefined).hit).toEqual({
            responseId: "regions",
            ref: region,
        });
    });

    it("answers a hit outside the scene with no id", () => {
        expect(pickTargetOf(pick(layer), undefined).hit).toEqual({ responseId: null, ref: null });
    });

    it("has no hit over empty ground, or when the nearest thing is an element", () => {
        expect(pickTargetOf(pick(null), undefined).hit).toBeNull();
        expect(
            pickTargetOf(pick(layer, region), Point.create({ position: [0, 0] })).hit,
        ).toBeNull();
    });
});

describe("isStorePick", () => {
    it("is a store pick only when the nearest hit was drawn for a store's object", () => {
        expect(isStorePick(pickTargetOf(pick(layer, region), undefined))).toBe(true);
        expect(isStorePick(pickTargetOf(pick(layer), undefined))).toBe(false);
        expect(isStorePick(pickTargetOf(pick(null), undefined))).toBe(false);
    });
});
