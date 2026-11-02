// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { createTestHost, type PluginTestHost } from "@opencop/plugin-sdk/testing";
import { TextBox, type TextBoxElement } from "@opencop/elements";
import type { MapperState } from "@opencop/contributions";
import { ELEMENT_STORE_ID } from "@opencop/tools";
import { TEXT_SIZE, type Primitive, type TextStyle } from "@opencop/scene";
import textBoxPlugin from "./plugin.tsx";

/** Loaded through the test host, which runs the same plugin manager and registries as the app. */

let host: PluginTestHost;

const injectedMappers = () =>
    host.contributions.elementMappers.getEntries().map(({ mapper }) => mapper);
const injectedTools = () => host.contributions.tools.getAll();

/** Load the plugin into a fresh test host, which is uninstalled when the test finishes. */
function load() {
    host = createTestHost({ plugin: textBoxPlugin });
    return host.record;
}

/** The render state the selector reads its zoom and label size from. */
const state = (zoom: number, labelSize = TEXT_SIZE): MapperState => ({
    units: { angle: "degrees", angleOrientation: "cw", measurement: "METRIC" },
    icons: { size: 40 },
    text: { size: labelSize },
    view: { bounds: [-1, -1, 1, 1], latitude: 0, zoom },
});

function box(text: string, overrides: Partial<{ color: string; size: number }> = {}) {
    const element = TextBox.create({
        positions: [0, 0, 0.01, 0, 0.01, 0.005, 0, 0.005],
        meta: { id: "box", objectId: "box-object", displayName: "A box", source: "local" },
    });
    element.getStyle().setText(text);
    if (overrides.color) element.getStyle().setTextColor(overrides.color);
    if (overrides.size) element.getStyle().setTextSize(overrides.size);
    return element;
}

/** The primitives the registered mapper draws one element as. */
function primitivesFor(element: TextBoxElement, zoom = 10, labelSize = TEXT_SIZE): Primitive[] {
    const mapper = injectedMappers().find((entry) => entry.type === "textbox");
    if (!mapper) throw new Error("Expected the plugin to register a textbox element mapper");
    const selected = mapper.selector?.select(state(zoom, labelSize));
    return mapper.map(element, selected);
}

/** The text primitive among them, or undefined when the box draws none. */
function textOf(element: TextBoxElement, zoom = 10, labelSize = TEXT_SIZE): TextStyle | undefined {
    const found = primitivesFor(element, zoom, labelSize).find((p) => p.style.type === "text");
    return found?.style as TextStyle | undefined;
}

describe("textbox plugin", () => {
    it("loads and reports active", () => {
        const record = load();
        expect(record.status).toBe("active");
        expect(host.problems).toEqual([]);
    });

    it("registers one tool producing textboxes into the element store", () => {
        load();
        expect(injectedTools()).toHaveLength(1);
        const [tool] = injectedTools();
        expect(tool.produces).toEqual({ store: ELEMENT_STORE_ID, type: "textbox" });
        expect(tool.draw?.grammar).toBeTypeOf("function");
        expect(tool.editor).toBeDefined();
    });

    it("registers an element mapper for the textbox type", () => {
        load();
        expect(injectedMappers().map((entry) => entry.type)).toEqual(["textbox"]);
    });

    it("draws the box as an outline, a fill and its content", () => {
        load();
        const kinds = primitivesFor(box("HOLDING AREA", { size: 22 })).map((p) => p.style.type);
        expect(kinds).toEqual(expect.arrayContaining(["path", "polygon", "text"]));
    });

    it("closes the outline, so the box has four sides and not three", () => {
        load();
        const outline = primitivesFor(box("HOLDING AREA", { size: 22 })).find(
            (p) => p.style.type === "path",
        );
        const positions = (outline?.geometry as { positions: number[] }).positions;

        // Five points for four corners: the last repeats the first.
        expect(positions).toHaveLength(5 * 3);
        expect(positions.slice(0, 3)).toEqual(positions.slice(-3));
    });

    it("draws the box's own content and colour", () => {
        load();
        const style = textOf(box("HOLDING AREA", { color: "#ff0000ff", size: 22 }));
        expect(style?.text).toBe("HOLDING AREA");
        expect(style?.color).toEqual([255, 0, 0, 255]);
    });

    it("has no text setting, so the map's per-type label toggles cannot reach it", () => {
        load();
        const [mapper] = injectedMappers();
        expect(mapper.textSetting).toBeUndefined();
        // Paired: with no toggle, the only thing keeping the content on screen
        // below the label zoom threshold is the pipeline pinning it.
        expect(mapper.textIsContent).toBe(true);
    });

    it("wraps content too wide for its box at the size the operator picked", () => {
        load();
        const long = box("ALPHA BRAVO CHARLIE DELTA ECHO FOXTROT GOLF HOTEL", { size: 200 });
        const style = textOf(long);

        expect(style?.text).toContain("\n");
        expect(style?.text.replace(/\n/g, " ")).toBe(long.getStyle().getText());
    });

    it("lays the content flat, running along the box's first edge", () => {
        load();
        // `box()` lays its first edge due east, which is no rotation at all.
        const style = textOf(box("HOLDING AREA", { size: 22 }));
        expect(style?.billboard).toBe(false);
        expect(style?.angle).toBeCloseTo(0, 0);
    });

    it("turns the content with a box drawn on its side", () => {
        load();
        const turned = TextBox.create({
            // First edge runs north instead of east.
            positions: [0, 0, 0, 0.01, -0.005, 0.01, -0.005, 0],
            meta: { id: "t", objectId: "t", displayName: "t", source: "local" },
        });
        turned.getStyle().setText("HOLDING AREA");
        turned.getStyle().setTextSize(22);
        expect(textOf(turned)?.angle).toBeCloseTo(90, 0);
    });

    it("scales the content with the map, so it holds its size relative to the box", () => {
        load();
        const element = box("HOLDING AREA", { size: 100 });
        const near = textOf(element, 10)?.size ?? 0;
        const far = textOf(element, 9)?.size ?? 0;

        expect(near).toBeGreaterThan(0);
        // One zoom level out halves the pixels a metre covers.
        expect(near / far).toBeCloseTo(2, 6);
    });

    it("opts the content out of collision, so a label under the box cannot hide it", () => {
        load();
        expect(textOf(box("HOLDING AREA", { size: 22 }))?.collides).toBe(false);
    });

    it("holds its size against the operator's label-size setting", () => {
        load();
        const element = box("HOLDING AREA", { size: 100 });
        // The scene scales every text primitive by `labelSize / TEXT_SIZE`, so a
        // mapper that did not divide it back out would draw the content at twice
        // the size the box was broken to.
        const atDefault = textOf(element, 10, TEXT_SIZE)?.size ?? 0;
        const atDouble = textOf(element, 10, TEXT_SIZE * 2)?.size ?? 0;

        expect(atDefault).toBeGreaterThan(0);
        expect(atDouble).toBeCloseTo(atDefault / 2, 10);
    });

    it("renders no content for a box whose text is hidden", () => {
        load();
        const hidden = box("HIDE ME");
        hidden.getStyle().setTextVisible(false);
        expect(textOf(hidden)).toBeUndefined();
    });

    it("unregisters everything on unload", () => {
        load();
        host.uninstall();
        expect(injectedTools()).toHaveLength(0);
        expect(injectedMappers()).toHaveLength(0);
    });
});
