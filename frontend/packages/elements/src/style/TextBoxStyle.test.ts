// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { DefaultTextBoxStyle, MAX_TEXT_LENGTH, TextBoxStyle } from "./TextBoxStyle";
import { StrokePattern } from "./StrokeStyle";

describe("TextBoxStyle", () => {
    it("round-trips through the property bag", () => {
        const original = new TextBoxStyle({
            fillColor: "#112233ff",
            strokeColor: "#445566ff",
            strokeWeight: 4,
            strokePattern: StrokePattern.Dashed,
            text: "HOLDING AREA",
            textColor: "#ff0000ff",
            textSize: 120,
            textVisible: false,
        });

        const decoded = TextBoxStyle.fromProperties(original.toProperties());

        expect(decoded.toProperties()).toEqual(original.toProperties());
        expect(decoded.getStrokePattern().name).toBe(StrokePattern.Dashed.name);
    });

    it("defaults every absent field, so older wire data decodes to a blank box", () => {
        const decoded = TextBoxStyle.fromProperties({});

        expect(decoded.getFillColor()).toBe(DefaultTextBoxStyle.getFillColor());
        expect(decoded.getStrokeColor()).toBe(DefaultTextBoxStyle.getStrokeColor());
        expect(decoded.getStrokeWeight()).toBe(DefaultTextBoxStyle.getStrokeWeight());
        expect(decoded.getText()).toBe(DefaultTextBoxStyle.getText());
        expect(decoded.getTextColor()).toBe(DefaultTextBoxStyle.getTextColor());
        expect(decoded.getTextSize()).toBe(DefaultTextBoxStyle.getTextSize());
        expect(decoded.isTextVisible()).toBe(DefaultTextBoxStyle.isTextVisible());
    });

    it("replaces a text size that cannot be drawn", () => {
        // The renderer multiplies by this and the wrap divides by it, and a NaN
        // also spins the size field's resync, which compares with `!==`.
        for (const bad of [Number.NaN, 0, -5, Number.POSITIVE_INFINITY]) {
            const decoded = TextBoxStyle.fromProperties({ textSize: bad });
            expect(decoded.getTextSize()).toBe(DefaultTextBoxStyle.getTextSize());
        }

        expect(TextBoxStyle.fromProperties({ textSize: 42 }).getTextSize()).toBe(42);
    });

    it("holds decoded content to the length bound", () => {
        const decoded = TextBoxStyle.fromProperties({ text: "x".repeat(MAX_TEXT_LENGTH + 500) });
        expect(decoded.getText()).toHaveLength(MAX_TEXT_LENGTH);
    });

    it("clones every field", () => {
        // Every field set away from its default, then compared whole: reading
        // a few by hand passes while `clone()` quietly drops the rest.
        const source = DefaultTextBoxStyle.clone()
            .setText("A")
            .setTextColor("#ff0000ff")
            .setTextSize(7)
            .setTextVisible(false);
        source.setStrokeWeight(5);

        expect(source.clone().toProperties()).toEqual(source.toProperties());
    });

    it("does not carry edits back to the style it was cloned from", () => {
        const clone = DefaultTextBoxStyle.clone().setText("COPY").setTextSize(7);

        expect(clone.getText()).toBe("COPY");
        expect(clone.getTextSize()).toBe(7);
        expect(DefaultTextBoxStyle.getText()).toBe("");
        expect(DefaultTextBoxStyle.getTextSize()).not.toBe(7);
    });
});
