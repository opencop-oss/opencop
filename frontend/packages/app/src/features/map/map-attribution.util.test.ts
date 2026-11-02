// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { StyleSpecification } from "maplibre-gl";
import {
    collectMapCredits,
    dedupeAttributions,
    MAX_PARSED_ATTRIBUTION_LENGTH,
    parseAttribution,
    readStyleAttributions,
    type MapCreditSources,
    type ParsedCredit,
} from "./map-attribution.util";

// Shaped like a provider's TileJSON credit: several links, an HTML entity, and link attributes the
// parser drops.
const PROVIDER_CREDIT =
    '<a href="https://tiles.example.org" target="_blank">Example Tiles</a> <a href="https://schema.example.org/" target="_blank">&copy; Example Schema</a> Data from <a href="https://data.example.org/copyright" target="_blank">Example Data</a>';

const rawCredits = (credits: ParsedCredit[]) => credits.map(({ credit }) => credit);

describe("parseAttribution", () => {
    it("keeps a provider's text and http(s) links", () => {
        expect(parseAttribution(PROVIDER_CREDIT)).toEqual([
            { kind: "link", text: "Example Tiles", href: "https://tiles.example.org/" },
            { kind: "text", text: " " },
            { kind: "link", text: "© Example Schema", href: "https://schema.example.org/" },
            { kind: "text", text: " Data from " },
            { kind: "link", text: "Example Data", href: "https://data.example.org/copyright" },
        ]);
    });

    it("reads plain text as one text run", () => {
        expect(parseAttribution("  © Esri,   Maxar \n Earthstar  ")).toEqual([
            { kind: "text", text: "© Esri, Maxar Earthstar" },
        ]);
    });

    it("drops script and style content entirely", () => {
        expect(parseAttribution("<script>alert(1)</script>© Example<style>a{}</style>")).toEqual([
            { kind: "text", text: "© Example" },
        ]);
    });

    it("turns links with unsafe or relative targets into plain text", () => {
        expect(
            parseAttribution(
                '<a href="javascript:alert(1)">One</a> <a href="data:text/html,x">Two</a> <a href="/copyright">Three</a> <a>Four</a>',
            ),
        ).toEqual([{ kind: "text", text: "One Two Three Four" }]);
    });

    it("keeps only the text of other markup, whatever its attributes", () => {
        expect(
            parseAttribution(
                '<details open onload="1" ontoggle="alert(1)"><summary>©</summary> <b onclick="x()">Example</b></details><img src="x" onerror="alert(1)">',
            ),
        ).toEqual([{ kind: "text", text: "© Example" }]);
    });

    it("separates words around block elements but not inline ones", () => {
        expect(
            parseAttribution(
                '<details>© Hostile</details><a href="https://example.com/">Click</a><div>One</div><div>Two</div> <b>Bold</b>text',
            ),
        ).toEqual([
            { kind: "text", text: "© Hostile " },
            { kind: "link", text: "Click", href: "https://example.com/" },
            { kind: "text", text: " One Two Boldtext" },
        ]);
    });

    it("applies the same text rules inside links", () => {
        expect(
            parseAttribution(
                '<a href="https://example.com/"><div>© One</div><div>© Two</div></a>' +
                    '<a href="https://example.org/">© Provider<script>NOT CREDIT</script></a>',
            ),
        ).toEqual([
            { kind: "link", text: "© One © Two", href: "https://example.com/" },
            { kind: "link", text: "© Provider", href: "https://example.org/" },
        ]);
    });

    it.each([
        ["leading whitespace", " javascript:alert(1)"],
        ["mixed case", "JaVaScRiPt:alert(1)"],
        ["an encoded tab inside the scheme", "jav&#x09;ascript:alert(1)"],
        ["an encoded first letter", "&#106;avascript:alert(1)"],
        ["a vbscript scheme", "vbscript:msgbox(1)"],
        ["a protocol-relative target", "//example.com/"],
        ["a user name that looks like the host", "https://www.openstreetmap.org@example.com/"],
        ["credentials", "https://user:secret@example.com/"],
    ])("keeps only the text of a link with %s", (_, href) => {
        expect(parseAttribution(`<a href="${href}">Credit</a>`)).toEqual([
            { kind: "text", text: "Credit" },
        ]);
    });

    it("normalizes an accepted link target", () => {
        expect(parseAttribution('<a href="HTTPS://EXAMPLE.COM/Path">A</a>')).toEqual([
            { kind: "link", text: "A", href: "https://example.com/Path" },
        ]);
    });

    it(`reads at most ${MAX_PARSED_ATTRIBUTION_LENGTH} characters`, () => {
        expect(parseAttribution("x".repeat(MAX_PARSED_ATTRIBUTION_LENGTH + 100))).toEqual([
            { kind: "text", text: "x".repeat(MAX_PARSED_ATTRIBUTION_LENGTH) },
        ]);
        // Deep nesting is what makes a long string slow to parse; the cut keeps what precedes it.
        expect(parseAttribution("© Kept " + "<b>".repeat(MAX_PARSED_ATTRIBUTION_LENGTH))).toEqual([
            { kind: "text", text: "© Kept" },
        ]);
    });

    it("returns nothing for blank or markup-only input", () => {
        expect(parseAttribution("")).toEqual([]);
        expect(parseAttribution('<img src="x"><br>')).toEqual([]);
    });
});

