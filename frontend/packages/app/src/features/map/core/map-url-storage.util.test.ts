// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createMapUrlStorage, type MapUrlStorage } from "./map-url-storage.util";

const setLocation = (url: string) => window.history.replaceState({}, "", url);
const param = (key: string) => new URL(window.location.href).searchParams.get(key);

describe("createMapUrlStorage", () => {
    const created: MapUrlStorage[] = [];
    const make = (timeout?: number, version?: number) => {
        const storage = createMapUrlStorage(timeout, version);
        created.push(storage);
        return storage;
    };

    beforeEach(() => {
        vi.useFakeTimers();
        setLocation("/map");
    });

    afterEach(() => {
        // Tear down listeners and timers from every storage instance this test created.
        while (created.length > 0) created.pop()!.dispose();
        vi.useRealTimers();
    });

    describe("getItem", () => {
        it("decodes flat URL params into partial state", () => {
            setLocation("/map?lat=40&lng=-74&z=10");
            const storage = make();
            expect(storage.getItem("map-url")).toEqual({
                state: { latitude: 40, longitude: -74, zoom: 10 },
                version: 0,
            });
        });

        it("returns empty state when no codec params present", () => {
            const storage = make();
            expect(storage.getItem("map-url")).toEqual({ state: {}, version: 0 });
        });
    });

    describe("setItem", () => {
        it("debounces the URL write", () => {
            const storage = make(250);
            storage.setItem("map-url", {
                state: { latitude: 40, longitude: -74, zoom: 10, pitch: 0, bearing: 0 },
            });
            expect(param("lat")).toBeNull();
            vi.advanceTimersByTime(249);
            expect(param("lat")).toBeNull();
            vi.advanceTimersByTime(1);
            expect(param("lat")).toBe("40");
            expect(param("lng")).toBe("-74");
            expect(param("z")).toBe("10");
        });

        it("batches all fields into a single pushState per flush", () => {
            const pushSpy = vi.spyOn(window.history, "pushState");
            const storage = make(250);
            storage.setItem("map-url", {
                state: { latitude: 40, longitude: -74, zoom: 10, pitch: 30, bearing: 45 },
            });
            vi.advanceTimersByTime(250);
            expect(pushSpy).toHaveBeenCalledTimes(1);
            pushSpy.mockRestore();
        });

        it("coalesces rapid writes", () => {
            const storage = make(250);
            storage.setItem("map-url", { state: { latitude: 1 } });
            vi.advanceTimersByTime(100);
            storage.setItem("map-url", { state: { latitude: 2 } });
            vi.advanceTimersByTime(100);
            storage.setItem("map-url", { state: { latitude: 3 } });
            vi.advanceTimersByTime(250);
            expect(param("lat")).toBe("3");
        });

        it("skips writing when params already match (rehydrate echo)", () => {
            setLocation("/map?lat=40&lng=-74&z=10");
            const pushSpy = vi.spyOn(window.history, "pushState");
            const storage = make(250);
            storage.setItem("map-url", {
                state: { latitude: 40, longitude: -74, zoom: 10 },
            });
            vi.advanceTimersByTime(250);
            expect(pushSpy).not.toHaveBeenCalled();
            pushSpy.mockRestore();
        });

        it("does not write when not on /map", () => {
            setLocation("/other");
            const pushSpy = vi.spyOn(window.history, "pushState");
            const storage = make(250);
            storage.setItem("map-url", { state: { latitude: 40 } });
            vi.advanceTimersByTime(250);
            expect(pushSpy).not.toHaveBeenCalled();
            pushSpy.mockRestore();
        });

        it("preserves foreign params", () => {
            setLocation("/map?utm_source=email&keep=me");
            const storage = make(250);
            storage.setItem("map-url", { state: { latitude: 40, longitude: -74 } });
            vi.advanceTimersByTime(250);
            expect(param("utm_source")).toBe("email");
            expect(param("keep")).toBe("me");
            expect(param("lat")).toBe("40");
            expect(param("lng")).toBe("-74");
        });

        it("deletes selected when value is null", () => {
            setLocation("/map?selected=ns:id");
            const storage = make(250);
            storage.setItem("map-url", { state: { selected: null } });
            vi.advanceTimersByTime(250);
            expect(param("selected")).toBeNull();
        });

        it("writes URL with no percent-encoded JSON", () => {
            const storage = make(250);
            storage.setItem("map-url", {
                state: { latitude: 40, longitude: -74, zoom: 10, pitch: 0, bearing: 0 },
            });
            vi.advanceTimersByTime(250);
            expect(window.location.search).not.toContain("%7B");
            expect(window.location.search).not.toContain("opencop");
            expect(window.location.search).not.toContain("mapstore");
        });
    });

    describe("removeItem", () => {
        it("clears only codec keys", () => {
            setLocation("/map?lat=40&lng=-74&z=10&keep=me");
            const storage = make();
            storage.removeItem("map-url");
            expect(param("lat")).toBeNull();
            expect(param("lng")).toBeNull();
            expect(param("z")).toBeNull();
            expect(param("keep")).toBe("me");
        });

        it("cancels a pending setItem", () => {
            const storage = make(250);
            storage.setItem("map-url", { state: { latitude: 40 } });
            storage.removeItem("map-url");
            vi.advanceTimersByTime(250);
            expect(param("lat")).toBeNull();
        });
    });

    describe("flush on lifecycle events", () => {
        it("flushes pending writes on pagehide", () => {
            const storage = make(250);
            storage.setItem("map-url", { state: { latitude: 40 } });
            expect(param("lat")).toBeNull();
            window.dispatchEvent(new Event("pagehide"));
            expect(param("lat")).toBe("40");
        });

        it("flushes pending writes when visibilitychange goes to hidden", () => {
            const storage = make(250);
            storage.setItem("map-url", { state: { latitude: 40 } });
            Object.defineProperty(document, "visibilityState", {
                configurable: true,
                value: "hidden",
            });
            document.dispatchEvent(new Event("visibilitychange"));
            expect(param("lat")).toBe("40");
        });

        it("does not flush when visibilitychange goes to visible", () => {
            const storage = make(250);
            storage.setItem("map-url", { state: { latitude: 40 } });
            Object.defineProperty(document, "visibilityState", {
                configurable: true,
                value: "visible",
            });
            document.dispatchEvent(new Event("visibilitychange"));
            expect(param("lat")).toBeNull();
        });
    });
});
