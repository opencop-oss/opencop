// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { PartialOCFeature } from "@opencop/elements";
import { stampBroadcastTimestamps } from "./broadcast-feature.util";

const NOW = 1_800_000_000_000;

function feature(timestampz: Record<string, number>): PartialOCFeature {
    return {
        type: "Feature",
        geometry: { type: "Point", coordinates: [1, 2] },
        properties: { opencop: { displayName: "Alpha", timestampz } },
    } as unknown as PartialOCFeature;
}

describe("stampBroadcastTimestamps", () => {
    it("resets time to now and keeps start and stale", () => {
        const stamped = stampBroadcastTimestamps(
            feature({ start: NOW - 5000, time: NOW - 100, stale: NOW + 9000 }),
            NOW,
        );

        expect(stamped.properties.opencop.timestampz).toEqual({
            start: NOW - 5000,
            time: NOW,
            stale: NOW + 9000,
        });
    });

    it("falls back to now for a missing start", () => {
        const stamped = stampBroadcastTimestamps(feature({ stale: NOW + 9000 }), NOW);

        expect(stamped.properties.opencop.timestampz.start).toBe(NOW);
    });

    it("leaves the rest of the feature alone and does not mutate the input", () => {
        const input = feature({ stale: NOW + 9000 });

        const stamped = stampBroadcastTimestamps(input, NOW);

        expect(stamped.geometry).toEqual(input.geometry);
        expect(stamped.properties.opencop.displayName).toBe("Alpha");
        expect(input.properties.opencop.timestampz).toEqual({ stale: NOW + 9000 });
    });
});
