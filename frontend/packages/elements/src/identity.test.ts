// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import {
    elementKeyToRef,
    elementRefToKey,
    elementRefsEqual,
    getElementKey,
    localEventSource,
    parseElementKey,
    toElementRef,
} from "./identity";

describe("identity", () => {
    it("builds compact separator-based keys", () => {
        expect(getElementKey({ source: "source-1", objectId: "event-1" })).toBe(
            ["source-1", "event-1"].join("\0"),
        );
    });

    it("defaults missing source to the local event source", () => {
        const key = getElementKey({ objectId: "event-1" });
        expect(parseElementKey(key)).toEqual({
            sourceId: localEventSource,
            objectId: "event-1",
        });
    });

    it("normalizes element identities into resolved refs", () => {
        expect(toElementRef({ objectId: "event-1" })).toEqual({
            sourceId: localEventSource,
            objectId: "event-1",
        });
    });

    it("round-trips refs through explicit helper aliases", () => {
        const ref = { sourceId: "source-1", objectId: "event-1" };
        expect(elementKeyToRef(elementRefToKey(ref))).toEqual(ref);
    });

    it("compares refs structurally", () => {
        expect(
            elementRefsEqual(
                { sourceId: "source-1", objectId: "event-1" },
                { sourceId: "source-1", objectId: "event-1" },
            ),
        ).toBe(true);
        expect(
            elementRefsEqual(
                { sourceId: "source-1", objectId: "event-1" },
                { sourceId: "source-2", objectId: "event-1" },
            ),
        ).toBe(false);
    });

    it("returns null for objectId-only identifiers", () => {
        expect(parseElementKey("event-1")).toBeNull();
    });

    it("returns null for the old JSON array key format", () => {
        expect(parseElementKey('["source-1","event-1"]')).toBeNull();
    });
});
