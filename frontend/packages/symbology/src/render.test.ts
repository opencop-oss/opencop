// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { _resetRenderCachesForTests, render, renderPreview } from "./render";
import * as strategy from "./strategy";
import * as milsymbol from "./milsymbol-wrapper";
import * as milstd from "./mil-sym-ts-wrapper";

vi.mock("./strategy", () => ({
    chooseRenderStrategy: vi.fn(),
}));
vi.mock("./milsymbol-wrapper", () => ({
    renderIcon: vi.fn(),
}));
vi.mock("./mil-sym-ts-wrapper", async (importOriginal) => {
    const actual = (await importOriginal()) as typeof milstd;
    return {
        ...actual,
        renderIcon: vi.fn(),
    };
});

const mockStrategy = vi.mocked(strategy.chooseRenderStrategy);
const mockMilsymbol = vi.mocked(milsymbol.renderIcon);
const mockMilstd = vi.mocked(milstd.renderIcon);

const SIDC = "10031000001211000000";
const MS_OUT = { url: "data:image/svg+xml;utf8,<svg></svg>", width: 108, height: 108 };

afterEach(() => {
    vi.resetAllMocks();
    _resetRenderCachesForTests();
});

describe("renderPreview", () => {
    describe("dispatcher routing", () => {
        it("returns milsymbol's URL when the strategy picks milsymbol", () => {
            mockStrategy.mockReturnValue({ library: "milsymbol", mode: "icon" });
            mockMilsymbol.mockReturnValue(MS_OUT);
            expect(renderPreview(SIDC)).toBe(MS_OUT.url);
            expect(mockMilstd).not.toHaveBeenCalled();
        });

        it("returns milstd's URL when the strategy picks milstd and milstd has a preview", () => {
            mockStrategy.mockReturnValue({ library: "milstd", mode: "icon" });
            mockMilstd.mockReturnValue({ url: "data:milstd", width: 100, height: 100 });
            expect(renderPreview(SIDC)).toBe("data:milstd");
        });
    });

    describe("caching — repeat calls hit the preview cache", () => {
        // The preview cache stabilizes the underlying IconSource so repeat
        // renderPreview calls don't re-invoke the wrapper. Consumers see
        // the same URL string back; the cached IconSource is held internally
        // for the on-map path (render()) to share.
        it("returns the same URL for repeat calls with the same SIDC", () => {
            mockStrategy.mockReturnValue({ library: "milsymbol", mode: "icon" });
            mockMilsymbol.mockReturnValue(MS_OUT);
            const a = renderPreview(SIDC);
            const b = renderPreview(SIDC);
            expect(a).toBe(b);
            // Wrapper called once; second call hit the cache.
            expect(mockMilsymbol).toHaveBeenCalledTimes(1);
        });

        it("caches the fallback path so the milsymbol '?' placeholder URL is also stable", () => {
            mockStrategy.mockReturnValue({ library: "none" });
            mockMilsymbol.mockReturnValue(MS_OUT);
            const a = renderPreview(SIDC, { fallback: true });
            const b = renderPreview(SIDC, { fallback: true });
            expect(a).toBe(b);
            expect(mockMilsymbol).toHaveBeenCalledTimes(1);
        });

        it("caches the null result for { fallback: false } so opt-out callers don't reinvoke the dispatcher", () => {
            mockStrategy.mockReturnValue({ library: "none" });
            const a = renderPreview(SIDC, { fallback: false });
            const b = renderPreview(SIDC, { fallback: false });
            expect(a).toBeNull();
            expect(b).toBeNull();
            // Strategy consulted exactly once.
            expect(mockStrategy).toHaveBeenCalledTimes(1);
        });
    });

    describe("permissive fallback to milsymbol — keeps stale SIDCs visible", () => {
        it("falls back to milsymbol's MIL-STD '?' placeholder URL when strategy is none", () => {
            // Pre-wrapper behavior: malformed SIDCs rendered as the milsymbol
            // "?" glyph (sometimes framed by affiliation). Validation still
            // flags these as invalid at input time; this fallback is only the
            // safety net for stale/imported elements already in the data.
            mockStrategy.mockReturnValue({ library: "none" });
            mockMilsymbol.mockReturnValue(MS_OUT);
            expect(renderPreview(SIDC, { fallback: true })).toBe(MS_OUT.url);
            // Preview path carries no size override — milsymbol keeps its 100px default.
            expect(mockMilsymbol).toHaveBeenCalledWith(SIDC, { size: undefined });
        });

        it("falls back to milsymbol when milstd claimed the SIDC but produced no SVG", () => {
            mockStrategy.mockReturnValue({ library: "milstd", mode: "icon" });
            mockMilstd.mockReturnValue(null);
            mockMilsymbol.mockReturnValue(MS_OUT);
            expect(renderPreview(SIDC, { fallback: true })).toBe(MS_OUT.url);
        });

        // (The prior "absolute-last-resort when milsymbol throws" test was
        // removed: the milsymbol wrapper now catches its own throws
        // internally and falls back to a pre-built "?" placeholder, so the
        // path from render.ts that used to land on MISSING_ICON_FALLBACK
        // is unreachable in real usage. The sentinel is still exported and
        // used externally by symbol.ts for malformed style.src data.)
    });

    describe("opt-out: { fallback: false }", () => {
        it("returns null instead of the milsymbol placeholder when strategy is none", () => {
            mockStrategy.mockReturnValue({ library: "none" });
            expect(renderPreview(SIDC, { fallback: false })).toBeNull();
            expect(mockMilsymbol).not.toHaveBeenCalled();
        });

        it("returns null when milstd claims the SIDC but has no preview", () => {
            mockStrategy.mockReturnValue({ library: "milstd", mode: "icon" });
            mockMilstd.mockReturnValue(null);
            expect(renderPreview(SIDC, { fallback: false })).toBeNull();
            expect(mockMilsymbol).not.toHaveBeenCalled();
        });

        it("still returns the URL when a library renders successfully", () => {
            mockStrategy.mockReturnValue({ library: "milsymbol", mode: "icon" });
            mockMilsymbol.mockReturnValue(MS_OUT);
            expect(renderPreview(SIDC, { fallback: false })).toBe(MS_OUT.url);
        });
    });
});

