// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { uniq, uniqBy } from "lodash";
import type { LayerSpecification, StyleSpecification } from "maplibre-gl";

/** One run of a parsed credit: plain text, or a link whose target is an absolute http(s) URL. */
export type AttributionPart =
    | { kind: "text"; text: string }
    | { kind: "link"; text: string; href: string };

/** Elements whose content is never credit text, so it is dropped rather than flattened. */
const DROPPED_ELEMENTS = new Set([
    "SCRIPT",
    "STYLE",
    "TEMPLATE",
    "NOSCRIPT",
    "IFRAME",
    "OBJECT",
    "EMBED",
    "SVG",
    "MATH",
]);

/** Phrasing elements that flow inside a line; any other element breaks words around itself. */
const INLINE_ELEMENTS = new Set([
    "A",
    "ABBR",
    "B",
    "BDI",
    "BDO",
    "CITE",
    "CODE",
    "DATA",
    "DFN",
    "EM",
    "I",
    "KBD",
    "MARK",
    "Q",
    "S",
    "SAMP",
    "SMALL",
    "SPAN",
    "STRONG",
    "SUB",
    "SUP",
    "TIME",
    "U",
    "VAR",
]);

/**
 * The most of an attribution string that is parsed. Browsers take time that grows faster than the
 * input to parse deeply nested markup, and a provider's credit has no other bound, so one style or
 * TileJSON could otherwise stall the map. Real credits run to a few hundred characters, and an
 * administrator's is limited to the API's `MAX_TILE_ATTRIBUTION_LENGTH`.
 */
export const MAX_PARSED_ATTRIBUTION_LENGTH = 4096;

/** A credit as the map shows it: its raw string and the text and links read from it. */
export interface ParsedCredit {
    credit: string;
    parts: AttributionPart[];
}

/**
 * Reduces an attribution string to text and http(s) links.
 *
 * Attribution comes from tile providers (TileJSON, style JSON) and from administrators, so it is
 * untrusted HTML. Its first `MAX_PARSED_ATTRIBUTION_LENGTH` characters are parsed into a
 * `DOMParser` document, whose scripts never run, and only text and absolute http(s) link targets
 * are read back out. Render them as text and links, never as HTML, so nothing from the string
 * reaches the page as markup. This keeps the credit off MapLibre's `AttributionControl` and its
 * `DOM.sanitize` sink (GHSA-jrc7-96c5-q579).
 */
export function parseAttribution(html: string): AttributionPart[] {
    // A cut inside markup is harmless: the parser closes whatever the cut leaves open.
    const doc = new DOMParser().parseFromString(
        html.slice(0, MAX_PARSED_ATTRIBUTION_LENGTH),
        "text/html",
    );
    const parts: AttributionPart[] = [];
    collectParts(doc.body, parts);
    return mergeText(parts);
}

function collectParts(node: Node, parts: AttributionPart[]): void {
    for (const child of node.childNodes) {
        if (child.nodeType === Node.TEXT_NODE) {
            parts.push({ kind: "text", text: child.textContent ?? "" });
            continue;
        }
        if (!(child instanceof Element) || DROPPED_ELEMENTS.has(child.tagName.toUpperCase())) {
            continue;
        }
        if (child.tagName.toUpperCase() === "BR") {
            parts.push({ kind: "text", text: " " });
            continue;
        }
        if (child.tagName.toUpperCase() === "A") {
            // A link's text follows the same rules as any other text: dropped elements stay
            // dropped and block elements still separate words.
            const inner: AttributionPart[] = [];
            collectParts(child, inner);
            const text = collapseWhitespace(inner.map((part) => part.text).join(""));
            const href = safeHref(child.getAttribute("href"));
            if (text) parts.push(href ? { kind: "link", text, href } : { kind: "text", text });
            continue;
        }
        // A block element such as <div> or <details> separates words even with no whitespace
        // around it in the source; the text merge collapses the extra spaces.
        const block = !INLINE_ELEMENTS.has(child.tagName.toUpperCase());
        if (block) parts.push({ kind: "text", text: " " });
        collectParts(child, parts);
        if (block) parts.push({ kind: "text", text: " " });
    }
}

/**
 * An absolute http(s) URL, normalized; anything else (relative, javascript:, data:) is dropped, as
 * is a URL with credentials, whose user name can pass for the host
 * (`https://www.openstreetmap.org@example.com/`).
 */
function safeHref(raw: string | null): string | null {
    if (!raw) return null;
    try {
        const url = new URL(raw.trim());
        if (url.protocol !== "https:" && url.protocol !== "http:") return null;
        return url.username || url.password ? null : url.href;
    } catch {
        return null;
    }
}

function collapseWhitespace(text: string): string {
    return text.replace(/\s+/g, " ").trim();
}