describe("dedupeAttributions", () => {
    it("drops blanks and exact repeats, in first-seen order", () => {
        expect(
            rawCredits(
                dedupeAttributions([
                    PROVIDER_CREDIT,
                    null,
                    "  ",
                    "© Example",
                    PROVIDER_CREDIT,
                    undefined,
                ]),
            ),
        ).toEqual([PROVIDER_CREDIT, "© Example"]);
    });

    it("drops a credit that renders the same text and links as an earlier one", () => {
        expect(
            rawCredits(dedupeAttributions(["<b>© Example</b>", "© Example", "<i>© Example</i>"])),
        ).toEqual(["<b>© Example</b>"]);
    });

    it("keeps a credit whose text appears elsewhere only inside an attribute", () => {
        expect(
            rawCredits(
                dedupeAttributions(["© Required", '<span title="© Required">© Other</span>']),
            ),
        ).toEqual(["© Required", '<span title="© Required">© Other</span>']);
    });

    it("keeps a credit whose text appears elsewhere only inside a dropped element", () => {
        expect(
            rawCredits(dedupeAttributions(["© Required", "<script>© Required</script>© Other"])),
        ).toEqual(["© Required", "<script>© Required</script>© Other"]);
    });

    it("keeps a shorter credit even when a longer one shows the same words", () => {
        expect(rawCredits(dedupeAttributions(["Example Data", PROVIDER_CREDIT]))).toEqual([
            "Example Data",
            PROVIDER_CREDIT,
        ]);
    });

    it("drops a credit that renders to nothing", () => {
        expect(rawCredits(dedupeAttributions(['<img src="x">', "© Example"]))).toEqual([
            "© Example",
        ]);
    });
});

describe("readStyleAttributions", () => {
    function fakeMap(
        style: StyleSpecification,
        attributions: Record<string, string | undefined>,
        zoom = 5,
    ) {
        return {
            getStyle: () => style,
            getZoom: () => zoom,
            getSource: (id: string) =>
                id in attributions ? { attribution: attributions[id] } : undefined,
        };
    }

    it("credits only sources a visible layer or the terrain uses", () => {
        const map = fakeMap(
            {
                version: 8,
                sources: {
                    shown: { type: "vector", url: "https://example.com/shown.json" },
                    hidden: { type: "vector", url: "https://example.com/hidden.json" },
                    unused: { type: "vector", url: "https://example.com/unused.json" },
                    dem: { type: "raster-dem", url: "https://example.com/dem.json" },
                },
                terrain: { source: "dem" },
                layers: [
                    { id: "bg", type: "background" },
                    { id: "a", type: "fill", source: "shown", "source-layer": "x" },
                    {
                        id: "b",
                        type: "fill",
                        source: "hidden",
                        "source-layer": "x",
                        layout: { visibility: "none" },
                    },
                ],
            },
            { shown: "© Shown", hidden: "© Hidden", unused: "© Unused", dem: "© Terrain" },
        );

        expect(readStyleAttributions(map)).toEqual(["© Shown", "© Terrain"]);
    });
});

