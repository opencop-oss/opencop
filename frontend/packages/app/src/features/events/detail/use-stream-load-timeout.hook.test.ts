// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, renderHook } from "~/test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useState } from "react";
import {
    CONVERTED_LOAD_TIMEOUT_MS,
    DIRECT_LOAD_TIMEOUT_MS,
    useStreamLoadTimeout,
    type StreamState,
} from "./use-stream-load-timeout.hook";

function setup(giveUpAfterMs: number, initial: StreamState = "loading") {
    return renderHook(() => {
        const [streamState, setStreamState] = useState<StreamState>(initial);
        const { slow } = useStreamLoadTimeout(
            "https://cam.example.com/x",
            streamState,
            setStreamState,
            giveUpAfterMs,
        );
        return { streamState, setStreamState, slow };
    });
}

const advance = (ms: number) =>
    act(() => {
        vi.advanceTimersByTime(ms);
    });

describe("useStreamLoadTimeout", () => {
    beforeEach(() => {
        vi.useFakeTimers();
    });
    afterEach(() => {
        vi.useRealTimers();
    });

    it("reports slow after 10 s of loading without giving up", () => {
        const { result } = setup(DIRECT_LOAD_TIMEOUT_MS);

        advance(9_999);
        expect(result.current.slow).toBe(false);

        advance(1);
        expect(result.current.slow).toBe(true);
        expect(result.current.streamState).toBe("loading");
    });

    it("goes offline once a direct stream has been silent for 30 s", () => {
        const { result } = setup(DIRECT_LOAD_TIMEOUT_MS);

        advance(DIRECT_LOAD_TIMEOUT_MS - 1);
        expect(result.current.streamState).toBe("loading");

        advance(1);
        expect(result.current.streamState).toBe("offline");
    });

    it("waits out MediaMTX's 90 s start window on a converted stream before going offline", () => {
        const { result } = setup(CONVERTED_LOAD_TIMEOUT_MS);

        advance(90_000);
        expect(result.current.streamState).toBe("loading");
        expect(result.current.slow).toBe(true);

        advance(CONVERTED_LOAD_TIMEOUT_MS - 90_000);
        expect(result.current.streamState).toBe("offline");
    });

    it("outlasts the 90 s MediaMTX start timeout so the server gives up first", () => {
        expect(CONVERTED_LOAD_TIMEOUT_MS).toBeGreaterThan(90_000);
    });

    it("leaves a stream that is already playing alone", () => {
        const { result } = setup(DIRECT_LOAD_TIMEOUT_MS, "playing");

        advance(DIRECT_LOAD_TIMEOUT_MS);

        expect(result.current.streamState).toBe("playing");
        expect(result.current.slow).toBe(false);
    });

    it("stops reporting slow as soon as the stream plays", () => {
        const { result } = setup(DIRECT_LOAD_TIMEOUT_MS);
        advance(10_000);
        expect(result.current.slow).toBe(true);

        act(() => result.current.setStreamState("playing"));

        expect(result.current.slow).toBe(false);
    });
});
