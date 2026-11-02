// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Harness self-test — proves the "no unmocked network" guarantee. These are not
 * feature tests; they assert that the setup file's belts actually hold.
 */
import { server, http, HttpResponse, API_BASE } from "~/test";
import { describe, expect, it } from "vitest";
import { unhandledRequests, drainUnhandledRequests } from "~/test/no-network";

describe("no-network guarantee", () => {
    it("layer 2: a swallowed fetch to an unhandled endpoint is still tallied", async () => {
        // Fire-and-forget a request no handler matches, and swallow the rejection —
        // exactly the shape (`.catch()`) that would otherwise leave a test green.
        await fetch(`${API_BASE}/does-not-exist`).catch(() => {});

        // The tally caught it even though the caller swallowed the error. Drain it
        // here so the global afterEach assertion doesn't (correctly) fail this test.
        const leaked = drainUnhandledRequests();
        expect(leaked.some((r) => r.includes("/does-not-exist"))).toBe(true);
        expect(unhandledRequests).toHaveLength(0);
    });

    it("layer 3: constructing a raw WebSocket throws", () => {
        expect(() => new WebSocket("ws://localhost:8083/socket")).toThrow(/Unmocked WebSocket/);
    });

    it("a handled request does not tally", async () => {
        server.use(http.get(`${API_BASE}/handled`, () => HttpResponse.json({ ok: true })));
        const res = await fetch(`${API_BASE}/handled`);
        expect(await res.json()).toEqual({ ok: true });
        expect(unhandledRequests).toHaveLength(0);
    });
});
