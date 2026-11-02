// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { testLayerAsync } from "@deck.gl/test-utils/vitest";
import type { Layer } from "@deck.gl/core";
import { TextLayer } from "@deck.gl/layers";
import { RecyclingTextLayer as _RecyclingTextLayer } from "./RecyclingTextLayer";
import { FONT_RECYCLE_AT_PX } from "./recycle.util";

interface Datum {
    position: [number, number];
    text: string;
}

const RecyclingTextLayer = _RecyclingTextLayer<Datum>;

// One distinct CJK ideograph per datum, drawn from the CJK Unified Ideographs
// block (U+4E00–U+9FFF). Each distinct character packs its own atlas cell — even
// if headless Chromium lacks the glyph and renders a `.notdef` box, the box has a
// real, non-zero width, so the atlas still grows by one cell per distinct char.
// Unlike the icon spike's SVG data-URIs, SDF glyph rasterisation is synchronous
// (TinySDF on a canvas), so there is no `Image.onload` to hang on.
function makeChar(i: number): string {
    return String.fromCharCode(0x4e00 + (i % 0x5000));
}

function makeData(start: number, count: number): Datum[] {
    return Array.from({ length: count }, (_, k) => ({
        position: [(k % 50) * 0.1, Math.floor(k / 50) * 0.1] as [number, number],
        text: makeChar(start + k),
    }));
}

// SDF + `characterSet: "auto"` + `radius: 4` mirrors the live label config
// (element-layer-renderers.ts). A unique `fontFamily` per test keeps each test's
// atlases in their own `_getKey` namespace, since deck's font-atlas cache is
// module-global and shared across the whole file.
function baseProps(fontFamily: string) {
    return {
        getPosition: (d: Datum) => d.position,
        getText: (d: Datum) => d.text,
        getSize: 20,
        characterSet: "auto" as const,
        fontFamily,
        fontSettings: { sdf: true, radius: 4 },
    };
}

interface AtlasReading {
    height: number;
    packed: number;
}

function readAtlas(layer: {
    state?: {
        fontAtlasManager?: {
            atlas?: { height: number };
            mapping?: Record<string, unknown>;
        };
    };
}): AtlasReading {
    const fam = layer.state?.fontAtlasManager;
    return {
        height: fam?.atlas?.height ?? 0,
        packed: fam?.mapping ? Object.keys(fam.mapping).length : 0,
    };
}

const ROUND = 160;
const ROUNDS = 8;

function rotatingTestCases(
    onAfterUpdate: (r: number, layer: Layer) => void,
    extraProps: Record<string, unknown>,
) {
    return Array.from({ length: ROUNDS }, (_, r) => {
        // Disjoint set per round → cumulative distinct glyph count grows
        // 160, 320, 480 … 1280, far past the 4096px overflow point.
        const data = makeData(r * ROUND, ROUND);
        const after = ({ layer }: { layer: Layer }) => onAfterUpdate(r, layer);
        return r === 0
            ? {
                  title: `round ${r}`,
                  props: { id: "atlas", data, ...extraProps },
                  onAfterUpdate: after,
              }
            : { title: `round ${r}`, updateProps: { data }, onAfterUpdate: after };
    });
}

describe("RecyclingTextLayer", () => {
    it("bounds its never-evicting SDF font atlas by forcing a cache-key recycle", async () => {
        const readings: AtlasReading[] = [];
        await testLayerAsync({
            Layer: RecyclingTextLayer,
            testCases: rotatingTestCases(
                (r, layer) => (readings[r] = readAtlas(layer as never)),
                baseProps("recycle-test-font"),
            ),
        });

        const seen = readings.filter(Boolean);
        expect(seen.length).toBe(ROUNDS);

        // Load-bearing invariant: the atlas height never reaches the recycle
        // threshold once recycling reclaims it. (Without the recycle a later round
        // climbs past 4096 toward Firefox's 8192 white-out — see the control test.)
        for (const reading of seen) {
            expect(reading.height).toBeLessThanOrEqual(FONT_RECYCLE_AT_PX);
        }

        // The recycle actually fires: packed glyphs stay bounded instead of
        // accumulating to ROUND × ROUNDS (the never-evict behaviour). Robust to
        // recycle cadence — unlike a per-round drop, which the in-frame rebuild
        // hides by recycling within the same round.
        const maxPacked = Math.max(...seen.map((r) => r.packed));
        expect(maxPacked).toBeLessThan(ROUND * ROUNDS);
    }, 120000);

    it("control: a plain TextLayer overflows the same corpus past the recycle threshold", async () => {
        // Teeth-check: proves the corpus is large enough to overflow, so the
        // bounded assertion above is meaningful (disabling the recycle fails it).
        const readings: AtlasReading[] = [];
        await testLayerAsync({
            Layer: TextLayer as typeof TextLayer<Datum>,
            testCases: rotatingTestCases(
                (r, layer) => (readings[r] = readAtlas(layer as never)),
                baseProps("control-test-font"),
            ),
        });

        const seen = readings.filter(Boolean);
        const maxHeight = Math.max(...seen.map((r) => r.height));
        // The never-evicting atlas climbs monotonically past the threshold.
        expect(maxHeight).toBeGreaterThan(FONT_RECYCLE_AT_PX);
        // And packs every distinct glyph ever seen (no eviction).
        const maxPacked = Math.max(...seen.map((r) => r.packed));
        expect(maxPacked).toBeGreaterThanOrEqual(ROUND * ROUNDS * 0.9);
    }, 120000);
});
