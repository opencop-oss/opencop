// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";
import { persist } from "zustand/middleware";

export interface WeatherLayerEntry {
    id: string;
    active: boolean;
    opacity: number;
}

interface WeatherState {
    layers: WeatherLayerEntry[];
}

interface WeatherActions {
    toggleLayer(id: string): void;
    setOpacity(id: string, opacity: number): void;
    reorderLayers(ids: string[]): void;
    reconcile(tileIds: string[]): void;
}

type WeatherStore = WeatherState & WeatherActions;

export const DEFAULT_OPACITY = 50;
const STORAGE_KEY = "opencop:weather";

export function getWeatherOpacity(layers: WeatherLayerEntry[], id: string): number {
    return layers.find((l) => l.id === id)?.opacity ?? DEFAULT_OPACITY;
}

export const useWeatherStore = create<WeatherStore>()(
    persist(
        (set) => ({
            layers: [],
            toggleLayer(id) {
                set((prev) => ({
                    layers: prev.layers.map((l) => (l.id === id ? { ...l, active: !l.active } : l)),
                }));
            },
            setOpacity(id, opacity) {
                set((prev) => ({
                    layers: prev.layers.map((l) => (l.id === id ? { ...l, opacity } : l)),
                }));
            },
            reorderLayers(ids) {
                set((prev) => {
                    const byId = new Map(prev.layers.map((l) => [l.id, l]));
                    return {
                        layers: ids.map(
                            (id) => byId.get(id) ?? { id, active: false, opacity: DEFAULT_OPACITY },
                        ),
                    };
                });
            },
            reconcile(tileIds) {
                set((prev) => {
                    const byId = new Map(prev.layers.map((l) => [l.id, l]));
                    const next: WeatherLayerEntry[] = tileIds.map(
                        (id) => byId.get(id) ?? { id, active: false, opacity: DEFAULT_OPACITY },
                    );
                    const changed =
                        next.length !== prev.layers.length ||
                        next.some((l, i) => l !== prev.layers[i]);
                    return changed ? { layers: next } : {};
                });
            },
        }),
        {
            name: STORAGE_KEY,
            version: 1,
            migrate(persisted, fromVersion) {
                if (fromVersion === 0) {
                    try {
                        const old = persisted as {
                            activeWeatherLayers?: { values?: string[] };
                            opacities?: { entries?: [string, number][] };
                            layerOrder?: string[];
                        };
                        const activeSet = new Set(old.activeWeatherLayers?.values ?? []);
                        const opacityMap = new Map(old.opacities?.entries ?? []);
                        const order = old.layerOrder ?? [];
                        const seen = new Set<string>();
                        const layers: WeatherLayerEntry[] = [];
                        for (const id of order) {
                            if (typeof id === "string") {
                                seen.add(id);
                                layers.push({
                                    id,
                                    active: activeSet.has(id),
                                    opacity: opacityMap.get(id) ?? DEFAULT_OPACITY,
                                });
                            }
                        }
                        for (const id of activeSet) {
                            if (!seen.has(id)) {
                                layers.push({
                                    id,
                                    active: true,
                                    opacity: opacityMap.get(id) ?? DEFAULT_OPACITY,
                                });
                            }
                        }
                        return { layers };
                    } catch {
                        return { layers: [] };
                    }
                }
                return persisted as WeatherState;
            },
            partialize: ({ layers }) => ({ layers }),
        },
    ),
);
