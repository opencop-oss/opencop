// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Primitive } from "@opencop/scene";
import { RenderPipeline } from "~/features/map/scene-binding/RenderPipeline";
import { pinLabels } from "~/features/map/scene-binding/elements/element-pipeline.util";
import { useWatchboxPanelStore } from "./watchbox-panel.store";
import { useWatchboxVisibilityStore } from "./watchbox-visibility.store";
import { isWatchboxVisible, watchboxPrimitives } from "./watchbox.mapper";
import type { WatchboxObject, WatchboxStore } from "./watchbox.store";

/**
 * Draws the saved watchboxes. A watchbox is labelled while it is selected or
 * hovered in the Watchbox panel, and disabled ones disappear while the
 * hide-disabled setting is on.
 */
export class WatchboxPipeline extends RenderPipeline<WatchboxStore> {
    private cleanup?: () => void;

    public override initialize(): void {
        super.initialize();
        const detachPanel = useWatchboxPanelStore.subscribe((state, previous) => {
            const keys = new Set([
                state.selected,
                state.hovered,
                previous.selected,
                previous.hovered,
            ]);
            if (state.selected === previous.selected && state.hovered === previous.hovered) return;
            for (const key of keys) {
                const object = key ? this.store.get(key) : undefined;
                if (object) this.update(object);
            }
        });
        const detachVisibility = useWatchboxVisibilityStore.subscribe((state, previous) => {
            if (state.hideDisabled === previous.hideDisabled) return;
            for (const object of this.store.getAll()) this.update(object);
        });
        this.cleanup = () => (detachPanel(), detachVisibility());
    }

    public override dispose(): void {
        super.dispose();
        this.cleanup?.();
    }

    public erase(): void {
        for (const prims of this.primitives.values()) this.scene.remove(prims.map((p) => p.id));
        this.primitives.clear();
    }

    protected map(object: WatchboxObject): Primitive[] | undefined {
        if (!isWatchboxVisible(object)) return;
        const { selected, hovered } = useWatchboxPanelStore.getState();
        const labelled = object.key === selected || object.key === hovered;
        return pinLabels(watchboxPrimitives(object, labelled));
    }
}
