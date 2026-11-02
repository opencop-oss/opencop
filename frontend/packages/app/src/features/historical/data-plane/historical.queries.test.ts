// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { ApiError } from "@opencop/api";
import { describe, expect, it } from "vitest";
import { HISTOGRAM_MAX_ATTEMPTS, historicalQueries } from "./historical.queries";

const PARAMS = { from: "2026-01-01T00:00:00.000Z", to: "2026-01-02T00:00:00.000Z", bars: 24 };

/**
 * The declared retry policy exercised as a pure function, so the four-attempt
 * budget and the 401/403/404 exclusion are asserted directly against exact
 * failure counts and error shapes — no query execution, no elapsed time.
 */
function retryPolicy(): (failureCount: number, error: Error) => boolean {
    const { retry } = historicalQueries.histogram("source-1", PARAMS, true);
    if (typeof retry !== "function") {
        throw new Error("historicalQueries.histogram(...).retry is expected to be a function");
    }
    return retry;
}

describe("historicalQueries.histogram retry policy", () => {
    it("declares a 4-attempt budget (1 initial + 3 retries)", () => {
        expect(HISTOGRAM_MAX_ATTEMPTS).toBe(4);
    });

    it.each([401, 403, 404])(
        "never retries a %i — the session/permission/not-found trio is terminal on the first failure",
        (status) => {
            expect(retryPolicy()(0, new ApiError(status, {}))).toBe(false);
        },
    );

    it("retries a server error up to the declared budget, then stops", () => {
        const retry = retryPolicy();
        const error = new ApiError(500, { message: "Something went wrong." });

        expect(retry(0, error)).toBe(true);
        expect(retry(1, error)).toBe(true);
        expect(retry(2, error)).toBe(true);
        expect(retry(3, error)).toBe(false);
    });

    it("retries a non-ApiError failure (network error or timeout abort) up to the same budget", () => {
        const retry = retryPolicy();
        const error = new TypeError("Failed to fetch");

        expect(retry(0, error)).toBe(true);
        expect(retry(1, error)).toBe(true);
        expect(retry(2, error)).toBe(true);
        expect(retry(3, error)).toBe(false);
    });
});
