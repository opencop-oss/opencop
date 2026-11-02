// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { Source, type SourceState } from "./Source";
import type { Element } from "@opencop/elements";

class TestSource extends Source {
    startCalls = 0;
    stopCalls = 0;

    protected onEnable(): void {
        this.setState("enabled");
        this.startCalls++;
    }

    protected onDisable(): void {
        this.stopCalls++;
    }

    /** Expose emit for testing. */
    testHandle(elements: Element[]) {
        this.handler?.(elements);
    }
}

describe("Source", () => {
    it("initializes with disabled state", () => {
        const source = new TestSource("test-source");
        expect(source.id).toBe("test-source");
        expect(source.state).toBe("disabled");
    });

    describe("enable", () => {
        it("transitions to syncing then calls handleStart", () => {
            const source = new TestSource("s1");
            const states: SourceState[] = [];

            // Subclass sets state in handleStart, but enable() sets syncing first
            const originalHandleStart = source["onEnable"].bind(source);
            source["onEnable"] = function () {
                // At this point, _state should have been set to syncing by enable()
                states.push(source.state);
                originalHandleStart();
                states.push(source.state);
            };

            source.enable();
            expect(states).toEqual(["syncing", "enabled"]);
        });
    });

    describe("disable", () => {
        it("calls handleStop and sets state to disabled", () => {
            const source = new TestSource("s1");
            source.enable();
            expect(source.state).toBe("enabled");

            source.disable();
            expect(source.state).toBe("disabled");
            expect(source.stopCalls).toBe(1);
        });
    });

    describe("bind", () => {
        it("sets the emit function used to push elements", () => {
            const source = new TestSource("s1");
            const handler = vi.fn();
            source.bind(handler);

            const fakeElements = [{ key: "a" }] as unknown as Element[];
            source.testHandle(fakeElements);

            expect(handler).toHaveBeenCalledWith(fakeElements);
        });

        it("does not throw when emit is called without bind", () => {
            const source = new TestSource("s1");
            expect(() => source.testHandle([])).not.toThrow();
        });
    });
});
