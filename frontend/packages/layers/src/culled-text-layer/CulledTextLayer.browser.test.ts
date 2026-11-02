// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { testLayer } from "@deck.gl/test-utils/vitest";
import { WebMercatorViewport } from "@deck.gl/core";
import { CulledTextLayer as _CulledTextLayer, type CulledTextLayerProps } from "./CulledTextLayer";
import { BinaryMultiIconLayer } from "../binary-text-layer/BinaryMultiIconLayer";
import { MixedBillboardTextLayer } from "../mixed-billboard-text-layer/MixedBillboardTextLayer";

interface TextDatum {
    text: string;
    position: [number, number];
}

const baseAccessors = {
    getText: (d) => d.text,
    getPosition: (d) => d.position,
} as const satisfies Partial<CulledTextLayerProps<TextDatum>>;

const CulledTextLayer = _CulledTextLayer<TextDatum>;

/** Zoom 8 is above the default `zoomThreshold` (5), so the culled glyph layer is visible. */
const VIEWPORT = new WebMercatorViewport({
    width: 400,
    height: 400,
    longitude: 0,
    latitude: 0,
    zoom: 8,
});
const [, , maxLng] = VIEWPORT.getBounds();
const LNG_SPAN = maxLng * 2;

/** Inside the raw viewport bounds. */
const INSIDE = { text: "in", position: [0, 0] } as const satisfies TextDatum;
/** Outside the viewport but inside the default 0.5 padding (bounds grow by half a span per side). */
const PADDED = {
    text: "pad",
    position: [maxLng + 0.3 * LNG_SPAN, 0],
} as const satisfies TextDatum;
/** Beyond the padded bounds. */
const OUTSIDE = {
    text: "out",
    position: [maxLng + 3 * LNG_SPAN, 0],
} as const satisfies TextDatum;
/** Near the far pan target below. */
const FAR = { text: "far", position: [30, 0] } as const satisfies TextDatum;

/** Small pan: stays inside the padded bounds of `VIEWPORT`. */
const NEAR_PAN = new WebMercatorViewport({
    width: 400,
    height: 400,
    longitude: 0.1 * LNG_SPAN,
    latitude: 0,
    zoom: 8,
});

/** Far pan: completely outside the padded bounds, over `FAR`. */
const FAR_PAN = new WebMercatorViewport({
    width: 400,
    height: 400,
    longitude: 30,
    latitude: 0,
    zoom: 8,
});

/** Below the default zoomThreshold of 5. */
const LOW_ZOOM = new WebMercatorViewport({
    width: 400,
    height: 400,
    longitude: 0,
    latitude: 0,
    zoom: 3,
});

/** Repeat `[x, y]` n times as a flat [x, y, 0, ...] array (glyph positions are 3-float). */
function repeatXY(xy: readonly number[], times: number): number[] {
    const out: number[] = [];
    for (let i = 0; i < times; i++) out.push(xy[0], xy[1], 0);
    return out;
}

const findGlyphLayer = (subLayers: { id: string }[]) =>
    subLayers.find((l) => l instanceof BinaryMultiIconLayer);
const findAlwaysVisibleLayer = (subLayers: { id: string }[]) =>
    subLayers.find((l) => l instanceof MixedBillboardTextLayer);

describe("culling on data load", () => {
    it("packs only labels whose anchor falls inside the padded viewport bounds", () => {
        testLayer({
            Layer: CulledTextLayer,
            viewport: VIEWPORT,
            testCases: [
                {
                    title: "mixed in/out data",
                    props: {
                        id: "cull-pack",
                        data: [INSIDE, PADDED, OUTSIDE, FAR],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        const { culled, culledNumInstances, culledStartIndices } = layer.state;
                        const total = INSIDE.text.length + PADDED.text.length;

                        // OUTSIDE and FAR are dropped; INSIDE and PADDED survive.
                        expect(culledNumInstances).toEqual(total);
                        expect(culledStartIndices).toEqual([0, INSIDE.text.length, total]);

                        const expected = repeatXY(INSIDE.position, INSIDE.text.length).concat(
                            repeatXY(PADDED.position, PADDED.text.length),
                        );
                        expect(Array.from(culled.getPosition!.value.slice(0, 3 * total))).toEqual(
                            expected,
                        );
                    },
                },
            ],
        });
    });

    it("feeds the culled subset (not the full data) to the glyph sub-layer", () => {
        testLayer({
            Layer: CulledTextLayer,
            viewport: VIEWPORT,
            testCases: [
                {
                    title: "glyph layer gets culled buffers",
                    props: {
                        id: "cull-sublayer",
                        data: [INSIDE, OUTSIDE],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer, subLayers }) => {
                        const glyphs = findGlyphLayer(subLayers);
                        expect(glyphs).toBeDefined();
                        expect(glyphs!.props.numInstances).toEqual(INSIDE.text.length);
                        expect(glyphs!.props.visible).toBe(true);
                        // Parent still holds every glyph; only the render subset is culled.
                        expect(layer.state.numInstances).toEqual(
                            INSIDE.text.length + OUTSIDE.text.length,
                        );
                    },
                },
            ],
        });
    });
});

