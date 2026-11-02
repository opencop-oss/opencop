// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";
import { persist, subscribeWithSelector } from "zustand/middleware";
import type { AngleUnit } from "@opencop/utils";
import type { AngleOrientation } from "@opencop/layers";

export type { AngleUnit } from "@opencop/utils";

export type MapSettings = {
    symbolSize: number;
    textSize: number;
    textZoomThreshold: number;
    /**
     * Text shown or hidden per element type, keyed by type. A type with no entry
     * shows its text; the toggles themselves come from the renderers.
     */
    textEnabled: Record<string, boolean>;
    accordion: string[];
    angleUnit: AngleUnit;
    angleOrientation: AngleOrientation;
    outlineColor: string;
};

interface MapSettingsStore extends MapSettings {
    setOutlineColor(outlineColor: string): void;
    setSymbolSize(symbolSize: number): void;
    setTextSize(textSize: number): void;
    setTextZoomThreshold(textZoomThreshold: number): void;
    setTextEnabled(value: boolean, type: string): void;
    setAllTextEnabled(value: boolean, types: readonly string[]): void;
    setAccordion(updater: (accordion: string[]) => string[]): void;
    setAngleUnit(unit: AngleUnit): void;
    setAngleOrientation(orientation: AngleOrientation): void;
}

export const DEFAULT_SYMBOL_SIZE = 30;
export const DEFAULT_TEXT_SIZE = 14;
export const DEFAULT_TEXT_ZOOM_THRESHOLD = 5;
export const DEFAULT_ANGLE_UNIT: AngleUnit = "both";
export const MAP_SETTINGS_STORAGE_KEY = "opencop:map-settings";
const LEGACY_MAP_SETTINGS_STORAGE_KEY = "mapSettings";

export const defaultMapSettings: MapSettings = {
    symbolSize: DEFAULT_SYMBOL_SIZE,
    textSize: DEFAULT_TEXT_SIZE,
    textZoomThreshold: DEFAULT_TEXT_ZOOM_THRESHOLD,
    textEnabled: {},
    accordion: [],
    angleUnit: DEFAULT_ANGLE_UNIT,
    angleOrientation: "ccw",
    outlineColor: "#ffffffff",
};

function loadLegacyMapSettings(): Partial<MapSettings> | null {
    try {
        const raw = localStorage.getItem(LEGACY_MAP_SETTINGS_STORAGE_KEY);
        if (!raw) return null;
        const parsed = JSON.parse(raw) as { state?: Partial<MapSettings> } | null;
        return parsed?.state ?? null;
    } catch {
        return null;
    }
}

export const useMapSettingsStore = create<MapSettingsStore>()(
    subscribeWithSelector(
        persist(
            (set) => ({
                ...defaultMapSettings,
                setOutlineColor: (outlineColor) => set({ outlineColor }),
                setSymbolSize: (symbolSize) =>
                    set((state) =>
                        state.symbolSize === symbolSize ? state : { ...state, symbolSize },
                    ),
                setTextSize: (textSize) =>
                    set((state) => (state.textSize === textSize ? state : { ...state, textSize })),
                setTextZoomThreshold: (textZoomThreshold) =>
                    set((state) =>
                        state.textZoomThreshold === textZoomThreshold
                            ? state
                            : { ...state, textZoomThreshold },
                    ),
                setTextEnabled: (value, type) =>
                    set((state) => ({
                        ...state,
                        textEnabled: { ...state.textEnabled, [type]: value },
                    })),
                setAllTextEnabled: (value, types) =>
                    set((state) => ({
                        ...state,
                        textEnabled: {
                            ...state.textEnabled,
                            ...Object.fromEntries(types.map((type) => [type, value])),
                        },
                    })),
                setAccordion: (updater) =>
                    set((state) => ({
                        ...state,
                        accordion: updater(state.accordion),
                    })),
                setAngleUnit: (angleUnit) =>
                    set((state) =>
                        state.angleUnit === angleUnit ? state : { ...state, angleUnit },
                    ),
                setAngleOrientation: (angleOrientation) => set({ angleOrientation }),
            }),
            {
                name: MAP_SETTINGS_STORAGE_KEY,
                merge: (persisted, current) => {
                    const persistedSettings = (persisted ?? {}) as Partial<MapSettings>;
                    return {
                        ...current,
                        ...defaultMapSettings,
                        ...persistedSettings,
                        textEnabled: { ...persistedSettings.textEnabled },
                    };
                },
                onRehydrateStorage: () => (_state, error) => {
                    if (error) return;
                    if (localStorage.getItem(MAP_SETTINGS_STORAGE_KEY) !== null) return;

                    const legacyState = loadLegacyMapSettings();
                    if (!legacyState) return;

                    useMapSettingsStore.setState({
                        ...legacyState,
                        textEnabled: { ...legacyState.textEnabled },
                    });
                    localStorage.removeItem(LEGACY_MAP_SETTINGS_STORAGE_KEY);
                },
                partialize: ({
                    symbolSize,
                    textSize,
                    textZoomThreshold,
                    textEnabled,
                    accordion,
                    angleUnit,
                    angleOrientation,
                    outlineColor,
                }) => ({
                    symbolSize,
                    textSize,
                    textZoomThreshold,
                    textEnabled,
                    accordion,
                    angleUnit,
                    angleOrientation,
                    outlineColor,
                }),
            },
        ),
    ),
);
