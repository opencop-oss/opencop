// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook } from "~/test";
import type { EnablementsState } from "./plugin-loader";
import { PLUGIN_LOAD_WAIT_MS, usePluginLoadSettled } from "./use-plugin-load-settled.hook";

let state: EnablementsState = { status: "pending" };

vi.mock("./plugin-records", async (importOriginal) => ({
    ...(await importOriginal<typeof import("./plugin-records")>()),
    usePluginEnablementsState: () => state,
}));

beforeEach(() => {
    vi.useFakeTimers();
    state = { status: "pending" };
});

afterEach(() => {
    vi.useRealTimers();
});

describe("usePluginLoadSettled", () => {
    it.each<EnablementsState>([
        { status: "loaded", pluginCount: 1 },
        { status: "absent" },
        { status: "failed", reason: "http", detail: "500" },
    ])("is settled once the loader reports $status", (settledState) => {
        const { result, rerender } = renderHook(() => usePluginLoadSettled());
        expect(result.current).toBe(false);

        state = settledState;
        rerender();

        expect(result.current).toBe(true);
    });

    it.each<EnablementsState>([
        { status: "pending" },
        { status: "retrying", attempt: 1, detail: "network" },
    ])("waits while the loader is $status, then stops waiting", (unsettled) => {
        state = unsettled;
        const { result } = renderHook(() => usePluginLoadSettled());

        act(() => vi.advanceTimersByTime(PLUGIN_LOAD_WAIT_MS - 1));
        expect(result.current).toBe(false);

        act(() => vi.advanceTimersByTime(1));
        expect(result.current).toBe(true);
    });
});
