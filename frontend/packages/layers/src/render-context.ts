// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { LayerExtension } from "@deck.gl/core";
import type { Element } from "@opencop/elements";
import type { MeasurementSystem } from "./measurement.props";
import type { AngleOrientation } from "./angle-layer/angle-layer.util";

/**
 * A deck.gl `DataFilterExtension` instance plus the `filterRange` it drives.
 *
 * The two halves sit at different privilege levels on purpose: a renderer sees
 * only the `extension` on {@link RenderContext} (it must hang the extension on
 * the layer it builds so the GPU attribute is allocated), while the **range** —
 * the value that actually filters — is host-only and reaches the layer through
 * {@link HostRenderContext} and the host's wrapper. The live map leaves the whole
 * thing undefined and pays nothing.
 */
export interface DataFilterOption {
    readonly extension: LayerExtension;
    readonly range: [number, number];
}

/**
 * Everything the host gathers on a layer (re)build and pushes into a renderer —
 * the **single declaration** of the render-inputs vocabulary, read identically by
 * native subjects and plugins, which is what makes moving a renderer across the
 * plugin boundary a re-registration rather than a rewrite.
 *
 * {@link HostRenderContext} widens this with the two inputs only the host's own
 * layer wrappers consume. A field's absence here is a **capability boundary, not
 * an omission**: the host owns the selection outline, the playhead filter range,
 * and terrain drape by wrapping the layer a renderer returns, so a renderer that
 * could read those would be reimplementing a wrapper.
 *
 * Render inputs are **pushed** as a plain immutable per-build snapshot, never
 * pulled from a store — so adding a host setting only widens this type when a
 * renderer would actually draw differently because of it.
 *
 * Viewport fields are deliberately absent: icon rendering is viewport-independent,
 * keeping this a per-build snapshot rather than a per-frame one.
 */
export interface RenderContext {
    readonly settings: {
        readonly symbolSize: number;
        readonly textSize: number;
        /** Minimum zoom at which element text (labels/measurements) becomes visible. */
        readonly textZoomThreshold: number;
        /** Per-element-type label visibility (`{ symbol: true, graphic: false, … }`). */
        readonly textEnabled: Record<string, boolean>;
        /**
         * The operator's angle-readout preference. Deliberately the *map setting*
         * union, not the layers' own `AngleUnit` — the setting offers "both", which
         * the angle renderer translates to the layer's `"degrees+mils"`.
         */
        readonly angleUnit?: "degrees" | "mils" | "both";
        readonly angleOrientation?: AngleOrientation;
    };
    /** The currently hovered element per type (`{ symbol: <element> }`), or `{}`. */
    readonly hovered: Record<string, Element>;
    /** The currently selected element, if any. */
    readonly selected?: Element | null;
    readonly measurementSystem: MeasurementSystem;
    readonly terrainEnabled?: boolean;
    readonly drapeEnabled?: boolean;
    readonly depthCompareAlways?: boolean;
    /** Source IDs represented in Filters and therefore subject to per-source text visibility. */
    readonly filterManagedSources: readonly string[];
    /** Filter-managed source IDs whose labels and measurements are opted in. */
    readonly textVisibleSources: readonly string[];
    /** Prefix the host prepends to layer ids to avoid collisions across pipelines. */
    readonly layerIdPrefix?: string;
    /** Historical View's filter extension, applied to the text sub-layer. */
    readonly dataFilter?: { readonly extension: LayerExtension };
}

/**
 * {@link RenderContext} widened with the two inputs only the host's layer
 * wrappers read.
 *
 * Because it *extends* rather than restates, the two declarations cannot drift,
 * and narrowing one to the other is a compiler-checked projection. It declares
 * `settings` explicitly instead of borrowing the host's map-settings store type,
 * so the render contract names exactly what it renders from — which is also what
 * lets this live beside the context it widens rather than in host source.
 */
export interface HostRenderContext extends RenderContext {
    readonly settings: RenderContext["settings"] & { readonly outlineColor: string };
    readonly dataFilter?: DataFilterOption;
    /** Filter function applied to elements. */
    readonly mapFilter?: (element: Element) => boolean;
    /** Whether app is in historical mode. */
    readonly historical?: boolean;
}
