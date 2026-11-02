// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createIterable, type UpdateParameters } from "@deck.gl/core";
import { IconLayer } from "@deck.gl/layers";
import Logger from "js-logger";
import { RECYCLE_AT_PX, shouldRecycleAtlas } from "./recycle.util";

const logger = Logger.get("RecyclingIconLayer");
/**
 * Minimal structural view of deck's (non-exported) `IconManager`. We never
 * import the class — we read these members off the instance deck already
 * created and rebuild from its own runtime constructor, so this stays
 * version-agnostic and doesn't reach into `@deck.gl/layers` dist internals.
 */
interface IconManagerLike {
    getTexture: () => { height: number } | null;
    _mapping?: Record<string, unknown>;
    onUpdate: (didFrameChange: boolean) => void;
    onError: (context: unknown) => void;
    setProps: (props: Record<string, unknown>) => void;
    packIcons: (data: unknown, getIcon: unknown) => void;
    finalize: () => void;
}

type UnpackedIconLike = { id?: string; url?: string };

/**
 * A drop-in {@link IconLayer} that bounds its own never-evicting auto-packed
 * atlas.
 *
 * deck.gl's icon atlas never evicts: every distinct icon (`getIcon(d).id || .url`)
 * ever seen accumulates into a 1024-wide texture whose height doubles until it
 * exceeds the GPU `MAX_TEXTURE_SIZE` (Firefox 8192, Chrome 16384), at which
 * point the texture allocation fails and every icon samples opaque black. There
 * is no public evict/reset API.
 *
 * Unlike the composite-level fix (which recycles by cycling a sub-layer id so
 * the *parent* rebuilds the layer), this works from inside the primitive layer:
 * when the atlas is under pressure and carries stale bloat (see
 * {@link shouldRecycleAtlas}), it **rebuilds the `IconManager` in place** —
 * spins up a fresh manager (reusing deck's own runtime class and the layer-bound
 * `onUpdate`/`onError` callbacks), repacks only the currently-live icons, and
 * finalizes the bloated one. The reused `onUpdate` is `IconLayer._onUpdate`, so
 * the `getIcon` attribute is invalidated against the new atlas automatically.
 * Swap `IconLayer` → `RecyclingIconLayer` at any auto-packing call site to get
 * this for free.
 */
export class RecyclingIconLayer<
    DataT = unknown,
    ExtraPropsT extends object = object,
> extends IconLayer<DataT, ExtraPropsT> {
    static layerName = "RecyclingIconLayer";

    updateState(params: UpdateParameters<this>): void {
        super.updateState(params);
        this.recycleIconAtlasIfNeeded();
    }

    private recycleIconAtlasIfNeeded(): void {
        const manager = this.state.iconManager as unknown as IconManagerLike | undefined;
        const atlasHeight = manager?.getTexture?.()?.height ?? 0;
        if (atlasHeight < RECYCLE_AT_PX || !manager) return; // cheap early-out

        const packedSlots = manager._mapping ? Object.keys(manager._mapping).length : 0;
        const liveDistinct = this.countLiveDistinct();
        if (!shouldRecycleAtlas({ atlasHeight, packedSlots, liveDistinct })) return;

        // Rebuild the atlas in place. Reuse the existing manager's runtime class
        // and its (already layer-bound) callbacks so we neither import deck
        // internals nor re-wire the update/error plumbing.
        const ManagerClass = (manager as object).constructor as new (
            device: unknown,
            opts: { onUpdate: unknown; onError: unknown },
        ) => IconManagerLike;

        const fresh = new ManagerClass(this.context.device, {
            onUpdate: manager.onUpdate,
            onError: manager.onError,
        });
        fresh.setProps({
            loadOptions: this.props.loadOptions,
            autoPacking: true,
            textureParameters: this.props.textureParameters,
        });
        fresh.packIcons(this.props.data, this.props.getIcon);
        manager.finalize();
        this.setState({ iconManager: fresh });
        // No explicit attribute invalidation needed: `fresh.onUpdate` is the
        // layer-bound `IconLayer._onUpdate`, which invalidates the `getIcon`
        // accessor (the `instanceIconDefs` attribute) when the repacked atlas's
        // frames are ready.

        if (import.meta.env.DEV) {
            logger.info(
                `recycled icon atlas: ${packedSlots} packed / ` +
                    `${liveDistinct} live, height ${atlasHeight}px`,
            );
        }
    }

    /** Distinct icon ids (`id || url`, deck's atlas dedup key) across current data. */
    private countLiveDistinct(): number {
        const { data, getIcon } = this.props;
        if (typeof getIcon !== "function") return 0;
        const accessor = getIcon as unknown as (
            object: DataT,
            info: { index: number },
        ) => UnpackedIconLike;
        const seen = new Set<string>();
        const { iterable, objectInfo } = createIterable(data);
        for (const object of iterable) {
            objectInfo.index++;
            const icon = accessor(object, objectInfo);
            if (icon) seen.add(icon.id || icon.url || "");
        }
        return seen.size;
    }
}
