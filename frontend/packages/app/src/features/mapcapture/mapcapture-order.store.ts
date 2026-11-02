// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";
import { persist } from "zustand/middleware";

const STORAGE_KEY = "opencop:mapcapture-order";

interface MapCaptureOrderState {
    order: string[];
}

interface MapCaptureOrderActions {
    reorderCaptures(ids: string[]): void;
    reconcile(ids: string[]): void;
}

type MapCaptureOrderStore = MapCaptureOrderState & { actions: MapCaptureOrderActions };

export const useMapCaptureOrderStore = create<MapCaptureOrderStore>()(
    persist(
        (set) => ({
            order: [],
            actions: {
                reorderCaptures(ids) {
                    set((prev) => {
                        if (
                            ids.length === prev.order.length &&
                            ids.every((id, i) => id === prev.order[i])
                        )
                            return {};
                        return { order: ids };
                    });
                },
                reconcile(ids: string[]) {
                    set((prev) => {
                        const valid = new Set(ids);
                        const next = prev.order.filter((id) => valid.has(id));
                        return next.length === prev.order.length ? {} : { order: next };
                    });
                },
            },
        }),
        { name: STORAGE_KEY, version: 1, partialize: ({ order }) => ({ order }) },
    ),
);

export function useMapCaptureOrderActions(): MapCaptureOrderActions {
    return useMapCaptureOrderStore((s) => s.actions);
}
