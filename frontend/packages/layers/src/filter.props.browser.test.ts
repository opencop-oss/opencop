// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { testLayer } from "@deck.gl/test-utils/vitest";
import { DataFilterExtension, type DataFilterExtensionProps } from "@deck.gl/extensions";
import type { Layer, LayerExtension } from "@deck.gl/core";
import { PointLayer as _PointLayer, type PointLayerProps } from "./point-layer/PointLayer";
import { CircleLayer as _CircleLayer, type CircleLayerProps } from "./circle-layer/CircleLayer";
import {
    LabeledPathLayer as _LabeledPathLayer,
    type LabeledPathLayerProps,
} from "./labeled-path-layer/LabeledPathLayer";
import {
    LabeledPolygonLayer as _LabeledPolygonLayer,
    type LabeledPolygonLayerProps,
} from "./labeled-polygon-layer/LabeledPolygonLayer";
import {
    CulledTextLayer as _CulledTextLayer,
    type CulledTextLayerProps,
} from "./culled-text-layer/CulledTextLayer";
import { BinaryMultiIconLayer } from "./binary-text-layer/BinaryMultiIconLayer";
import {
    EllipseLayer as _EllipseLayer,
    type EllipseLayerProps,
} from "./ellipse-layer/EllipseLayer";
import { ArrowLayer as _ArrowLayer, type ArrowLayerProps } from "./arrow-layer/ArrowLayer";
import {
    IconElementLayer as _IconElementLayer,
    type IconElementLayerProps,
} from "./icon-element-layer/IconElementLayer";
import type { FilterProps } from "./filter.props";

// A 1×1 transparent PNG — enough for the icon layer to build its sub-layers
// without pulling in a real symbol renderer (the layer is icon-source-agnostic).
const STUB_ICON = {
    url: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==",
    width: 1,
    height: 1,
};

/**
 * Proves the generic `FilterProps` forward (Historical View's GPU playhead
 * filter) reaches the geometry sublayers of the diffable composite layers, for
 * both 1:1 data (PointLayer → ScatterplotLayer) and derived/tessellated data
 * (CircleLayer → stroke PathLayer + fill SolidPolygonLayer), and is inert on the
 * live path (no `DataFilterExtension` → no filter props forwarded).
 */

interface FilterDatum {
    position: [number, number];
    radius: number;
    /** Stand-in for an element's `filterValue` (effective-stale offset). */
    t: number;
}

const DATA: FilterDatum[] = [
    { position: [0, 0], radius: 100, t: 10 },
    { position: [10, 10], radius: 2000, t: 50 },
];

const FILTER_RANGE = [0, 30] as const;

const pointAccessors = {
    getPosition: (d) => d.position,
    getRadius: (d) => d.radius,
    getFilterValue: (d) => d.t,
} as const satisfies Partial<PointLayerProps<FilterDatum> & FilterProps<FilterDatum>>;

const circleAccessors = {
    getPosition: (d) => d.position,
    getRadius: (d) => d.radius,
    getFilterValue: (d) => d.t,
} as const satisfies Partial<CircleLayerProps<FilterDatum> & FilterProps<FilterDatum>>;

const PointLayer = _PointLayer<FilterDatum, FilterProps<FilterDatum>>;
const CircleLayer = _CircleLayer<FilterDatum, FilterProps<FilterDatum>>;

interface PathDatum {
    path: number[];
    t: number;
}
interface PolyDatum {
    polygon: number[];
    t: number;
}

const LINES: PathDatum[] = [
    { path: [0, 0, 1, 1, 2, 0], t: 10 },
    { path: [5, 5, 6, 6], t: 50 },
];
const POLYS: PolyDatum[] = [
    { polygon: [0, 0, 1, 0, 1, 1, 0, 1], t: 10 },
    { polygon: [5, 5, 6, 5, 6, 6, 5, 6], t: 50 },
];

const pathAccessors = {
    getPath: (d) => d.path,
    getFilterValue: (d) => d.t,
} as const satisfies Partial<LabeledPathLayerProps<PathDatum> & FilterProps<PathDatum>>;

