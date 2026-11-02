// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { DefaultTextBoxStyle } from "@opencop/elements";
import { textBoxStyleSpecs } from "./textbox-style-specs";

/** The spec's `defaultValue` for `label`, whatever input kind it uses. */
const defaultFor = (label: string) =>
    textBoxStyleSpecs.find((spec) => spec.label === label)?.input.defaultValue;

describe("textBoxStyleSpecs", () => {
    it("offers the style bar's four shape controls", () => {
        expect(textBoxStyleSpecs.map((spec) => spec.label)).toEqual([
            "fill color",
            "stroke color",
            "stroke weight",
            "stroke pattern",
        ]);
    });

    it("names every label in this plugin's own namespace", () => {
        // Borrowing the host's `toolbar.*` keys would leave raw keys here if
        // shapes ever renamed them.
        for (const spec of textBoxStyleSpecs) {
            expect(spec.labelKey).toMatch(/^textbox:/);
        }
    });

    it("declares the same defaults the style itself carries", () => {
        // Literals here so the module reads nothing off `@opencop/elements` at
        // load; this is what stops them drifting from the real defaults.
        expect(defaultFor("fill color")).toBe(DefaultTextBoxStyle.getFillColor());
        expect(defaultFor("stroke color")).toBe(DefaultTextBoxStyle.getStrokeColor());
        expect(defaultFor("stroke weight")).toBe(DefaultTextBoxStyle.getStrokeWeight());
        expect(defaultFor("stroke pattern")).toBe(DefaultTextBoxStyle.getStrokePattern().name);
    });
});
