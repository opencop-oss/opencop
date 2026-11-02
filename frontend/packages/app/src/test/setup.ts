// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Central Vitest setup file (wired via `vitest.config.ts` → `test.setupFiles`).
 * Runs once per test file before any test. Owns, in one place:
 *   - jest-dom matcher registration
 *   - a fake IndexedDB (the real `IngestProvider` persists to it)
 *   - the Chakra v3 DOM polyfills (previously copy-pasted per test file)
 *   - the MSW server lifecycle + the three-layer "no unmocked network" guarantee
 *   - a reset of module-local CSRF state between tests
 *   - a reset of the module-level `OCMPClient` singleton between tests
 *
 * No provider logic lives here — that is `index.tsx`.
 */
import "@testing-library/jest-dom/vitest";
import "fake-indexeddb/auto";
import { clearCsrfToken } from "@opencop/api";
import { afterAll, afterEach, beforeAll, beforeEach, vi } from "vitest";
import { server } from "./server";
import { drainUnhandledRequests, unhandledRequests } from "./no-network";
import { virtualizerMock } from "./react-virtual-mock";
import { websocketMock } from "./websocket-mock";

// ---------------------------------------------------------------------------
// Chakra UI v3 DOM polyfills — happy-dom/jsdom don't implement these fully.
// Registered once here; per-file copies were deleted during the harness migration.
// ---------------------------------------------------------------------------
class ResizeObserverMock {
    observe() {}
    unobserve() {}
    disconnect() {}
}
class IntersectionObserverMock {
    observe() {}
    unobserve() {}
    disconnect() {}
    takeRecords() {
        return [];
    }
    readonly root = null;
    readonly rootMargin = "";
    readonly thresholds = [];
}

globalThis.ResizeObserver = ResizeObserverMock as typeof ResizeObserver;
globalThis.IntersectionObserver =
    IntersectionObserverMock as unknown as typeof IntersectionObserver;

if (!globalThis.matchMedia) {
    globalThis.matchMedia = (query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
    });
}

Element.prototype.scrollTo ??= () => {};
Element.prototype.scrollIntoView ??= () => {};
globalThis.requestAnimationFrame ??= (cb: FrameRequestCallback) =>
    setTimeout(() => cb(performance.now()), 0) as unknown as number;

if (!("createObjectURL" in URL)) {
    (URL as unknown as { createObjectURL: () => string }).createObjectURL = () => "blob:mock";
}
if (!navigator.clipboard) {
    Object.defineProperty(navigator, "clipboard", {
        value: { writeText: vi.fn(async () => {}) },
        configurable: true,
    });
}

// ---------------------------------------------------------------------------
// No unmocked network: HTTP calls need MSW handlers, unhandled requests fail in
// teardown, and WebSocket or sendBeacon calls throw.
// ---------------------------------------------------------------------------

// Layer 2 lives in ./no-network (imported above) so the harness self-test can
// read the tally. It catches rejections the app swallows (React Query
// `retry:false`, try/catch, fire-and-forget `.catch()`): MSW's per-request
// `error` rejects the *request*, but a swallowed rejection can still leave the
// *test* green; this tally fails in teardown regardless, naming every leaked URL.

// Layer 3: MSW's HTTP tally can't see sockets. The OCMPClient stub already
// prevents real sockets, but hard-stub the constructor as a belt so nothing
// opens one even if the stub is bypassed.
class ThrowingWebSocket {
    constructor(url: string | URL) {
        throw new Error(`Unmocked WebSocket to ${String(url)} during test`);
    }
}
if ("sendBeacon" in navigator) {
    navigator.sendBeacon = () => {
        throw new Error("Unmocked navigator.sendBeacon during test");
    };
}

beforeAll(() => {
    // Layer 1: per-request loud failure — `print.error()` names method + URL.
    server.listen({
        onUnhandledRequest(request, print) {
            unhandledRequests.push(`${request.method} ${request.url}`);
            print.error();
        },
    });
    // Install the throw-guard *after* listen(): MSW re-patches `globalThis.WebSocket`
    // with its own interceptor on listen (a read-only property), so assign via
    // defineProperty and after listen() so ours wins.
    Object.defineProperty(globalThis, "WebSocket", {
        value: ThrowingWebSocket,
        configurable: true,
        writable: true,
    });
});

beforeEach(() => {
    clearCsrfToken();
    unhandledRequests.length = 0;
    // The virtualizer stub (aliased in vitest.config.ts) carries module-level state:
    // the window size a test may have raised, and the observed item counts.
    virtualizerMock.reset();
    websocketMock.reset();
});

afterEach(() => {
    server.resetHandlers();
    // The OCMPClient singleton self-cleans: RTL's auto-cleanup unmounts each
    // render, which releases the ref-counted client (clientEntry → null). We
    // deliberately do NOT import the hook here — statically pre-loading it from
    // this setup file would defeat the local `vi.mock("@opencop/websocket")` in
    // use-ocmp-client.hook.test.ts.
    const leaked = drainUnhandledRequests();
    if (leaked.length > 0) {
        throw new Error(
            `Unmocked network request(s) during test — add an MSW handler or fix the call:\n` +
                leaked.map((r) => `  • ${r}`).join("\n"),
        );
    }
});

afterAll(() => {
    server.close();
});
