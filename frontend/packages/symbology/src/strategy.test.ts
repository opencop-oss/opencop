// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, beforeEach } from "vitest";
import {
    chooseRenderStrategy,
    chooseEditor,
    getMultipointSymbolSets,
    _resetRenderStrategyCacheForTests,
} from "./strategy";
import { _resetIconCacheForTests as _resetMilsymbolIconCacheForTests } from "./milsymbol-wrapper";
import { _resetIconCacheForTests as _resetMilstdIconCacheForTests } from "./mil-sym-ts-wrapper";

import { ACTION_POINT, FRIEND_INFANTRY, GARBAGE, LIGHT_LINE } from "./test-fixtures";

describe("chooseRenderStrategy", () => {
    beforeEach(() => {
        _resetRenderStrategyCacheForTests();
        _resetMilsymbolIconCacheForTests();
        _resetMilstdIconCacheForTests();
    });

    describe("intent=icon", () => {
        it("prefers milsymbol for single-point unit SIDCs both libraries can render", () => {
            // FRIEND_INFANTRY is a single-point unit renderable by both;
            // milsymbol-first preserves visual continuity for unit symbols.
            expect(chooseRenderStrategy(FRIEND_INFANTRY, "icon")).toEqual({
                library: "milsymbol",
                mode: "icon",
            });
        });

        it("prefers milstd for multipoint tactical graphics (icon previews)", () => {
            // milsymbol's canRender returns true for some 2525D graphic codes
            // but its rendered icon does not match the on-map multipoint shape.
            // Routing graphics through milstd keeps preview and map in sync.
            expect(chooseRenderStrategy(LIGHT_LINE, "icon")).toEqual({
                library: "milstd",
                mode: "icon",
            });
        });

        it("prefers milstd for single-point tactical graphics (Action Point case)", () => {
            // Action Point is SS25 (Control Measure) but single-point. The
            // earlier point-count heuristic (isMultiPoint) misclassified it
            // as a unit symbol; isTacticalGraphic correctly routes it to
            // milstd-first so the icon matches the on-map render.
            expect(chooseRenderStrategy(ACTION_POINT, "icon")).toEqual({
                library: "milstd",
                mode: "icon",
            });
        });

        it("returns library=none for unrenderable input", () => {
            expect(chooseRenderStrategy(GARBAGE, "icon")).toEqual({ library: "none" });
        });
    });

    it("caches the result per (intent, sidc) key", () => {
        const a = chooseRenderStrategy(FRIEND_INFANTRY, "icon");
        const b = chooseRenderStrategy(FRIEND_INFANTRY, "icon");
        expect(b).toBe(a); // same object reference proves cache hit
    });
});

describe("chooseEditor", () => {
    beforeEach(() => {
        _resetRenderStrategyCacheForTests();
    });

    it("returns graphic-editor for SS25 multipoint graphics", () => {
        expect(chooseEditor(LIGHT_LINE)).toBe("graphic-editor");
    });

    it("returns graphic-editor for SS25 single-point graphics (Action Point)", () => {
        // Single-point control measures belong in the graphic editor
        // regardless of point count.
        expect(chooseEditor(ACTION_POINT)).toBe("graphic-editor");
    });

    it("returns symbol-editor for unit symbols", () => {
        expect(chooseEditor(FRIEND_INFANTRY)).toBe("symbol-editor");
    });

    it("returns symbol-editor for malformed/unknown SIDCs", () => {
        // isTacticalGraphic returns false for inputs it can't classify;
        // renderability is checked separately by validateSidc.
        expect(chooseEditor(GARBAGE)).toBe("symbol-editor");
    });
});

describe("getMultipointSymbolSets", () => {
    beforeEach(() => {
        _resetRenderStrategyCacheForTests();
    });

    it("includes SS25 (Control Measures)", () => {
        // SS25 is the canonical tactical-graphic symbol set; the catalog
        // partition is broken if this is ever absent.
        expect(getMultipointSymbolSets().has("25")).toBe(true);
    });

    it("excludes unit-symbol sets like SS10 (Land Units)", () => {
        // SS10 unit symbols belong in the Symbol Editor, not the Graphic
        // Editor — even though some land-unit SIDCs are renderable as icons
        // by both libraries.
        expect(getMultipointSymbolSets().has("10")).toBe(false);
    });

    it("returns the same Set instance on repeated calls (memoized)", () => {
        const a = getMultipointSymbolSets();
        const b = getMultipointSymbolSets();
        expect(b).toBe(a);
    });
});