describe("map-atlas icon size (M2)", () => {
    // Map icons render at a reduced octagon size so the on-map GPU atlas stays
    // bounded; previews keep milsymbol's 100px default for UI crispness.
    const ICON_OPTS = { sidc: SIDC } as const;

    it("renders map icons at the 64px atlas size via milsymbol", () => {
        mockStrategy.mockReturnValue({ library: "milsymbol", mode: "icon" });
        mockMilsymbol.mockReturnValue(MS_OUT);

        render(ICON_OPTS);

        expect(mockMilsymbol).toHaveBeenCalledWith(SIDC, { size: 64 });
    });

    it("keeps previews at the milsymbol default (no size override)", () => {
        mockStrategy.mockReturnValue({ library: "milsymbol", mode: "icon" });
        mockMilsymbol.mockReturnValue(MS_OUT);

        renderPreview(SIDC);

        expect(mockMilsymbol).toHaveBeenCalledWith(SIDC, { size: undefined });
    });

    it("does not thread a size override into the milstd path", () => {
        // milstd renders icons at ~50px natively — comparable to
        // the map target — so render() leaves its size untouched.
        mockStrategy.mockReturnValue({ library: "milstd", mode: "icon" });
        mockMilstd.mockReturnValue({ url: "data:milstd", width: 50, height: 34 });

        render(ICON_OPTS);

        expect(mockMilstd).toHaveBeenCalledWith(SIDC);
        expect(mockMilsymbol).not.toHaveBeenCalled();
    });

    it("sizes the milsymbol placeholder to the atlas size on the map path", () => {
        // strategy "none" → permissive milsymbol placeholder; on the map path it
        // must also be packed at the reduced size, not 100px.
        mockStrategy.mockReturnValue({ library: "none" });
        mockMilsymbol.mockReturnValue(MS_OUT);

        const result = render(ICON_OPTS);

        expect(result.kind).toBe("icon");
        expect(mockMilsymbol).toHaveBeenCalledWith(SIDC, { size: 64 });
    });
});
