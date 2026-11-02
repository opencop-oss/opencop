// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Point } from "./types/Point";

describe("Element", () => {
    describe("exemptFromStale", () => {
        it("is false for live elements (they expire normally)", () => {
            const el = Point.create({ position: [1, 2], meta: { objectId: "e1", source: "s1" } });
            expect(el.exemptFromStale).toBe(false);
        });

        it("is true for historical elements (versioned snapshots, never swept)", () => {
            const el = Point.create({
                position: [1, 2],
                meta: { id: "e1:0", objectId: "e1", source: "s1", elementClass: "historical" },
            });
            expect(el.exemptFromStale).toBe(true);
        });
    });

    it("precomputes a stable key from source and objectId", () => {
        const element = Point.create({
            position: [1, 2],
            meta: {
                objectId: "event-1",
                source: "source-1",
            },
        });

        expect(element.key).toBe(["source-1", "event-1"].join("\0"));
    });

    it("preserves explicit start and time timestamps in features and clones", () => {
        const element = Point.create({
            position: [1, 2],
            meta: {
                id: "element-1",
                objectId: "event-1",
                source: "source-1",
                displayName: "Test Event",
                start: 1_000,
                time: 2_000,
                stale: 3_000,
            },
        });

        expect(element.toFeature().properties.opencop.timestampz).toEqual({
            start: 1_000,
            time: 2_000,
            stale: 3_000,
        });

        expect(element.clone(true).toFeature().properties.opencop.timestampz).toEqual({
            start: 1_000,
            time: 2_000,
            stale: 3_000,
        });
    });

    it("omits start and time from toFeature() when not provided", () => {
        const element = Point.create({
            position: [1, 2],
            meta: {
                id: "element-1",
                objectId: "event-1",
                source: "source-1",
                displayName: "Test Event",
                stale: 86_400_000,
            },
        });
        const { timestampz } = element.toFeature().properties.opencop;

        expect(timestampz).toEqual({ stale: 86_400_000 });
        expect("start" in timestampz).toBe(false);
        expect("time" in timestampz).toBe(false);
    });
});