const polyAccessors = {
    getPolygon: (d) => d.polygon,
    getFilterValue: (d) => d.t,
} as const satisfies Partial<LabeledPolygonLayerProps<PolyDatum> & FilterProps<PolyDatum>>;

const LabeledPathLayer = _LabeledPathLayer<PathDatum, FilterProps<PathDatum>>;
const LabeledPolygonLayer = _LabeledPolygonLayer<PolyDatum, FilterProps<PolyDatum>>;

const dataFilter = (): LayerExtension[] => [new DataFilterExtension({ filterSize: 1 })];
const hasDataFilter = (exts: readonly LayerExtension[] | undefined): boolean =>
    (exts ?? []).some((e) => e instanceof DataFilterExtension);

const find = (subLayers: Layer[], suffix: string): Layer | undefined =>
    subLayers.find((l) => l.id.endsWith(suffix));

/** Read the DataFilterExtension props off a sub-layer's (generically-typed) props. */
const filterView = (l: Layer): Partial<DataFilterExtensionProps<FilterDatum>> =>
    l.props as Partial<DataFilterExtensionProps<FilterDatum>>;

describe("PointLayer — DataFilterExtension forwarding (1:1 data)", () => {
    it("forwards the extension + getFilterValue + filterRange to the scatter sub-layer", () => {
        testLayer({
            Layer: PointLayer,
            testCases: [
                {
                    title: "filtering active",
                    props: {
                        id: "point-filter",
                        data: DATA,
                        filterRange: FILTER_RANGE,
                        extensions: dataFilter(),
                        ...pointAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const scatter = find(subLayers, "-scatter");
                        expect(scatter).toBeDefined();
                        expect(hasDataFilter(scatter!.props.extensions)).toBe(true);
                        expect(typeof filterView(scatter!).getFilterValue).toBe("function");
                        expect(filterView(scatter!).filterRange).toEqual(FILTER_RANGE);
                    },
                },
            ],
        });
    });

    it("is inert on the live path (no extension → no filter props on the sub-layer)", () => {
        testLayer({
            Layer: PointLayer,
            testCases: [
                {
                    title: "no filtering",
                    props: { id: "point-no-filter", data: DATA, ...pointAccessors },
                    onAfterUpdate: ({ subLayers }) => {
                        const scatter = find(subLayers, "-scatter");
                        expect(scatter).toBeDefined();
                        expect(hasDataFilter(scatter!.props.extensions)).toBe(false);
                        expect(filterView(scatter!).filterRange).toBeUndefined();
                        expect(filterView(scatter!).getFilterValue).toBeUndefined();
                    },
                },
            ],
        });
    });

    it("forwards the filter accessor + range to the text sub-layer (DFE delivered by the factory)", () => {
        testLayer({
            Layer: PointLayer,
            testCases: [
                {
                    title: "filtering active, text on",
                    props: {
                        id: "point-filter-text",
                        data: DATA,
                        text: true,
                        filterRange: FILTER_RANGE,
                        extensions: dataFilter(),
                        ...pointAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const text = find(subLayers, "-text");
                        expect(text).toBeDefined();
                        // The composite forwards getFilterValue/filterRange to text; the DFE
                        // itself is delivered separately via `_subLayerProps.text` (factory).
                        expect(typeof filterView(text!).getFilterValue).toBe("function");
                        expect(filterView(text!).filterRange).toEqual(FILTER_RANGE);
                    },
                },
            ],
        });
    });
});

