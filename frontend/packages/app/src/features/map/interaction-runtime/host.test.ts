// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it } from "vitest";
import type { InteractionHost } from "./contract";
import { attachHost, getHost } from "./host";

/** One host at a time: the single-map assumption every runtime singleton rests on, made loud. */

const hostA = { a: true } as unknown as InteractionHost;
const hostB = { b: true } as unknown as InteractionHost;

const disposers: Array<() => void> = [];
afterEach(() => {
    disposers.splice(0).forEach((dispose) => dispose());
});

describe("attachHost", () => {
    it("attaches, and the disposer clears the slot", () => {
        const dispose = attachHost(hostA);
        expect(getHost()).toBe(hostA);
        dispose();
        expect(getHost()).toBeNull();
    });

    it("a second, different host over a bound one throws — one map per app", () => {
        disposers.push(attachHost(hostA));
        expect(() => attachHost(hostB)).toThrow(/one map per app/);
        expect(getHost()).toBe(hostA);
    });

    it("re-attaching the same host is allowed, and a detached slot takes a new host", () => {
        const first = attachHost(hostA);
        disposers.push(attachHost(hostA));
        first();
        disposers.push(attachHost(hostB));
        expect(getHost()).toBe(hostB);
    });

    it("a stale disposer leaves a newer host alone", () => {
        const disposeA = attachHost(hostA);
        disposeA();
        disposers.push(attachHost(hostB));
        disposeA();
        expect(getHost()).toBe(hostB);
    });
});
