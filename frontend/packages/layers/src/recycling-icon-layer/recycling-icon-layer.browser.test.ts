// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { testLayerAsync } from "@deck.gl/test-utils/vitest";
import type { Layer } from "@deck.gl/core";
import { Renderer } from "@opencop/symbology";
import { RecyclingIconLayer as _RecyclingIconLayer } from "./RecyclingIconLayer";
import { RECYCLE_AT_PX } from "./recycle.util";

interface Datum {
    position: [number, number];
    sidc: string;
}

const RecyclingIconLayer = _RecyclingIconLayer<Datum>;

// Distinct, sizeable synthetic SIDCs (identity × echelon × entity) — milsymbol
// renders these (loadable data URIs) and they pack large, so the atlas reaches
// the 4096 recycle threshold within a couple of 120-distinct rounds.
function makeSidc(i: number): string {
    const identity = String((i % 6) + 1);
    const echelon = String(11 + (i % 8)).padStart(2, "0");
    const entity = String(i).padStart(6, "0").slice(-6);
    return "10" + "0" + identity + "10" + "0" + "0" + echelon + entity + "0000";
}

function makeData(start: number, count: number): Datum[] {
    return Array.from({ length: count }, (_, k) => ({
        position: [(k % 50) * 0.1, Math.floor(k / 50) * 0.1] as [number, number],
        sidc: makeSidc(start + k),
    }));
}

const baseAccessors = {
    getPosition: (d: Datum) => d.position,
    getIcon: (d: Datum) => {
        const src = Renderer.render({ sidc: d.sidc }).source;
        return { url: src.url, id: src.id ?? d.sidc, width: src.width, height: src.height };
    },
    getSize: 20,
} as const;

interface AtlasReading {
    height: number;
    packed: number;
}

function readAtlas(layer: {
    state?: {
        iconManager?: {
            getTexture?: () => { height: number } | null;
            _mapping?: Record<string, unknown>;
        };
    };
}): AtlasReading {
    const im = layer.state?.iconManager;
    return {
        height: im?.getTexture?.()?.height ?? 0,
        packed: im?._mapping ? Object.keys(im._mapping).length : 0,
    };
}

describe("RecyclingIconLayer", () => {
    it("bounds its never-evicting atlas by rebuilding the IconManager in place", async () => {
        // Rotate disjoint 120-distinct sets. Without recycling the atlas would
        // accumulate every set (120, 240, 360, …) and climb 1024→2048→4096→8192;
        // the in-place rebuild keeps the height sawtoothing below the black-box level.
        const ROUND = 120;
        const ROUNDS = 8;
        const readings: AtlasReading[] = [];

        const testCases = Array.from({ length: ROUNDS }, (_, r) => {
            const data = makeData(r * ROUND, ROUND);
            const onAfterUpdate = ({ layer }: { layer: Layer }) => {
                readings[r] = readAtlas(layer as never);
            };
            return r === 0
                ? {
                      title: `round ${r}`,
                      props: { id: "recycle", data, ...baseAccessors },
                      onAfterUpdate,
                  }
                : { title: `round ${r}`, updateProps: { data }, onAfterUpdate };
        });

        await testLayerAsync({ Layer: RecyclingIconLayer, testCases });

        const seen = readings.filter(Boolean);
        expect(seen.length).toBe(ROUNDS);

        // The load-bearing invariant: the atlas never grows past the recycle
        // threshold to the 8192 level that turns icons black. (Without the
        // in-place rebuild a later round reaches 8192 here.)
        for (const reading of seen) {
            expect(reading.height).toBeLessThanOrEqual(RECYCLE_AT_PX);
        }
        // The recycle fires: packed slots stay bounded instead of accumulating to
        // ROUND × ROUNDS (the never-evict behaviour). Robust to recycle cadence —
        // unlike a per-round drop, which the rebuild can hide by recycling within
        // the same round.
        const maxPacked = Math.max(...seen.map((r) => r.packed));
        expect(maxPacked).toBeLessThan(ROUND * 3);
    }, 60000);
});
