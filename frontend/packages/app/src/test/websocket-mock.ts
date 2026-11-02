// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Test stub for `@opencop/websocket`, wired in via `vitest.config.ts`
 * `resolve.alias` (mirrors the existing `maplibre-gl` → `maplibre-gl-mock.ts`
 * pattern). The real `OCMPClient` opens a live socket in its constructor — a
 * non-HTTP system boundary. Stubbing it here lets the harness keep
 * `WebSocketProvider` in the real chain (many components call `useWebSocket()`)
 * without any socket, timer, or singleton leaking into tests.
 *
 * The alias is global. Any test in `packages/websocket` must import
 * `./OCMPClient` relatively (not via the `@opencop/websocket` barrel) to reach
 * the real class rather than this stub. A test that needs to drive real client
 * behaviour re-mocks `@opencop/websocket` locally with `vi.mock` (which takes
 * precedence over the alias).
 */

import type { OCMPSubscribeOptions } from "@opencop/websocket";

export const MAX_RECONNECT_ATTEMPTS = 10;
export const SESSION_ENDED = 4401;

export function isTerminalClose(code: number): boolean {
    return code === 1000 || code === SESSION_ENDED;
}

export interface OCMPClientOptions {
    onOpen?: () => void;
    onClose?: (event: CloseEvent) => void;
    onError?: (event: Event) => void;
    onRetry?: (attempt: number) => void;
    onReconnect?: () => void;
    onMaxRetries?: () => void;
}

let activeOptions: OCMPClientOptions | undefined;

type SubscriptionHandler = (payload: Uint8Array, headers?: Record<string, string>) => unknown;

/** Drives lifecycle callbacks at the mocked socket boundary in integration tests. */
export const websocketMock = {
    reset(): void {
        activeOptions = undefined;
    },
    close(code: number, reason = ""): CloseEvent {
        const event = new CloseEvent("close", { code, reason });
        activeOptions?.onClose?.(event);
        return event;
    },
};

/** No-op client — no socket, no timers, no reconnection state machine. */
export class OCMPClient {
    constructor(_url: string, options?: OCMPClientOptions) {
        activeOptions = options;
    }
    subscribe(
        _destination: string,
        _handler: SubscriptionHandler,
        _options?: OCMPSubscribeOptions,
    ): () => void {
        return () => {};
    }
    unsubscribe(_destination: string, _handler: SubscriptionHandler): void {}
    send(): void {}
    disconnect(): void {}
    isOpen(): boolean {
        return true;
    }
    isClosed(): boolean {
        return false;
    }
    isConnecting(): boolean {
        return false;
    }
}

/** Pure backoff helper — safe to stub with an inert shape. */
export class ExponentialBackoffWithJitter {
    constructor(_initialDelay?: number, _maxDelay?: number) {}
    next(): number {
        return 0;
    }
}
