// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useState, type Dispatch, type SetStateAction } from "react";

export type StreamState = "loading" | "playing" | "offline";

/** How long a stream may stay `loading` before the UI says it is taking longer than usual. */
const SLOW_LOAD_HINT_MS = 10_000;

/**
 * Lanes with nothing on our side to start (HLS, MPEG-TS): a stream silent this long isn't coming,
 * and an endless spinner is worse than saying so.
 */
export const DIRECT_LOAD_TIMEOUT_MS = 30_000;

/**
 * Converted lanes (RTSP, SRT): MediaMTX starts a conversion process on the first request and holds
 * that request open until it is ready or its own `runOnDemandStartTimeout` (90 s) runs out. This
 * must outlast that, so a slow cold start is waited out and only a path MediaMTX itself gave up
 * on is reported offline. `HlsPlayer` raises hls.js's manifest policy for these lanes so its own
 * (shorter) default give-up doesn't pre-empt this.
 */
export const CONVERTED_LOAD_TIMEOUT_MS = 100_000;

/**
 * Flips a player that is still `loading` to `offline` after `giveUpAfterMs`, and reports `slow`
 * once it has been loading past the hint threshold. The state resets by re-mounting the caller
 * (key it on the URL), not by this hook.
 */
export function useStreamLoadTimeout(
    url: string,
    streamState: StreamState,
    setStreamState: Dispatch<SetStateAction<StreamState>>,
    giveUpAfterMs: number,
): { slow: boolean } {
    const [slow, setSlow] = useState(false);

    useEffect(() => {
        const slowId = setTimeout(() => setSlow(true), SLOW_LOAD_HINT_MS);
        const giveUpId = setTimeout(
            () => setStreamState((s) => (s === "loading" ? "offline" : s)),
            giveUpAfterMs,
        );
        return () => {
            clearTimeout(slowId);
            clearTimeout(giveUpId);
        };
    }, [url, giveUpAfterMs, setStreamState]);

    return { slow: slow && streamState === "loading" };
}
