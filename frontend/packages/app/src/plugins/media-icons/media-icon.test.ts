// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { resolveMediaIcon, UNRESOLVED_MEDIA_ICON } from "./media-icon";

describe("an href with nothing to fetch", () => {
    it.each([
        ["a relative path that matched no imported image", "icons/missing.png"],
        ["a bare Core path without the opencop:url: prefix", "/v1/layers/abc/media/def"],
        ["a symbol code", "10031000001211000000"],
        ["an empty href", ""],
    ])("resolves %s to the broken-image placeholder", (_, href) => {
        expect(resolveMediaIcon(href)).toBe(UNRESOLVED_MEDIA_ICON);
    });

    it("draws the placeholder from an inline image, so it never makes a request", () => {
        expect(UNRESOLVED_MEDIA_ICON.url.startsWith("data:image/svg+xml")).toBe(true);
    });
});

describe("resolveMediaIcon", () => {
    // VITE_OPENCOP_CORE_API_URL = "http://localhost/api" in vitest.config.ts
    const API_BASE = "http://localhost/api";

    it("parses path and dimensions from fragment", () => {
        const src = resolveMediaIcon("opencop:url:/v1/layers/abc/media/def#32x48");
        expect(src).toEqual({
            url: `${API_BASE}/v1/layers/abc/media/def`,
            width: 32,
            height: 48,
            id: "opencop:url:/v1/layers/abc/media/def#32x48",
        });
    });

    it("normalizes uppercase prefixes, path segments, and X separators", () => {
        const symbol = "OPENCOP:URL:/V1/LAYERS/ABC/MEDIA/DEF#32X48";
        const src = resolveMediaIcon(symbol);
        expect(src).toEqual({
            url: `${API_BASE}/v1/layers/abc/media/def`,
            width: 32,
            height: 48,
            id: symbol.toLowerCase(),
        });
    });

    it("defaults to 64x64 when no fragment", () => {
        const src = resolveMediaIcon("opencop:url:/v1/layers/abc/media/def");
        expect(src).toEqual({
            url: `${API_BASE}/v1/layers/abc/media/def`,
            width: 64,
            height: 64,
            id: "opencop:url:/v1/layers/abc/media/def",
        });
    });

    it("preserves an external icon URL byte-for-byte", () => {
        const symbol = "https://CDN.example.com/Icons/PushPin.PNG?Theme=Dark#anchor";
        const src = resolveMediaIcon(symbol);
        expect(src).toEqual({
            url: symbol,
            width: 64,
            height: 64,
            id: symbol,
        });
    });

    it("preserves an inline data: icon URI byte-for-byte", () => {
        const symbol = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB";
        const src = resolveMediaIcon(symbol);
        expect(src).toEqual({
            url: symbol,
            width: 64,
            height: 64,
            id: symbol,
        });
    });

    describe("external scheme upgrade", () => {
        afterEach(() => vi.unstubAllGlobals());

        it("upgrades http to https when the page is served over https, to avoid mixed-content blocking", () => {
            vi.stubGlobal("location", { ...location, protocol: "https:" });
            const symbol = "http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png";
            const src = resolveMediaIcon(symbol);
            expect(src).toEqual({
                url: "https://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png",
                width: 64,
                height: 64,
                id: symbol, // identity preserved, un-upgraded
            });
        });

        it("leaves the href alone when the page itself is served over http", () => {
            vi.stubGlobal("location", { ...location, protocol: "http:" });
            const symbol = "http://cdn.example.com/http-only-host.png";
            expect(resolveMediaIcon(symbol).url).toBe(symbol);
        });

        it("upgrades uppercase HTTP URLs case-insensitively", () => {
            vi.stubGlobal("location", { ...location, protocol: "https:" });
            expect(resolveMediaIcon("HTTP://cdn.example.com/Upper.png").url).toBe(
                "https://cdn.example.com/Upper.png",
            );
        });
    });

    it("memoizes external URLs without conflating case-sensitive paths", () => {
        const upper = resolveMediaIcon("https://example.com/Icons/Pin.png");
        const upperAgain = resolveMediaIcon("https://example.com/Icons/Pin.png");
        const lower = resolveMediaIcon("https://example.com/icons/pin.png");
        expect(upperAgain).toBe(upper);
        expect(lower).not.toBe(upper);
    });

    it("defaults to 64x64 when fragment is malformed", () => {
        const src = resolveMediaIcon("opencop:url:/v1/layers/abc/media/def#garbage");
        if (typeof src === "string") return expect.fail("Expected a non-string IconSource");
        expect(src.width).toBe(64);
        expect(src.height).toBe(64);
    });

    it("defaults to 64x64 when one dimension is invalid", () => {
        const src = resolveMediaIcon("opencop:url:/v1/layers/abc/media/def#32xabc");
        if (typeof src === "string") return expect.fail("Expected a non-string IconSource");
        expect(src.width).toBe(64);
        expect(src.height).toBe(64);
    });

    it("defaults to 64x64 when the fragment shape is incomplete", () => {
        const src = resolveMediaIcon("opencop:url:/v1/layers/abc/media/def#32x");
        if (typeof src === "string") return expect.fail("Expected a non-string IconSource");
        expect(src.width).toBe(64);
        expect(src.height).toBe(64);
    });

    it("defaults to 64x64 when the fragment has extra numeric parts", () => {
        const src = resolveMediaIcon("opencop:url:/v1/layers/abc/media/def#32x48x64");
        if (typeof src === "string") return expect.fail("Expected a non-string IconSource");
        expect(src.width).toBe(64);
        expect(src.height).toBe(64);
    });

    it("defaults to 64x64 when the fragment is non-numeric text", () => {
        const src = resolveMediaIcon("opencop:url:/v1/layers/abc/media/def#FOOxBAR");
        if (typeof src === "string") return expect.fail("Expected a non-string IconSource");
        expect(src.width).toBe(64);
        expect(src.height).toBe(64);
    });

    it("defaults to 64x64 when the fragment contains negative dimensions", () => {
        const src = resolveMediaIcon("opencop:url:/v1/layers/abc/media/def#-1x-2");
        if (typeof src === "string") return expect.fail("Expected a non-string IconSource");
        expect(src.width).toBe(64);
        expect(src.height).toBe(64);
    });

    it("uses the normalized symbol string as id", () => {
        const symbol = "opencop:url:/v1/layers/abc/media/def#32x32";
        const src = resolveMediaIcon(symbol);
        if (typeof src === "string") return expect.fail("Expected a non-string IconSource");
        expect(src.id).toBe(symbol.toLowerCase());
    });

    it("returns the same reference for repeat lookups", () => {
        const a = resolveMediaIcon("opencop:url:/v1/layers/x/media/y#16x16");
        const b = resolveMediaIcon("opencop:url:/v1/layers/x/media/y#16x16");
        expect(a).toBe(b);
    });

    it("returns the same reference for differently cased lookups", () => {
        const a = resolveMediaIcon("OPENCOP:URL:/V1/LAYERS/X/MEDIA/Y#16X16");
        const b = resolveMediaIcon("opencop:url:/v1/layers/x/media/y#16x16");
        expect(a).toBe(b);
        expect(a.id).toBe("opencop:url:/v1/layers/x/media/y#16x16");
    });
});
