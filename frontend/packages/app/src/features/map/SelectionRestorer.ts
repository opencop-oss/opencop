// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect } from "react";
import { useMapManager } from "./MapManagerContext";
import { useMapStore } from "./core/map.store";
import { elementKeyToRef } from "@opencop/elements";

// Because the selection logic is very tied to `mapManager` instead of the element pipeline, we have to subscribe transiently to the store.
// This will change when layer constructiong gets refactored, selection logic will be reactive to the entire store and decoupled from MapManager.
/** Temporary restorer for selection. This can go away whenever layer construction gets refactored to react to the store */
export function SelectionRestorer() {
    const mapManager = useMapManager();

    useEffect(() => {
        const selected = useMapStore.getState().selected;
        if (!selected) return;

        const ref = elementKeyToRef(selected);
        if (!ref) return;

        mapManager.restoreSelection(ref);
    }, [mapManager]);

    return null;
}