describe("CircleLayer — DataFilterExtension forwarding (derived sub-layer data)", () => {
    it("forwards filter props to both the stroke and fill sub-layers", () => {
        testLayer({
            Layer: CircleLayer,
            testCases: [
                {
                    title: "filtering active",
                    props: {
                        id: "circle-filter",
                        data: DATA,
                        filterRange: FILTER_RANGE,
                        extensions: dataFilter(),
                        ...circleAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        for (const suffix of ["-stroke", "-fill"]) {
                            const sub = find(subLayers, suffix);
                            expect(sub, suffix).toBeDefined();
                            expect(hasDataFilter(sub!.props.extensions), suffix).toBe(true);
                            expect(typeof filterView(sub!).getFilterValue, suffix).toBe("function");
                            expect(filterView(sub!).filterRange, suffix).toEqual(FILTER_RANGE);
                        }
                    },
                },
            ],
        });
    });

    it("derived sub-layer rows carry __source.object so the wrapped accessor resolves the element", () => {
        testLayer({
            Layer: CircleLayer,
            testCases: [
                {
                    title: "source backref present",
                    props: {
                        id: "circle-filter-source",
                        data: DATA,
                        filterRange: FILTER_RANGE,
                        extensions: dataFilter(),
                        ...circleAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const stroke = find(subLayers, "-stroke");
                        const rows = stroke!.props.data as { __source: { object: FilterDatum } }[];
                        // Each tessellated polygon row points back at its originating
                        // element — the backref `getSubLayerAccessor` unwraps so the
                        // element-level `getFilterValue` works on derived data.
                        expect(rows[0].__source.object).toBe(DATA[0]);
                    },
                },
            ],
        });
    });

    it("leaves the fill sub-layer without extensions on the live path", () => {
        testLayer({
            Layer: CircleLayer,
            testCases: [
                {
                    title: "no filtering",
                    props: {
                        id: "circle-no-filter",
                        data: DATA,
                        getPosition: (d: FilterDatum) => d.position,
                        getRadius: (d: FilterDatum) => d.radius,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const fill = find(subLayers, "-fill");
                        expect(fill).toBeDefined();
                        expect(hasDataFilter(fill!.props.extensions)).toBe(false);
                        expect(filterView(fill!).filterRange).toBeUndefined();
                    },
                },
            ],
        });
    });
});

describe("LabeledPathLayer — DataFilterExtension forwarding (variable line geometry)", () => {
    it("forwards filter props to the stroke (paths) sub-layer, coexisting with the dash extension", () => {
        testLayer({
            Layer: LabeledPathLayer,
            testCases: [
                {
                    title: "filtering active with dash",
                    props: {
                        id: "line-filter",
                        data: LINES,
                        dashed: true,
                        filterRange: FILTER_RANGE,
                        extensions: dataFilter(),
                        ...pathAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const paths = find(subLayers, "-paths");
                        expect(paths).toBeDefined();
                        expect(hasDataFilter(paths!.props.extensions)).toBe(true);
                        // The DataFilterExtension coexists with the dash PathStyleExtension on
                        // the budget-tight PathLayer (≥ 2 extensions present).
                        expect((paths!.props.extensions ?? []).length).toBeGreaterThanOrEqual(2);
                        expect(typeof filterView(paths!).getFilterValue).toBe("function");
                        expect(filterView(paths!).filterRange).toEqual(FILTER_RANGE);
                    },
                },
            ],
        });
    });

    it("is inert on the live path (filter accessor set, but no extension → not forwarded)", () => {
        testLayer({
            Layer: LabeledPathLayer,
            testCases: [
                {
                    title: "no filtering",
                    props: { id: "line-no-filter", data: LINES, ...pathAccessors },
                    onAfterUpdate: ({ subLayers }) => {
                        const paths = find(subLayers, "-paths");
                        expect(paths).toBeDefined();
                        expect(hasDataFilter(paths!.props.extensions)).toBe(false);
                        expect(filterView(paths!).filterRange).toBeUndefined();
                    },
                },
            ],
        });
    });
});

