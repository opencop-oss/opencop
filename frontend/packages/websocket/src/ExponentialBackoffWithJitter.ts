// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Backoff } from "./WebsocketTransport";

/**
 * Custom exponential backoff with jitter to prevent thundering herd.
 * Implements the Backoff interface from websocket-ts.
 */
export class ExponentialBackoffWithJitter implements Backoff {
    private _retries = 0;
    private readonly baseMs: number;
    private readonly maxMs: number;

    constructor(baseMs = 1000, maxMs = 30000) {
        this.baseMs = baseMs;
        this.maxMs = maxMs;
    }

    get retries(): number {
        return this._retries;
    }

    get current(): number {
        const exponential = Math.min(this.baseMs * Math.pow(2, this._retries), this.maxMs);
        const jitter = Math.random() * 1000; // 0-1s random jitter
        return exponential + jitter;
    }

    next(): number {
        const duration = this.current;
        this._retries++;
        return duration;
    }

    reset(): void {
        this._retries = 0;
    }
}
