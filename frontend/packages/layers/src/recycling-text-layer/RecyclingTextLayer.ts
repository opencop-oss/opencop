// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { UpdateParameters } from "@deck.gl/core";
import { TextLayer } from "@deck.gl/layers";
import Logger from "js-logger";
import { FONT_RECYCLE_AT_PX, shouldRecycleAtlas } from "./recycle.util";

const logger = Logger.get("RecyclingTextLayer");

/**
 * Minimal structural view of deck's (non-exported) `FontAtlasManager`. We never
 * import the class — we read these members off the instance deck already created
 * in `TextLayer.initializeState`. `deleteAtlas` is added by our deck patch
 * (`patches/@deck.gl+layers+9.3.1.patch`).
 */
interface FontAtlasManagerLike {
    readonly atlas?: { height: number } | undefined;
    readonly mapping?: Record<string, unknown> | undefined;
    /** OpenCOP patch: evict this manager's atlas from the shared cache + drop the local ref. */
    deleteAtlas: () => void;
}

/**
 * A drop-in {@link TextLayer} that bounds its own never-evicting SDF font atlas.
 *
 * deck.gl's font atlas lives in a **module-level LRU cache keyed by font config
 * only** (`fontFamily fontWeight fontSize buffer radius cutoff`, never the
 * character set). With `characterSet: "auto"` every distinct glyph ever seen for a
 * font config is appended to a 1024-wide canvas that grows monotonically and never
 * evicts. CJK's thousands of distinct glyphs push it past the GPU
 * `MAX_TEXTURE_SIZE` (Firefox 8192); the incomplete texture then makes the SDF
 * shader read alpha `1.0` everywhere → every label turns into a solid white bar.
 *
 * deck exposes no eviction API, so we add one: the {@link FontAtlasManagerLike.deleteAtlas}
 * method patched onto deck's `FontAtlasManager` (the underlying `LRUCache` already
 * has `delete`). When the atlas is under pressure and carries stale bloat (see
 * {@link shouldRecycleAtlas}), we evict this manager's atlas and let deck's own
 * `_updateFontAtlas` rebuild a fresh atlas from only the currently-live glyphs. The
 * atlas is keyed by the **real, untouched** font config — no `fontSize`/`fontFamily`
 * perturbation, so glyphs render pixel-identically and the prop contract is honored.
 *
 * Swap `TextLayer` → `RecyclingTextLayer` at any `characterSet: "auto"` SDF call
 * site to get this for free. The live label chain gets it by inheritance:
 * `MixedBillboardTextLayer` (→ `BinaryTextLayer` → `CulledTextLayer`, the layer
 * every composite label layer renders through) extends this instead of `TextLayer`,
 * so all live SDF labels are bounded at one chokepoint. Declared as a transparent
 * generic base (`<DataT, ExtraProps>` passed straight to `TextLayer`) so it composes
 * with those subclasses' own generics without a mixin's friction.
 */
export class RecyclingTextLayer<
    DataT = unknown,
    ExtraPropsT extends object = object,
> extends TextLayer<DataT, ExtraPropsT> {
    static layerName = "RecyclingTextLayer";

    updateState(params: UpdateParameters<this>): void {
        super.updateState(params);
        this.recycleFontAtlasIfNeeded();
    }

    private recycleFontAtlasIfNeeded(): void {
        const { fontAtlasManager, characterSet, styleVersion } = this.state as unknown as {
            fontAtlasManager: FontAtlasManagerLike;
            characterSet?: Set<string>;
            styleVersion: number;
        };

        const atlasHeight = fontAtlasManager.atlas?.height ?? 0;
        if (atlasHeight < FONT_RECYCLE_AT_PX) return; // cheap early-out

        const packedGlyphs = fontAtlasManager.mapping
            ? Object.keys(fontAtlasManager.mapping).length
            : 0;
        const liveDistinct = characterSet?.size ?? 0;
        if (!shouldRecycleAtlas({ atlasHeight, packedGlyphs, liveDistinct })) return;

        // Evict the bloated atlas from deck's shared cache and drop the manager's
        // reference (the patched `deleteAtlas`), bump `styleVersion` so the
        // `characters` sub-layer re-reads the new mapping, then re-run deck's own
        // `_updateFontAtlas` — which now sees no mapping and rebuilds a fresh atlas
        // from only the live glyphs, keyed by the real (untouched) font config.
        fontAtlasManager.deleteAtlas();
        this.setState({ styleVersion: styleVersion + 1 });
        (this as unknown as { _updateFontAtlas: () => boolean })._updateFontAtlas();

        if (import.meta.env.DEV) {
            logger.info(
                `recycled font atlas: ${packedGlyphs} packed / ` +
                    `${liveDistinct} live, height ${atlasHeight}px`,
            );
        }
    }
}
