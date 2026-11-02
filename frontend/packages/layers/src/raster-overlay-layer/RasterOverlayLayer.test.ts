// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { inferWmtsMaxZoom, isKvpWmtsUrl, resolveTileUrl } from "./RasterOverlayLayer";

describe("resolveTileUrl", () => {
    it("replaces {Time} with 'default'", () => {
        expect(resolveTileUrl("https://example.com/{Time}/tile.png", null)).toBe(
            "https://example.com/default/tile.png",
        );
    });

    it("replaces token placeholders when a token is provided", () => {
        expect(resolveTileUrl("https://example.com/tile?key={key}", "abc123")).toBe(
            "https://example.com/tile?key=abc123",
        );
    });

    it("leaves token placeholders when no token is provided", () => {
        expect(resolveTileUrl("https://example.com/tile?key={key}", null)).toBe(
            "https://example.com/tile?key={key}",
        );
    });
});

describe("inferWmtsMaxZoom", () => {
    it("parses the zoom level from a GoogleMapsCompatible_Level tile matrix set", () => {
        expect(inferWmtsMaxZoom("GoogleMapsCompatible_Level6")).toBe(6);
    });

    it("returns undefined when the tile matrix set does not match the pattern", () => {
        expect(inferWmtsMaxZoom("EPSG:3857")).toBeUndefined();
    });
});

describe("isKvpWmtsUrl", () => {
    it("returns true for KVP WMTS URLs", () => {
        expect(isKvpWmtsUrl("https://example.com/wmts?SERVICE=WMTS&REQUEST=GetTile")).toBe(true);
    });

    it("is case-insensitive", () => {
        expect(isKvpWmtsUrl("https://example.com/wmts?service=wmts")).toBe(true);
    });

    it("returns false for WMS URLs", () => {
        expect(isKvpWmtsUrl("https://example.com/wms?SERVICE=WMS")).toBe(false);
    });

    it("returns false for regular tile URLs", () => {
        expect(isKvpWmtsUrl("https://example.com/tiles/{z}/{x}/{y}.png")).toBe(false);
    });
});