/** Joins adjacent text runs, collapses whitespace, and trims the credit's two ends. */
function mergeText(parts: AttributionPart[]): AttributionPart[] {
    const merged: AttributionPart[] = [];
    for (const part of parts) {
        const last = merged.at(-1);
        if (part.kind === "text" && last?.kind === "text") {
            last.text += part.text;
        } else {
            merged.push({ ...part });
        }
    }
    for (const part of merged) {
        if (part.kind === "text") part.text = part.text.replace(/\s+/g, " ");
    }
    const first = merged[0];
    if (first?.kind === "text") first.text = first.text.trimStart();
    const last = merged.at(-1);
    if (last?.kind === "text") last.text = last.text.trimEnd();
    return merged.filter((part) => part.text !== "");
}

/**
 * Drops blank credits, exact repeats, and credits that render to nothing or to the same text and
 * links as an earlier one, keeping first-seen order. Comparison is on what is shown, not on the raw
 * HTML: a raw substring can sit inside an attribute the renderer drops, so containment would lose
 * a credit that is shown nowhere else. Each kept credit comes back with its parts, so nothing needs
 * to parse it again.
 */
export function dedupeAttributions(
    credits: readonly (string | null | undefined)[],
): ParsedCredit[] {
    const rendered = uniq(credits.map((c) => c?.trim() ?? "").filter((c) => c !== ""))
        .map((credit) => ({ credit, parts: parseAttribution(credit) }))
        .filter(({ parts }) => parts.length > 0);
    return uniqBy(rendered, ({ parts }) => JSON.stringify(parts));
}

/** Everything that decides which credits the map shows right now. */
export interface MapCreditSources {
    /** Attribution carried by the active MapLibre style's used sources. */
    styleAttributions: readonly string[];
    /** Administrator-entered attribution by tile id, from the tile list. */
    attributionByTileId: ReadonlyMap<string, string | null>;
    activeBasemapTileId: string | null;
    /** The tiles the 3D terrain is drawn from, or null while terrain is off. */
    terrain: { terrainId?: string; textureId?: string } | null;
    visibleOverlayIds: readonly string[];
    activeWeatherIds: readonly string[];
}

/**
 * The deduplicated credits for what the map is drawing, in drawing order: the active style's
 * sources and the basemap tile, then the 3D terrain and its texture while terrain is on, then
 * visible imagery overlays, then active weather layers. The basemap keeps its credits under
 * terrain: `MapManager.setBasemapVisibility` only makes its canvas transparent, so MapLibre still
 * fetches and draws its tiles, and MapLibre's own AttributionControl would still credit them.
 */
export function collectMapCredits(sources: MapCreditSources): ParsedCredit[] {
    const tile = (id: string | null | undefined) =>
        id ? sources.attributionByTileId.get(id) : null;
    const terrain = sources.terrain
        ? [tile(sources.terrain.terrainId), tile(sources.terrain.textureId)]
        : [];
    return dedupeAttributions([
        ...sources.styleAttributions,
        tile(sources.activeBasemapTileId),
        ...terrain,
        ...sources.visibleOverlayIds.map(tile),
        ...sources.activeWeatherIds.map(tile),
    ]);
}

/** The map members `readStyleAttributions` reads; a MapLibre `Map` satisfies it structurally. */
export interface StyleAttributionMap {
    getStyle(): StyleSpecification | undefined;
    getZoom(): number;
    getSource(id: string): { attribution?: string } | undefined;
}

/**
 * The attribution of every style source that a layer shown at the current zoom, or the terrain,
 * draws from. Sources no shown layer uses are left out, as MapLibre's own control does, so a style
 * that ships unused or zoom-limited sources does not credit them where they are not drawn.
 *
 * Reads `getStyle()` rather than the live layer API: `getLayer` and `getLayersOrder` need a loaded
 * style, and `StyleLayer.isHidden` has no documented contract, so `isShown` below copies its rule
 * and is re-checked on MapLibre upgrades.
 */
export function readStyleAttributions(map: StyleAttributionMap): string[] {
    const style = map.getStyle();
    if (!style) return [];
    const zoom = map.getZoom();
    const usedSourceIds = new Set<string>();
    for (const layer of style.layers) {
        if ("source" in layer && typeof layer.source === "string" && isShown(layer, zoom)) {
            usedSourceIds.add(layer.source);
        }
    }
    if (style.terrain?.source) usedSourceIds.add(style.terrain.source);
    return [...usedSourceIds].flatMap((id) => {
        const attribution = map.getSource(id)?.attribution;
        return typeof attribution === "string" ? [attribution] : [];
    });
}

/**
 * MapLibre's own rule (`StyleLayer.isHidden`): minzoom is inclusive, maxzoom exclusive, and a zero
 * bound counts as unset.
 */
function isShown(layer: LayerSpecification, zoom: number): boolean {
    if (layer.minzoom && zoom < layer.minzoom) return false;
    if (layer.maxzoom && zoom >= layer.maxzoom) return false;
    return layer.layout?.visibility !== "none";
}