describe("LabeledPolygonLayer — DataFilterExtension forwarding (variable polygon geometry)", () => {
    it("forwards filter props to both the stroke and fill sub-layers", () => {
        testLayer({
            Layer: LabeledPolygonLayer,
            testCases: [
                {
                    title: "filtering active",
                    props: {
                        id: "poly-filter",
                        data: POLYS,
                        filterRange: FILTER_RANGE,
                        extensions: dataFilter(),
                        ...polyAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        for (const suffix of ["-stroke", "-fill"]) {
                            const sub = find(subLayers, suffix);
                            expect(sub, suffix).toBeDefined();
                            expect(hasDataFilter(sub!.props.extensions), suffix).toBe(true);
                            expect(typeof filterView(sub!).getFilterValue, suffix).toBe("function");
                            expect(filterView(sub!).filterRange, suffix).toEqual(FILTER_RANGE);
                        }
                    },
                },
            ],
        });
    });

    it("leaves the fill sub-layer without extensions on the live path", () => {
        testLayer({
            Layer: LabeledPolygonLayer,
            testCases: [
                {
                    title: "no filtering",
                    props: { id: "poly-no-filter", data: POLYS, ...polyAccessors },
                    onAfterUpdate: ({ subLayers }) => {
                        const fill = find(subLayers, "-fill");
                        expect(fill).toBeDefined();
                        expect(hasDataFilter(fill!.props.extensions)).toBe(false);
                        expect(filterView(fill!).filterRange).toBeUndefined();
                    },
                },
            ],
        });
    });
});

interface TextDatum {
    text: string;
    position: [number, number];
    t: number;
}

const TEXTS: TextDatum[] = [
    { text: "AB", position: [0, 0], t: 10 },
    { text: "C", position: [1, 1], t: 50 },
];

const textAccessors = {
    getText: (d) => d.text,
    getPosition: (d) => d.position,
} as const satisfies Partial<CulledTextLayerProps<TextDatum>>;

const CulledTextLayer = _CulledTextLayer<TextDatum>;

/** Read the binary `data.attributes` off the glyph (BinaryMultiIcon) sub-layer. */
const glyphAttributes = (l: Layer): Record<string, { value: Float32Array; size: number }> =>
    (l.props.data as { attributes: Record<string, { value: Float32Array; size: number }> })
        .attributes;

describe("CulledTextLayer — DataFilterExtension forwarding (per-glyph binary text filter)", () => {
    it("packs a per-glyph getFilterValue attribute and attaches the DFE + range to the glyph layer", () => {
        testLayer({
            Layer: CulledTextLayer,
            testCases: [
                {
                    // culled:false renders the full (un-culled) glyph layer, so the assertion
                    // is independent of the test viewport's zoom-vs-threshold.
                    title: "filtering active",
                    props: {
                        id: "text-filter",
                        data: TEXTS,
                        culled: false,
                        filterRange: FILTER_RANGE,
                        extensions: dataFilter(),
                        getFilterValue: (d) => d.t,
                        ...textAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const glyphs = subLayers.find((l) => l instanceof BinaryMultiIconLayer);
                        expect(glyphs).toBeDefined();
                        expect(hasDataFilter(glyphs!.props.extensions)).toBe(true);
                        expect(filterView(glyphs!).filterRange).toEqual(FILTER_RANGE);
                        const filterAttr = glyphAttributes(glyphs!).getFilterValue;
                        expect(filterAttr).toBeDefined();
                        // "AB" (t=10) → 2 glyphs; "C" (t=50) → 1 glyph; value replicated per glyph.
                        expect(Array.from(filterAttr.value.slice(0, 3))).toEqual([10, 10, 50]);
                    },
                },
            ],
        });
    });

    it("is inert on the live path (no DFE, no getFilterValue attribute)", () => {
        testLayer({
            Layer: CulledTextLayer,
            testCases: [
                {
                    title: "no filtering",
                    props: {
                        id: "text-no-filter",
                        data: TEXTS,
                        culled: false,
                        ...textAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const glyphs = subLayers.find((l) => l instanceof BinaryMultiIconLayer);
                        expect(glyphs).toBeDefined();
                        expect(hasDataFilter(glyphs!.props.extensions)).toBe(false);
                        expect(glyphAttributes(glyphs!).getFilterValue).toBeUndefined();
                    },
                },
            ],
        });
    });
});

/** Assert every named sub-layer received the DFE (auto-forwarded) + filter accessor/range. */
const expectFiltered = (subLayers: Layer[], suffixes: string[]): void => {
    for (const suffix of suffixes) {
        const sub = find(subLayers, suffix);
        expect(sub, suffix).toBeDefined();
        expect(hasDataFilter(sub!.props.extensions), suffix).toBe(true);
        expect(typeof filterView(sub!).getFilterValue, suffix).toBe("function");
        expect(filterView(sub!).filterRange, suffix).toEqual(FILTER_RANGE);
    }
};

