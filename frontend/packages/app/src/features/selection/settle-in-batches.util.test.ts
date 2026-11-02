// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { settleInBatches } from "./settle-in-batches.util";

describe("settleInBatches", () => {
    it("returns one outcome per item in input order, including failures", async () => {
        const outcomes = await settleInBatches([1, 2, 3], 2, async (n) => {
            if (n === 2) throw new Error("boom");
            return n * 10;
        });

        expect(outcomes.map((o) => o.status)).toEqual(["fulfilled", "rejected", "fulfilled"]);
        expect(outcomes[0]).toEqual({ status: "fulfilled", value: 10 });
        expect(outcomes[2]).toEqual({ status: "fulfilled", value: 30 });
    });

    it("never has more than the batch size in flight", async () => {
        let inFlight = 0;
        let peak = 0;
        await settleInBatches([1, 2, 3, 4, 5], 2, async () => {
            inFlight++;
            peak = Math.max(peak, inFlight);
            await Promise.resolve();
            inFlight--;
        });

        expect(peak).toBe(2);
    });

    it("records a synchronous throw from the callback as a failed outcome for that item", async () => {
        const outcomes = await settleInBatches([1, 2], 2, (n) => {
            if (n === 1) throw new Error("sync boom");
            return Promise.resolve(n);
        });

        expect(outcomes.map((o) => o.status)).toEqual(["rejected", "fulfilled"]);
    });

    it("resolves to an empty list for no items", async () => {
        await expect(settleInBatches([], 3, async () => 1)).resolves.toEqual([]);
    });
});
