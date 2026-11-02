// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
    defaultMapSettings,
    MAP_SETTINGS_STORAGE_KEY,
    useMapSettingsStore,
} from "./map-settings.store";

const LEGACY_MAP_SETTINGS_STORAGE_KEY = "mapSettings";

function resetStore() {
    useMapSettingsStore.setState({ ...defaultMapSettings });
    localStorage.removeItem(MAP_SETTINGS_STORAGE_KEY);
    localStorage.removeItem(LEGACY_MAP_SETTINGS_STORAGE_KEY);
}

beforeEach(resetStore);
afterEach(resetStore);

describe("map settings persistence", () => {
    it("defaults textZoomThreshold to 5", () => {
        expect(useMapSettingsStore.getState().textZoomThreshold).toBe(5);
    });

    it("persists textZoomThreshold updates under the namespaced storage key", () => {
        useMapSettingsStore.getState().setTextZoomThreshold(2.5);

        expect(useMapSettingsStore.getState().textZoomThreshold).toBe(2.5);
        expect(
            JSON.parse(localStorage.getItem(MAP_SETTINGS_STORAGE_KEY)!).state.textZoomThreshold,
        ).toBe(2.5);
    });

    it("enables every registered text type for fresh settings", () => {
        expect(Object.values(useMapSettingsStore.getState().textEnabled)).toEqual(
            Object.values(defaultMapSettings.textEnabled).map(() => true),
        );
    });

    it("preserves an explicitly persisted disabled text type", async () => {
        localStorage.setItem(
            MAP_SETTINGS_STORAGE_KEY,
            JSON.stringify({ state: { textEnabled: { circle: false } }, version: 0 }),
        );

        await useMapSettingsStore.persist.rehydrate();

        expect(useMapSettingsStore.getState().textEnabled.circle).toBe(false);
    });

    it("keeps a persisted text toggle and leaves an unlisted type unset, which reads as shown", async () => {
        localStorage.setItem(
            MAP_SETTINGS_STORAGE_KEY,
            JSON.stringify({ state: { textEnabled: { circle: false } }, version: 0 }),
        );

        await useMapSettingsStore.persist.rehydrate();

        // The store names no element types: a type with no entry shows its text.
        expect(useMapSettingsStore.getState().textEnabled).toEqual({ circle: false });
    });

    it("persists updates under the namespaced storage key", () => {
        useMapSettingsStore.getState().setSymbolSize(42);

        expect(localStorage.getItem(LEGACY_MAP_SETTINGS_STORAGE_KEY)).toBeNull();
        expect(JSON.parse(localStorage.getItem(MAP_SETTINGS_STORAGE_KEY)!).state.symbolSize).toBe(
            42,
        );
    });

    it("migrates legacy mapSettings storage into the namespaced key", async () => {
        localStorage.setItem(
            LEGACY_MAP_SETTINGS_STORAGE_KEY,
            JSON.stringify({
                state: {
                    symbolSize: 55,
                    angleUnit: "mils",
                },
                version: 0,
            }),
        );

        await useMapSettingsStore.persist.rehydrate();

        expect(useMapSettingsStore.getState().symbolSize).toBe(55);
        expect(useMapSettingsStore.getState().angleUnit).toBe("mils");
        expect(useMapSettingsStore.getState().textSize).toBe(defaultMapSettings.textSize);
        expect(useMapSettingsStore.getState().textZoomThreshold).toBe(
            defaultMapSettings.textZoomThreshold,
        );
        expect(localStorage.getItem(LEGACY_MAP_SETTINGS_STORAGE_KEY)).toBeNull();
        expect(JSON.parse(localStorage.getItem(MAP_SETTINGS_STORAGE_KEY)!).state).toMatchObject({
            symbolSize: 55,
            angleUnit: "mils",
        });
    });

    it("keeps legacy text toggles when migrating, and adds none", async () => {
        localStorage.setItem(
            LEGACY_MAP_SETTINGS_STORAGE_KEY,
            JSON.stringify({
                state: {
                    textEnabled: { circle: false },
                },
                version: 0,
            }),
        );

        await useMapSettingsStore.persist.rehydrate();

        expect(useMapSettingsStore.getState().textEnabled).toEqual({ circle: false });
    });
});