describe("EllipseLayer — DataFilterExtension forwarding (derived geometry)", () => {
    interface EllipseDatum {
        position: [number, number];
        major: number;
        minor: number;
        t: number;
    }
    const ELLIPSES: EllipseDatum[] = [
        { position: [0, 0], major: 1000, minor: 500, t: 10 },
        { position: [10, 10], major: 2000, minor: 1000, t: 50 },
    ];
    const ellipseAccessors = {
        getPosition: (d) => d.position,
        getMajor: (d) => d.major,
        getMinor: (d) => d.minor,
        getFilterValue: (d) => d.t,
    } as const satisfies Partial<EllipseLayerProps<EllipseDatum> & FilterProps<EllipseDatum>>;
    const EllipseLayer = _EllipseLayer<EllipseDatum, FilterProps<EllipseDatum>>;

    it("forwards filter props to stroke, fill, and text", () => {
        testLayer({
            Layer: EllipseLayer,
            testCases: [
                {
                    title: "filtering active",
                    props: {
                        id: "ellipse-filter",
                        data: ELLIPSES,
                        filterRange: FILTER_RANGE,
                        extensions: dataFilter(),
                        ...ellipseAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) =>
                        expectFiltered(subLayers, ["-stroke", "-fill", "-text"]),
                },
            ],
        });
    });
});

describe("ArrowLayer — DataFilterExtension forwarding (line + icon head + text)", () => {
    interface ArrowDatum {
        start: [number, number];
        end: [number, number];
        t: number;
    }
    const ARROWS: ArrowDatum[] = [
        { start: [0, 0], end: [1, 1], t: 10 },
        { start: [5, 5], end: [6, 6], t: 50 },
    ];
    const arrowAccessors = {
        getStart: (d) => d.start,
        getEnd: (d) => d.end,
        getFilterValue: (d) => d.t,
    } as const satisfies Partial<ArrowLayerProps<ArrowDatum> & FilterProps<ArrowDatum>>;
    const ArrowLayer = _ArrowLayer<ArrowDatum, FilterProps<ArrowDatum>>;

    it("forwards filter props to lines, heads (IconLayer), and text", () => {
        testLayer({
            Layer: ArrowLayer,
            testCases: [
                {
                    title: "filtering active",
                    props: {
                        id: "arrow-filter",
                        data: ARROWS,
                        filterRange: FILTER_RANGE,
                        extensions: dataFilter(),
                        ...arrowAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) =>
                        expectFiltered(subLayers, ["-lines", "-heads", "-text"]),
                },
            ],
        });
    });
});

describe("IconElementLayer — DataFilterExtension forwarding (symbol)", () => {
    interface IconDatum {
        position: [number, number];
        size: number;
        course?: number;
        t: number;
    }
    const ICONS: IconDatum[] = [
        { position: [0, 0], size: 32, course: 0, t: 10 },
        { position: [10, 10], size: 32, course: 90, t: 50 },
    ];
    const iconAccessors = {
        getPosition: (d) => d.position,
        getIcon: () => STUB_ICON,
        getSize: (d) => d.size,
        getCourse: (d) => d.course,
        getFilterValue: (d) => d.t,
    } as const satisfies Partial<IconElementLayerProps<IconDatum> & FilterProps<IconDatum>>;
    const IconElementLayer = _IconElementLayer<IconDatum, FilterProps<IconDatum>>;

    it("forwards filter props to icons, courses, and text", () => {
        testLayer({
            Layer: IconElementLayer,
            testCases: [
                {
                    title: "filtering active",
                    props: {
                        id: "milicon-filter",
                        data: ICONS,
                        filterRange: FILTER_RANGE,
                        extensions: dataFilter(),
                        ...iconAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) =>
                        expectFiltered(subLayers, ["-icons", "-courses", "-text"]),
                },
            ],
        });
    });
});
