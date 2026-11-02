// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Line, type Element } from "@opencop/elements";
import { label, path, point, type Primitive } from "@opencop/scene";
import {
    AllExpression,
    TruthyExpression,
    type CompiledFilter,
} from "~/features/map-filter/compiled-filter.model";
import type { RenderState } from "../RenderSink";
import { LABEL, decorator, pinLabels } from "./element-pipeline.util";

type StatePatch = {
    emphasis?: Partial<RenderState["emphasis"]>;
    filters?: CompiledFilter[];
    text?: Partial<RenderState["text"]>;
};

function renderState({ emphasis, filters = [], text }: StatePatch = {}): RenderState {
    return {
        emphasis: {
            selected: null,
            hovered: null,
            outlineColor: "#ffffffff",
            custodyEdit: false,
            ...emphasis,
        },
        inclusion: { filters, historical: false, playheadRange: null },
        text: {
            enabledTypes: { line: true },
            managedSources: [],
            visibleSources: [],
            size: 14,
            zoomThreshold: 5,
            ...text,
        },
        units: { angle: "both", angleOrientation: "ccw", measurement: "METRIC" },
        icons: { size: 30 },
        terrain: { enabled: false, drape: true, depthCompare: "lessequal" },
        view: { bounds: [-180, -90, 180, 90], latitude: 0, zoom: 10 },
    };
}

const line = (objectId = "a", source = "feed") =>
    Line.create({ positions: [0, 0, 1, 1], meta: { objectId, source } });

const stroke = path({
    id: "path",
    geometry: { positions: [0, 0, 0, 1, 1, 0] },
    style: { strokeColor: [0, 0, 0, 255], strokeWidth: 1 },
});
const dot = point({
    id: "dot",
    geometry: { positions: [0, 0, 0] },
    style: { fillColor: [0, 0, 0, 255], strokeColor: [0, 0, 0, 255], strokeWidth: 1, radius: 2 },
});
const name = label({ id: "label", geometry: { positions: [0, 0, 0] }, style: { text: "a" } });

const rejectAll = new AllExpression("reject", [{ execute: () => false }]);

/** The label channel of an element's text primitive. */
const labelOf = (element: Element, state: RenderState, draft?: boolean) =>
    decorator(element, state, draft)(name).filter[1];

describe("decorator", () => {
    it("gives non-text primitives an inclusion channel and text an inclusion and a label channel", () => {
        const decorate = decorator(line(), renderState());

        expect(decorate(stroke)).toEqual({ filter: [1], outline: false });
        expect(decorate(dot)).toEqual({ filter: [1], outline: false });
        expect(decorate(name)).toEqual({ filter: [1, LABEL.shown], outline: false });
    });

    it("includes a live element only when every map filter accepts it", () => {
        const element = line();

        expect(
            decorator(element, renderState({ filters: [new TruthyExpression("all")] }))(stroke),
        ).toEqual({ filter: [1], outline: false });
        expect(
            decorator(
                element,
                renderState({ filters: [new TruthyExpression("all"), rejectAll] }),
            )(name).filter,
        ).toEqual([-1, LABEL.shown]);
    });

    it("includes a historical element by its own filter value, ignoring the map filters", () => {
        const element = Line.create({
            positions: [0, 0, 1, 1],
            meta: { source: "feed", id: "v1", elementClass: "historical" },
        }).setFilterValue(7);

        const decorate = decorator(element, renderState({ filters: [rejectAll] }));

        expect(decorate(stroke).filter).toEqual([7]);
        expect(decorate(name).filter).toEqual([7, LABEL.shown]);
    });

    it("pins the label of the hovered or the selected element", () => {
        const element = line();

        expect(labelOf(element, renderState({ emphasis: { hovered: element.key } }))).toBe(
            LABEL.pinned,
        );
        expect(labelOf(element, renderState({ emphasis: { selected: element.key } }))).toBe(
            LABEL.pinned,
        );
        expect(labelOf(element, renderState({ emphasis: { selected: line("b").key } }))).toBe(
            LABEL.shown,
        );
    });

    it("hides the label of a type whose text is disabled, even when selected", () => {
        const element = line();
        const text = { enabledTypes: { line: false } };

        expect(labelOf(element, renderState({ text }))).toBe(LABEL.hidden);
        expect(labelOf(element, renderState({ text, emphasis: { selected: element.key } }))).toBe(
            LABEL.hidden,
        );
    });

    it("shows the label of a type missing from the text settings", () => {
        expect(labelOf(line(), renderState({ text: { enabledTypes: {} } }))).toBe(LABEL.shown);
    });

    it("hides a managed source's labels unless the source is opted in", () => {
        const element = line("a", "feed");

        expect(labelOf(element, renderState({ text: { managedSources: ["feed"] } }))).toBe(
            LABEL.hidden,
        );
        expect(
            labelOf(
                element,
                renderState({ text: { managedSources: ["feed"], visibleSources: ["feed"] } }),
            ),
        ).toBe(LABEL.shown);
        expect(labelOf(element, renderState({ text: { managedSources: ["other"] } }))).toBe(
            LABEL.shown,
        );
    });

    it("pins a hidden managed source's label on the selected element", () => {
        const element = line("a", "feed");

        expect(
            labelOf(
                element,
                renderState({
                    text: { managedSources: ["feed"] },
                    emphasis: { selected: element.key },
                }),
            ),
        ).toBe(LABEL.pinned);
    });

    it("always includes a draft and pins its label", () => {
        const element = line("a", "feed");
        const state = renderState({
            filters: [rejectAll],
            text: { enabledTypes: { line: false }, managedSources: ["feed"] },
        });

        const decorate = decorator(element, state, true);

        expect(decorate(stroke).filter).toEqual([1]);
        expect(decorate(name).filter).toEqual([1, LABEL.pinned]);
    });

    it("pins content text, so it survives the zoom threshold and the text settings", () => {
        const element = line("a", "feed");
        const state = renderState({
            text: { enabledTypes: { line: false }, managedSources: ["feed"] },
        });

        // Not content: the type's text is disabled, so its label is hidden and
        // the zoom threshold can filter it out.
        expect(decorator(element, state)(name).filter[1]).toBe(LABEL.hidden);
        // Content: the text *is* the object, so hiding it would hide the element.
        expect(decorator(element, state, false, true)(name).filter[1]).toBe(LABEL.pinned);
    });

    it("outlines only the non-text primitives of the selected element", () => {
        const element = line();
        const selected = decorator(element, renderState({ emphasis: { selected: element.key } }));
        const hovered = decorator(element, renderState({ emphasis: { hovered: element.key } }));

        expect(selected(stroke).outline).toBe(true);
        expect(selected(dot).outline).toBe(true);
        expect(selected(name).outline).toBe(false);
        expect(hovered(stroke).outline).toBe(false);
    });
});

describe("pinLabels", () => {
    it("pins every text primitive in place and leaves the rest alone", () => {
        const primitives: Primitive[] = [{ ...stroke }, { ...name }];

        const pinned = pinLabels(primitives);

        expect(pinned).toBe(primitives);
        expect(pinned[0].filter).toBeUndefined();
        expect(pinned[1].filter).toEqual([1, LABEL.pinned]);
    });
});
