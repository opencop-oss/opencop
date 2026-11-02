// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useScene } from "@opencop/scene";
import { useBackendFeatureEnabled } from "~/features/admin/runtime-features/use-feature-flag.hook";
import { useHistoricalMode } from "~/features/historical/data-plane/historical.store";
import { useSceneBinding } from "~/features/map/scene-binding/SceneBindingContext";
import { WatchboxPipeline } from "./WatchboxPipeline";
import { createWatchboxStore } from "./watchbox.store";
import { followWatchboxDraft } from "./watchbox-draft";

/**
 * Draws the saved watchboxes, and the one being drawn, through the scene while
 * the backend offers the watchbox feature, in live mode only. Must be rendered
 * inside a `SceneView`.
 */
export function WatchboxSceneLayer() {
    const enabled = useBackendFeatureEnabled("watchbox");
    const live = useHistoricalMode() === "live";
    const client = useQueryClient();
    const { scene } = useScene();
    const { sink } = useSceneBinding();

    useEffect(() => {
        if (!enabled || !live) return;
        const store = createWatchboxStore(client);
        const disconnect = store.connect();
        const pipeline = new WatchboxPipeline({ scene, store, sink });
        pipeline.initialize();
        const stopDraft = followWatchboxDraft(store);
        return () => {
            stopDraft();
            pipeline.erase();
            pipeline.dispose();
            disconnect();
        };
    }, [enabled, live, client, scene, sink]);

    return null;
}
