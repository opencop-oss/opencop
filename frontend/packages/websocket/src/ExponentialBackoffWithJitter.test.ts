// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { ExponentialBackoffWithJitter } from "./ExponentialBackoffWithJitter";

describe("ExponentialBackoffWithJitter", () => {
    it("should double delay with each retry, capped at maxMs", () => {
        vi.spyOn(Math, "random").mockReturnValue(0);
        const backoff = new ExponentialBackoffWithJitter(1000, 30000);

        expect(backoff.next()).toBe(1000); // 1000 * 2^0
        expect(backoff.next()).toBe(2000); // 1000 * 2^1
        expect(backoff.next()).toBe(4000); // 1000 * 2^2
        expect(backoff.next()).toBe(8000); // 1000 * 2^3
        expect(backoff.next()).toBe(16000); // 1000 * 2^4
        expect(backoff.next()).toBe(30000); // capped at maxMs
        expect(backoff.next()).toBe(30000); // stays capped

        vi.restoreAllMocks();
    });

    it("should add 0-1000ms jitter", () => {
        vi.spyOn(Math, "random").mockReturnValue(0);
        const backoff = new ExponentialBackoffWithJitter(1000, 30000);
        expect(backoff.current).toBe(1000);
        vi.restoreAllMocks();

        vi.spyOn(Math, "random").mockReturnValue(0.999);
        expect(backoff.current).toBeCloseTo(1999, 0);
        vi.restoreAllMocks();
    });

    it("should reset to initial state", () => {
        vi.spyOn(Math, "random").mockReturnValue(0);
        const backoff = new ExponentialBackoffWithJitter(1000, 30000);

        backoff.next();
        backoff.next();
        backoff.next();
        expect(backoff.retries).toBe(3);
        expect(backoff.current).toBe(8000);

        backoff.reset();
        expect(backoff.retries).toBe(0);
        expect(backoff.current).toBe(1000);

        vi.restoreAllMocks();
    });
});
