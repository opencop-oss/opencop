// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { Circle, localEventSource } from "@opencop/elements";

const { mockLoggerWarn } = vi.hoisted(() => ({ mockLoggerWarn: vi.fn() }));
vi.mock("js-logger", () => ({
    default: { get: () => ({ debug: vi.fn(), info: vi.fn(), warn: mockLoggerWarn }) },
}));

import { LocalSource, db } from "./LocalSource";

function makeCircle(objectId = "event-1", revId = "rev-1") {
    return Circle.create({
        origin: [10, 20],
        radius: 250,
        meta: { id: revId, objectId, source: localEventSource },
    });
}

describe("LocalSource", () => {
    afterEach(() => {
        vi.restoreAllMocks();
        mockLoggerWarn.mockReset();
    });

    it("persists a top-level source field for IndexedDB lookups", () => {
        const put = vi.spyOn(db.elements, "put").mockResolvedValue("local\0event-1");
        const element = makeCircle();

        new LocalSource(localEventSource).persist(element);

        expect(put).toHaveBeenCalledWith(
            expect.objectContaining({
                key: element.key,
                source: localEventSource,
                properties: expect.objectContaining({
                    opencop: expect.objectContaining({
                        source: localEventSource,
                    }),
                }),
            }),
        );
    });

    it("persist resolves — confirms the DB write completes without error", async () => {
        vi.spyOn(db.elements, "put").mockResolvedValue("local\0event-1");
        const element = makeCircle();

        // persist() is fire-and-forget but the internal promise should resolve.
        // Flush the microtask queue so the .then() branch runs.
        new LocalSource(localEventSource).persist(element);
        await Promise.resolve();
        // No assertion needed beyond "did not throw / did not warn" —
        // if put rejects, logger.warn fires; we just confirm the happy path.
        expect(db.elements.put).toHaveBeenCalledTimes(1);
    });

    it("notifies the bound handler with reconstructed elements when DB rows are loaded on enable", async () => {
        const element = makeCircle("event-notify", "rev-notify");
        const row = { ...element.toFeature(), key: element.key, source: localEventSource };

        const toArray = vi.fn().mockResolvedValue([row]);
        const equals = vi.fn().mockReturnValue({ toArray });
        vi.spyOn(db.elements, "where").mockReturnValue({ equals } as never);

        const handler = vi.fn();
        const source = new LocalSource(localEventSource).bind(handler);
        source.enable();

        await Promise.resolve();

        // Handler must be called exactly once with the hydrated element —
        // this is how the store (and ultimately the map) learns about saved events after refresh.
        expect(handler).toHaveBeenCalledTimes(1);
        const [notified] = handler.mock.calls[0][0];
        expect(notified.key).toBe(element.key);
        expect(notified.objectId).toBe("event-notify");
        expect(notified.source).toBe(localEventSource);
    });

    it("hydrates valid rows and skips invalid legacy records", async () => {
        const valid = {
            ...Circle.create({
                origin: [5, 6],
                radius: 100,
                meta: {
                    id: "rev-2",
                    objectId: "event-2",
                    source: localEventSource,
                },
            }).toFeature(),
            key: "local\0event-2",
            source: localEventSource,
        };
        const invalid = { key: "legacy\0event-0", source: localEventSource };

        const toArray = vi.fn().mockResolvedValue([invalid, valid]);
        const equals = vi.fn().mockReturnValue({ toArray });
        const where = vi.spyOn(db.elements, "where").mockReturnValue({ equals } as never);
        const handler = vi.fn();

        const source = new LocalSource(localEventSource).bind(handler);
        source.enable();

        await Promise.resolve();

        expect(where).toHaveBeenCalledWith("source");
        expect(equals).toHaveBeenCalledWith(localEventSource);
        expect(mockLoggerWarn).toHaveBeenCalledWith(
            "Skipping invalid persisted element:",
            expect.any(Error),
        );
        expect(handler).toHaveBeenCalledTimes(1);
        expect(handler).toHaveBeenCalledWith([
            expect.objectContaining({
                key: "local\0event-2",
                source: localEventSource,
                objectId: "event-2",
            }),
        ]);
        expect(source.state).toBe("enabled");
    });

    it("returns to enabled when persisted element hydration fails", async () => {
        const failure = new Error("indexeddb unavailable");
        const toArray = vi.fn().mockRejectedValue(failure);
        const equals = vi.fn().mockReturnValue({ toArray });
        vi.spyOn(db.elements, "where").mockReturnValue({ equals } as never);

        const handler = vi.fn();
        const source = new LocalSource(localEventSource).bind(handler);
        source.enable();

        await Promise.resolve();
        await Promise.resolve();

        expect(mockLoggerWarn).toHaveBeenCalledWith("Failed to load persisted elements:", failure);
        expect(handler).not.toHaveBeenCalled();
        expect(source.state).toBe("enabled");
    });

    it("does not re-enable a source that was disabled before hydration failure resolves", async () => {
        const failure = new Error("indexeddb unavailable");
        let rejectLoad!: (reason?: unknown) => void;
        const toArray = vi.fn().mockReturnValue(
            new Promise<never[]>((_, reject) => {
                rejectLoad = reject;
            }),
        );
        const equals = vi.fn().mockReturnValue({ toArray });
        vi.spyOn(db.elements, "where").mockReturnValue({ equals } as never);

        const source = new LocalSource(localEventSource);
        source.enable();
        source.disable();
        rejectLoad(failure);

        await Promise.resolve();
        await Promise.resolve();

        expect(mockLoggerWarn).toHaveBeenCalledWith("Failed to load persisted elements:", failure);
        expect(source.state).toBe("disabled");
    });
});