describe("readStyleAttributions zoom ranges", () => {
    const style: StyleSpecification = {
        version: 8,
        sources: {
            base: { type: "vector", url: "https://example.com/base.json" },
            detail: { type: "vector", url: "https://example.com/detail.json" },
        },
        layers: [
            { id: "base", type: "fill", source: "base", "source-layer": "x" },
            {
                id: "detail",
                type: "fill",
                source: "detail",
                "source-layer": "x",
                minzoom: 10,
                maxzoom: 14,
            },
        ],
    };
    const mapAt = (zoom: number) => ({
        getStyle: () => style,
        getZoom: () => zoom,
        getSource: (id: string) => ({ attribution: `© ${id}` }),
    });

    it("credits a zoom-limited source only inside its range, minzoom inclusive", () => {
        expect(readStyleAttributions(mapAt(9.9))).toEqual(["© base"]);
        expect(readStyleAttributions(mapAt(10))).toEqual(["© base", "© detail"]);
        expect(readStyleAttributions(mapAt(13.9))).toEqual(["© base", "© detail"]);
        expect(readStyleAttributions(mapAt(14))).toEqual(["© base"]);
    });

    it("treats a zero zoom bound as unset, as MapLibre does", () => {
        const map = {
            getStyle: () => ({
                ...style,
                layers: [{ ...style.layers[1], minzoom: 0, maxzoom: 0 }],
            }),
            getZoom: () => 3,
            getSource: (id: string) => ({ attribution: `© ${id}` }),
        };
        expect(readStyleAttributions(map)).toEqual(["© detail"]);
    });
});

describe("collectMapCredits", () => {
    const attributionByTileId = new Map<string, string | null>([
        ["basemap", "© Basemap admin"],
        ["dem", "© Elevation"],
        ["texture", "© Texture imagery"],
        ["overlay", "© Overlay imagery"],
        ["weather", "© Radar"],
        ["no-credit", null],
    ]);
    const base: MapCreditSources = {
        styleAttributions: ["© Style source"],
        attributionByTileId,
        activeBasemapTileId: "basemap",
        terrain: null,
        visibleOverlayIds: [],
        activeWeatherIds: [],
    };

    it("credits the basemap's style sources, then its tile", () => {
        expect(rawCredits(collectMapCredits(base))).toEqual(["© Style source", "© Basemap admin"]);
    });

    it("appends visible overlays and active weather layers in drawing order", () => {
        expect(
            rawCredits(
                collectMapCredits({
                    ...base,
                    visibleOverlayIds: ["overlay", "no-credit"],
                    activeWeatherIds: ["weather"],
                }),
            ),
        ).toEqual(["© Style source", "© Basemap admin", "© Overlay imagery", "© Radar"]);
    });

    it("keeps the basemap's credits and adds the terrain's while terrain is on", () => {
        expect(
            rawCredits(
                collectMapCredits({
                    ...base,
                    terrain: { terrainId: "dem", textureId: "texture" },
                    visibleOverlayIds: ["overlay"],
                }),
            ),
        ).toEqual([
            "© Style source",
            "© Basemap admin",
            "© Elevation",
            "© Texture imagery",
            "© Overlay imagery",
        ]);
    });

    it("credits a shared tile once when the texture is also the basemap", () => {
        expect(
            rawCredits(
                collectMapCredits({
                    ...base,
                    styleAttributions: [],
                    terrain: { terrainId: "dem", textureId: "basemap" },
                }),
            ),
        ).toEqual(["© Basemap admin", "© Elevation"]);
    });
});
