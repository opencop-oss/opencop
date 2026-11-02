// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { WebSocketSource } from "./WebSocketSource";
import { ElementMessage, SocketMessage } from "@opencop/proto";
import type { OCMPClient } from "@opencop/websocket";
import type { Element } from "@opencop/elements";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Builds a minimal ElementMessage for a symbol element. */
function buildElementMessage(overrides: Partial<ElementMessage> = {}): ElementMessage {
    const id = new Uint8Array(16);
    const source = new Uint8Array(16);

    return ElementMessage.create({
        type: "symbol",
        id,
        objectId: "obj-1",
        source,
        displayName: "Test Symbol",
        positions: [38.0, -77.0],
        stale: 0,
        start: Date.now(),
        time: Date.now(),
        values: {
            symbol: { string: "SFGPUCI----D***" },
            size: { double: 20 },
        },
        ...overrides,
    });
}

/** Encodes element messages into a MultiPayload Uint8Array. */
function encodeMultiPayload(messages: ElementMessage[]): Uint8Array {
    const payloads = messages.map((m) => ElementMessage.encode(m).finish());
    return SocketMessage.MultiPayload.encode({ payload: payloads }).finish();
}

type SubscribeHandler = (payload: Uint8Array) => unknown;

function fakeClient(): OCMPClient & { simulateMessage(bytes: Uint8Array): void } {
    const handlers = new Map<string, SubscribeHandler>();

    return {
        subscribe: vi.fn((dest: string, handler: SubscribeHandler) => {
            handlers.set(dest, handler);
        }),
        unsubscribe: vi.fn(),
        simulateMessage(bytes: Uint8Array) {
            for (const handler of handlers.values()) handler(bytes);
        },
    } as unknown as OCMPClient & { simulateMessage(bytes: Uint8Array): void };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("WebSocketSource", () => {
    describe("decoding", () => {
        it("decodes a single element from a MultiPayload", () => {
            const client = fakeClient();
            const source = new WebSocketSource("ws-1", client, "/topic/test");
            const emitted: Element[][] = [];
            source.bind((elements) => emitted.push(elements));
            source.enable();

            const bytes = encodeMultiPayload([buildElementMessage()]);
            client.simulateMessage(bytes);

            expect(emitted).toHaveLength(1);
            expect(emitted[0]).toHaveLength(1);
            expect(emitted[0][0].getDisplayName()).toBe("Test Symbol");
        });

        it("decodes multiple elements from a MultiPayload", () => {
            const client = fakeClient();
            const source = new WebSocketSource("ws-1", client, "/topic/test");
            const emitted: Element[][] = [];
            source.bind((elements) => emitted.push(elements));
            source.enable();

            const bytes = encodeMultiPayload([
                buildElementMessage({ displayName: "Alpha" }),
                buildElementMessage({ displayName: "Bravo" }),
                buildElementMessage({ displayName: "Charlie" }),
            ]);
            client.simulateMessage(bytes);

            expect(emitted).toHaveLength(1);
            expect(emitted[0]).toHaveLength(3);
            expect(emitted[0].map((e) => e.getDisplayName())).toEqual([
                "Alpha",
                "Bravo",
                "Charlie",
            ]);
        });

        it("skips elements with unknown type and only emits decoded ones", () => {
            const client = fakeClient();
            const source = new WebSocketSource("ws-1", client, "/topic/test");
            const emitted: Element[][] = [];
            source.bind((elements) => emitted.push(elements));
            source.enable();

            const bytes = encodeMultiPayload([
                buildElementMessage({ type: "unknown_type_xyz" }),
                buildElementMessage({ displayName: "Valid" }),
            ]);
            client.simulateMessage(bytes);

            expect(emitted).toHaveLength(1);
            expect(emitted[0]).toHaveLength(1);
            expect(emitted[0][0].getDisplayName()).toBe("Valid");
        });

        it("does not emit when all elements have unknown type", () => {
            const client = fakeClient();
            const source = new WebSocketSource("ws-1", client, "/topic/test");
            const emitted: Element[][] = [];
            source.bind((elements) => emitted.push(elements));
            source.enable();

            const bytes = encodeMultiPayload([
                buildElementMessage({ type: "bogus_a" }),
                buildElementMessage({ type: "bogus_b" }),
            ]);
            client.simulateMessage(bytes);

            expect(emitted).toHaveLength(0);
        });

        it("does not emit when MultiPayload has no payloads", () => {
            const client = fakeClient();
            const source = new WebSocketSource("ws-1", client, "/topic/test");
            const emitted: Element[][] = [];
            source.bind((elements) => emitted.push(elements));
            source.enable();

            const bytes = SocketMessage.MultiPayload.encode({ payload: [] }).finish();
            client.simulateMessage(bytes);

            expect(emitted).toHaveLength(0);
        });
    });

    describe("lifecycle", () => {
        it("subscribes to the correct destination on enable", () => {
            const client = fakeClient();
            const source = new WebSocketSource("ws-1", client, "/topic/test");
            source.enable();

            expect(client.subscribe).toHaveBeenCalledWith("/topic/test", expect.any(Function));
        });

        it("unsubscribes on disable", () => {
            const client = fakeClient();
            const source = new WebSocketSource("ws-1", client, "/topic/test");
            source.enable();
            source.disable();

            expect(client.unsubscribe).toHaveBeenCalledWith("/topic/test", expect.any(Function));
        });
    });
});
