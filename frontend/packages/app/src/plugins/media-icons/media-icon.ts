// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * How a media icon's `href` becomes something drawable. A media icon carries
 * its image's location in its style's `href`, in one of three forms: an
 * `opencop:url:` reference to an image Core stored at import (with a `#WxH`
 * size fragment), an external `http(s)://` or `data:` URL kept from the import,
 * or a relative path that matched no imported image.
 *
 * A media icon is **self-describing**: its `href` carries everything needed to
 * draw it — a URL and, for Core-stored images, the artwork's intrinsic
 * dimensions — so it needs no catalog and nothing else to interpret it. The
 * resolution lives here, in the subject that draws media icons: not in
 * `@opencop/elements` (a data model that stores the `href` without reading it)
 * and not in the generic `IconElementLayer` (scheme-agnostic).
 */

const CORE_API_URL = String(import.meta.env.VITE_OPENCOP_CORE_API_URL ?? "").replace(/\/$/, "");
const MANAGED_PREFIX = "opencop:url:";
const CACHE_SIZE = 1_000;
// deck.gl uniformly scales loaded images into this bounding box and then
// rewrites their atlas dimensions, so the source aspect ratio is preserved.
const DEFAULT_ICON_DIMENSION = 64;

/**
 * A resolved media icon: a fetchable URL plus the intrinsic dimensions a
 * renderer needs to place it. Structurally compatible with `@opencop/layers`'
 * `UnpackedIcon`, so it reaches deck.gl without the layer knowing the scheme.
 */
export interface MediaIcon {
    /** The absolute image URL. */
    url: string;
    /** The source image width in pixels. */
    width: number;
    /** The source image height in pixels. */
    height: number;
    /**
     * Stable identity for atlas dedupe. Case-normalized for Core-stored
     * `opencop:url:` images; preserved as-is for external URLs, whose case may be
     * meaningful.
     */
    id: string;
}

/**
 * What a media icon whose `href` cannot be fetched draws: a "broken image" mark
 * (Material's `broken_image`), so the placemark stays visible and selectable
 * instead of disappearing. Inline, so drawing it never makes a request.
 */
export const UNRESOLVED_MEDIA_ICON: MediaIcon = {
    url:
        "data:image/svg+xml;utf8," +
        encodeURIComponent(
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="64" height="64">' +
                '<path fill="#ffffff" stroke="#000000" stroke-width="0.75" d="M21 5v6.59l-3-3.01-4 4.01-4-4-4 4-3-3.01V5c0-1.1.9-2 2-2h14c1.1 0 2 .9 2 2zm-3 6.42 3 3.01V19c0 1.1-.9 2-2 2H5c-1.1 0-2-.9-2-2v-6.58l3 2.99 4-4 4 4 4-3.99z"/>' +
                "</svg>",
        ),
    width: DEFAULT_ICON_DIMENSION,
    height: DEFAULT_ICON_DIMENSION,
    id: "__unresolved_media_icon__",
};

/**
 * True when the `href` names an image Core stored at import.
 *
 * Compares the prefix case-insensitively **without allocating** a lowercased
 * copy: the render pipeline resolves once per element on the add path, where a
 * per-element allocation is measurable on large source loads. `| 0x20` folds
 * ASCII letters to lower case and leaves `:` unchanged, which is sufficient
 * because the prefix is ASCII letters plus colons.
 */
function isManaged(href: string): boolean {
    if (href.length < MANAGED_PREFIX.length) return false;
    for (let i = 0; i < MANAGED_PREFIX.length; i++) {
        if ((href.charCodeAt(i) | 0x20) !== MANAGED_PREFIX.charCodeAt(i)) return false;
    }
    return true;
}

/**
 * True when the `href` is an external image reference — an `http(s)://` URL or
 * an inline `data:` URI — kept as imported. Matches Core's `KmzAssetResolver`,
 * which classifies the same three schemes as external.
 */
function isExternal(href: string): boolean {
    return /^(https?:\/\/|data:)/i.test(href);
}

const cache = new Map<string, MediaIcon>();

/**
 * On an HTTPS page a plain `http://` href is *blockable* mixed content: the
 * @loaders.gl `fetch()` call that loads map icons is never auto-upgraded, so
 * the icon silently fails to load. Rewriting the scheme costs nothing there —
 * the insecure URL could not have loaded on this page either way — while a
 * plain-HTTP deployment is left alone so http-only icon hosts keep resolving.
 * This does not help a host whose CORS policy lacks a permissive
 * `Access-Control-Allow-Origin` — `fetch()` enforces CORS where `<img src>`
 * would not — which remains a separate, unresolved limitation.
 */
function toFetchableUrl(href: string): string {
    if (typeof location === "undefined" || location.protocol !== "https:") return href;
    return href.replace(/^http:/i, "https:");
}

/**
 * Resolve a media icon's `href` to a {@link MediaIcon}.
 *
 *  - `opencop:url:/v1/layers/{layerId}/media/{mediaId}#WxH` (written by the
 *    Processor): strips the prefix, prepends the Core API base URL, parses the
 *    dimension fragment.
 *  - An external `http(s)://` or `data:` URL: uses a default square bounding
 *    box because the client never decodes the image to read its real
 *    dimensions. Its raw `href` remains the identity, although an `http://`
 *    source is upgraded when the page uses HTTPS.
 *  - Anything else — a relative path that matched no imported image — has
 *    nothing to fetch and resolves to {@link UNRESOLVED_MEDIA_ICON}.
 *
 * Results are memoized so repeated lookups skip reparsing and reallocating.
 */
export function resolveMediaIcon(href: string): MediaIcon {
    const external = isExternal(href);
    if (!external && !isManaged(href)) return UNRESOLVED_MEDIA_ICON;
    // Core-stored paths are generated identifiers and case-insensitive. A
    // remote URL is opaque: path, query, and fragment may all be case-sensitive.
    const cacheKey = external ? href : href.toLowerCase();
    const hit = cache.get(cacheKey);
    if (hit) {
        cache.delete(cacheKey);
        cache.set(cacheKey, hit);
        return hit;
    }

    if (external) {
        const src: MediaIcon = {
            url: toFetchableUrl(href),
            width: DEFAULT_ICON_DIMENSION,
            height: DEFAULT_ICON_DIMENSION,
            id: href,
        };
        cache.set(cacheKey, src);
        evictOldest();
        return src;
    }

    const raw = href.slice(MANAGED_PREFIX.length);
    const hashIdx = raw.indexOf("#");
    const path = (hashIdx >= 0 ? raw.slice(0, hashIdx) : raw).toLowerCase();
    const fragment = hashIdx >= 0 ? raw.slice(hashIdx + 1) : null;

    let width = DEFAULT_ICON_DIMENSION;
    let height = DEFAULT_ICON_DIMENSION;
    if (fragment) {
        const match = fragment.match(/^(\d+)[xX](\d+)$/);
        if (match) {
            // the regex above makes this safe, see tests, no need to check for NaN or partial failure
            width = Number(match[1]);
            height = Number(match[2]);
        }
    }

    const src: MediaIcon = {
        url: CORE_API_URL + path,
        width,
        height,
        id: cacheKey,
    };
    cache.set(cacheKey, src);
    evictOldest();
    return src;
}

function evictOldest(): void {
    if (cache.size > CACHE_SIZE) {
        // Evict the least-recently-used (first-inserted) entry. Guard on `.done`
        // rather than casting `.value` — the Map is non-empty here, but the guard
        // keeps the type honest without an `as string`.
        const oldest = cache.keys().next();
        if (!oldest.done) cache.delete(oldest.value);
    }
}
