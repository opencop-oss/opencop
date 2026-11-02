// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { buildRasterPreviewUrl } from "./tile-preview-image.util";

// Preview tile: London, zoom 10 — x=511, y=340 (XYZ), y=683 (TMS).
describe("buildRasterPreviewUrl", () => {
    it("substitutes zoom level {z} with 10", () => {
        expect(buildRasterPreviewUrl("https://example.com/{z}/tile.png")).toBe(
            "https://example.com/10/tile.png",
        );
    });

    it("substitutes column {x} with 511", () => {
        expect(buildRasterPreviewUrl("https://example.com/10/{x}/340.png")).toBe(
            "https://example.com/10/511/340.png",
        );
    });

    it("substitutes XYZ row {y} with 340", () => {
        expect(buildRasterPreviewUrl("https://example.com/10/511/{y}.png")).toBe(
            "https://example.com/10/511/340.png",
        );
    });

    it("substitutes TMS row {y-tms} with 683", () => {
        expect(buildRasterPreviewUrl("https://example.com/10/511/{y-tms}.png")).toBe(
            "https://example.com/10/511/683.png",
        );
    });

    it("substitutes inverted TMS row {-y} with 683", () => {
        expect(buildRasterPreviewUrl("https://example.com/10/511/{-y}.png")).toBe(
            "https://example.com/10/511/683.png",
        );
    });

    it("substitutes all placeholders in a typical XYZ tile URL", () => {
        expect(buildRasterPreviewUrl("https://tiles.example.com/{z}/{x}/{y}.png")).toBe(
            "https://tiles.example.com/10/511/340.png",
        );
    });

    it("substitutes all placeholders in a typical TMS tile URL", () => {
        expect(buildRasterPreviewUrl("https://tiles.example.com/{z}/{x}/{y-tms}.png")).toBe(
            "https://tiles.example.com/10/511/683.png",
        );
    });

    it("passes through URLs with no placeholders unchanged", () => {
        expect(buildRasterPreviewUrl("https://tiles.example.com/static.png")).toBe(
            "https://tiles.example.com/static.png",
        );
    });

    it("replaces all occurrences when a placeholder appears more than once", () => {
        expect(buildRasterPreviewUrl("/{z}/{z}/{x}/{y}")).toBe("/10/10/511/340");
    });
});
