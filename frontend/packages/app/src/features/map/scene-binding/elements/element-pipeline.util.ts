// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { shallow } from "zustand/shallow";
import { TextPrimitive, type Primitive } from "@opencop/scene";
import type { Element } from "@opencop/elements";
import type { RenderState, Selector } from "../RenderSink";

/** Values of a label's second filter channel. */
export const LABEL = {
    /** Hidden by the text settings. */
    hidden: 0,
    shown: 1,
    /** Hovered or selected: shown at every zoom, and despite its source's text setting. */
    pinned: 2,
} as const;

/** State every element's decoration depends on. */
export const settingsSelector: Selector<{
    filters: RenderState["inclusion"]["filters"];
    enabledTypes: RenderState["text"]["enabledTypes"];
    managedSources: RenderState["text"]["managedSources"];
    visibleSources: RenderState["text"]["visibleSources"];
}> = {
    select: (state) => ({
        filters: state.inclusion.filters,
        enabledTypes: state.text.enabledTypes,
        managedSources: state.text.managedSources,
        visibleSources: state.text.visibleSources,
    }),
    equalityFn: shallow,
};

/** Keys of the elements whose decoration differs from the rest. */
export const emphasisSelector: Selector<[selected: string | null, hovered: string | null]> = {
    select: (state) => [state.emphasis.selected, state.emphasis.hovered],
    equalityFn: shallow,
};

/** Shows the text among primitives that aren't elements at every zoom. */
export function pinLabels<P extends Primitive>(primitives: P[]): P[] {
    for (const primitive of primitives)
        if (TextPrimitive.is(primitive)) primitive.filter = [1, LABEL.pinned];
    return primitives;
}

/** What a primitive takes from render state on top of what its mapper produced. */
type Decoration = { filter: number[]; outline: boolean };

/**
 * The decoration of each of an element's primitives. A draft is never filtered
 * out and always shows its label, so what is being drawn stays on the map.
 *
 * `content` marks a type whose text *is* the object rather than a label about
 * it — the content of a box, say. Such a type declares `textIsContent` on its
 * mapper, and it is pinned: drawn at every zoom and whatever the text settings
 * say, because hiding it would hide the element itself.
 */
export function decorator(
    element: Element,
    state: RenderState,
    draft = false,
    content = false,
): (primitive: Primitive) => Decoration {
    const inclusion = draft ? 1 : inclusionValue(element, state);
    const selected = element.key === state.emphasis.selected;
    const label = draft || content ? LABEL.pinned : labelValue(element, state);

    return (primitive) =>
        TextPrimitive.is(primitive)
            ? { filter: [inclusion, label], outline: false }
            : { filter: [inclusion], outline: selected };
}

/**
 * Historical elements carry their own value, matched against the playhead's
 * range; live ones are in or out of the map filters.
 */
function inclusionValue(element: Element, state: RenderState): number {
    if (element.elementClass === "historical") return element.filterValue;
    return state.inclusion.filters.every((filter) => filter.execute({ element })) ? 1 : -1;
}

function labelValue(element: Element, state: RenderState): number {
    const { enabledTypes, managedSources, visibleSources } = state.text;
    const { selected, hovered } = state.emphasis;

    if (enabledTypes[element.type] === false) return LABEL.hidden;
    if (element.key === selected || element.key === hovered) return LABEL.pinned;
    return !managedSources.includes(element.source) || visibleSources.includes(element.source)
        ? LABEL.shown
        : LABEL.hidden;
}