describe("viewport-driven refresh", () => {
    it("skips re-culling on small pans and re-culls once the padded bounds are left", () => {
        let culledAfterLoad: unknown;

        testLayer({
            Layer: CulledTextLayer,
            viewport: VIEWPORT,
            testCases: [
                {
                    title: "initial cull",
                    props: {
                        id: "cull-refresh",
                        data: [INSIDE, PADDED, OUTSIDE, FAR],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        culledAfterLoad = layer.state.culled;
                        expect(layer.state.culledNumInstances).toEqual(
                            INSIDE.text.length + PADDED.text.length,
                        );
                    },
                },
                {
                    title: "pan within padded bounds",
                    viewport: NEAR_PAN,
                    onAfterUpdate: ({ layer }) => {
                        // Same object identity — no re-cull happened.
                        expect(layer.state.culled).toBe(culledAfterLoad);
                    },
                },
                {
                    title: "pan far away",
                    viewport: FAR_PAN,
                    onAfterUpdate: ({ layer }) => {
                        const { culled, culledNumInstances, culledStartIndices } = layer.state;
                        expect(culled).not.toBe(culledAfterLoad);

                        // Only FAR is inside the new bounds.
                        expect(culledNumInstances).toEqual(FAR.text.length);
                        expect(culledStartIndices).toEqual([0, FAR.text.length]);
                        expect(
                            Array.from(culled.getPosition!.value.slice(0, 3 * FAR.text.length)),
                        ).toEqual(repeatXY(FAR.position, FAR.text.length));
                    },
                },
            ],
        });
    });
});

describe("zoom threshold visibility", () => {
    it("hides the culled glyph layer below the threshold and shows it above", () => {
        testLayer({
            Layer: CulledTextLayer,
            viewport: LOW_ZOOM,
            testCases: [
                {
                    title: "below threshold",
                    props: {
                        id: "cull-zoom",
                        data: [INSIDE],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const glyphs = findGlyphLayer(subLayers);
                        expect(glyphs).toBeDefined();
                        expect(glyphs!.props.visible).toBe(false);
                    },
                },
                {
                    title: "zoom past threshold",
                    viewport: VIEWPORT,
                    onAfterUpdate: ({ subLayers }) => {
                        const glyphs = findGlyphLayer(subLayers);
                        expect(glyphs).toBeDefined();
                        expect(glyphs!.props.visible).toBe(true);
                    },
                },
            ],
        });
    });
});

describe("culled: false passthrough", () => {
    it("renders the full glyph layer untouched and no always-visible layer", () => {
        testLayer({
            Layer: CulledTextLayer,
            viewport: VIEWPORT,
            testCases: [
                {
                    title: "unculled",
                    props: {
                        id: "cull-off",
                        data: [INSIDE, OUTSIDE],
                        culled: false,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer, subLayers }) => {
                        const glyphs = findGlyphLayer(subLayers);
                        expect(glyphs).toBeDefined();
                        // Full data, including the off-screen label.
                        expect(glyphs!.props.numInstances).toEqual(
                            INSIDE.text.length + OUTSIDE.text.length,
                        );
                        // Culling state never populated.
                        expect(layer.state.culledNumInstances).toEqual(0);
                        expect(findAlwaysVisibleLayer(subLayers)).toBeUndefined();
                    },
                },
            ],
        });
    });
});

describe("always-visible labels", () => {
    it("renders alwaysVisible data in a dedicated visible sub-layer regardless of zoom", () => {
        testLayer({
            Layer: CulledTextLayer,
            viewport: LOW_ZOOM,
            testCases: [
                {
                    title: "hovered label below zoom threshold",
                    props: {
                        id: "cull-always",
                        data: [INSIDE, OUTSIDE],
                        alwaysVisible: [OUTSIDE],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        // Culled glyphs are hidden at this zoom...
                        expect(findGlyphLayer(subLayers)!.props.visible).toBe(false);

                        // ...but the always-visible layer renders its subset anyway.
                        const always = findAlwaysVisibleLayer(subLayers);
                        expect(always).toBeDefined();
                        expect(always!.props.visible).toBe(true);
                        expect(always!.props.data).toEqual([OUTSIDE]);
                    },
                },
            ],
        });
    });
});

describe("text: false", () => {
    it("renders no glyph layer when text is disabled", () => {
        testLayer({
            Layer: CulledTextLayer,
            viewport: VIEWPORT,
            testCases: [
                {
                    title: "text off",
                    props: {
                        id: "cull-text-off",
                        data: [INSIDE],
                        text: false,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        expect(findGlyphLayer(subLayers)).toBeUndefined();
                    },
                },
            ],
        });
    });

    it("still renders alwaysVisible labels when text is disabled", () => {
        testLayer({
            Layer: CulledTextLayer,
            viewport: VIEWPORT,
            testCases: [
                {
                    title: "text off with a selected label",
                    props: {
                        id: "cull-text-off-always",
                        data: [INSIDE, OUTSIDE],
                        text: false,
                        alwaysVisible: [OUTSIDE],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        // Text is off by prop, not by zoom — this viewport is above the threshold.
                        expect(findGlyphLayer(subLayers)).toBeUndefined();

                        // Hover/selection exempts a label from the global text-off switch.
                        const always = findAlwaysVisibleLayer(subLayers);
                        expect(always).toBeDefined();
                        expect(always!.props.visible).toBe(true);
                        expect(always!.props.data).toEqual([OUTSIDE]);
                    },
                },
            ],
        });
    });
});
