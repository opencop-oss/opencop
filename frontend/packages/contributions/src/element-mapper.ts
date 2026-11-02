// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Element } from "@opencop/elements";
import type { Primitive } from "@opencop/scene";
import type { LabelUnits } from "@opencop/tools";

/**
 * The render state a mapper may depend on: the units labels are written in, the
 * icon size, the label text size, and the settled viewport. Nothing else of the host's state reaches a
 * mapper, whatever its selector's types claim.
 */
export interface MapperState {
    readonly units: LabelUnits;
    readonly icons: { readonly size: number };
    /**
     * Label text settings. `size` is the operator's label size, which the scene
     * applies to *every* text primitive as a global scale — so a mapper drawing
     * text that is content rather than a label has to divide it back out to keep
     * the size it asked for.
     *
     * **This is a workaround, not the shape to build on.** It exists because
     * `sizeScale` is a property of the whole text batch, so the only way out of
     * it is to pre-divide. That makes every content mapper repeat the same
     * correction and silently wrong if the scale's definition ever moves. The
     * cleaner fix is to let a primitive opt out of the scale the way
     * `TextStyle.collides` lets it opt out of collision — at which point
     * this field has no callers and should go.
     */
    readonly text: { readonly size: number };
    readonly view: {
        readonly bounds: readonly [left: number, bottom: number, right: number, top: number];
        readonly latitude: number;
        readonly zoom: number;
    };
}

/** Picks what a mapper depends on out of {@link MapperState}. */
export interface MapperSelector<S> {
    select(state: MapperState): S;
    /** Whether two selections are the same; reference equality when omitted. */
    equalityFn?(next: S, prev: S): boolean;
    /** Milliseconds to wait for the selection to settle before re-mapping. */
    debounce?: number;
}

/**
 * Draws one element type as scene primitives. Every element of the type is
 * mapped when it arrives or changes, and again when what the selector picks
 * changes. One mapper per type: registering one for a type another holds is
 * refused.
 */
export interface ElementMapper<E extends Element = Element, S = undefined> {
    /** The element type drawn. */
    readonly type: string;
    readonly selector?: MapperSelector<S>;
    /** The primitives drawing the element; their ids need only be unique within it. */
    map(element: E, state: S): Primitive[];
    /** The element's thumbnail, or null for none. */
    preview?(element: E): CanvasImageSource | null;
    /**
     * The operator's text toggle for this type, in Map Settings: its label's
     * translation key, and where it is listed (lower first).
     */
    readonly textSetting?: { readonly labelKey: string; readonly order?: number };
    /**
     * Whether this type's text is the object itself rather than a label about
     * it — the content of a box a plugin draws, say.
     *
     * Content is pinned: drawn at every zoom and whatever the text settings say,
     * because hiding it would hide the element. It is also drawn at the size its
     * mapper asked for, so a mapper setting this divides the scene's global
     * label scale ({@link MapperState.text}) back out of its sizes. Defaults to
     * false, so ordinary labels are unaffected.
     */
    readonly textIsContent?: boolean;
}
