// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { WebsocketTsTransportBuilder } from "./WebsocketTsTransport";
import type { Backoff, WebsocketTransport } from "./WebsocketTransport";

/**
 * Conformance tests for the websocket-ts adapter.
 *
 * Everything here is a claim about how websocket-ts behaves — reconnect ordering, retry
 * scheduling, whether `close()` is final. `OCMPClient` is written against the
 * {@link WebsocketTransport} contract alone and must not restate any of it; this file is where
 * those assumptions live, so a dependency upgrade fails here rather than somewhere confusing.
 */

// websocket-ts constructs the native socket internally, so the browser boundary is faked here.
class FakeNativeWebSocket extends EventTarget {
    static readonly CONNECTING = 0;
    static readonly OPEN = 1;
    static readonly CLOSING = 2;
    static readonly CLOSED = 3;
    static instances: FakeNativeWebSocket[] = [];

    readonly CONNECTING = FakeNativeWebSocket.CONNECTING;
    readonly OPEN = FakeNativeWebSocket.OPEN;
    readonly CLOSING = FakeNativeWebSocket.CLOSING;
    readonly CLOSED = FakeNativeWebSocket.CLOSED;
    readonly url: string;
    readonly protocol = "";
    readonly extensions = "";
    binaryType: BinaryType = "blob";
    bufferedAmount = 0;
    readyState = FakeNativeWebSocket.CONNECTING;
    closeCalls = 0;
    readonly sent: Array<string | ArrayBufferLike | Blob | ArrayBufferView> = [];

    constructor(url: string | URL) {
        super();
        this.url = String(url);
        FakeNativeWebSocket.instances.push(this);
    }

    open(): void {
        this.readyState = FakeNativeWebSocket.OPEN;
        this.dispatchEvent(new Event("open"));
    }

    serverClose(code: number, reason = ""): CloseEvent {
        this.readyState = FakeNativeWebSocket.CLOSED;
        const event = new CloseEvent("close", { code, reason });
        this.dispatchEvent(event);
        return event;
    }

    serverMessage(data: Uint8Array): MessageEvent {
        const event = new MessageEvent("message", {
            data: new Blob([new Uint8Array(data).buffer]),
        });
        this.dispatchEvent(event);
        return event;
    }

    send(data: string | ArrayBufferLike | Blob | ArrayBufferView): void {
        this.sent.push(data);
    }

    close(code = 1000, reason = ""): void {
        this.closeCalls++;
        if (this.readyState === FakeNativeWebSocket.CLOSED) return;
        this.readyState = FakeNativeWebSocket.CLOSED;
        this.dispatchEvent(new CloseEvent("close", { code, reason }));
    }
}

const RETRY_DELAY = 1_000;

function fixedBackoff(delay = RETRY_DELAY): Backoff {
    let retries = 0;
    return {
        get retries() {
            return retries;
        },
        get current() {
            return delay;
        },
        next() {
            retries++;
            return delay;
        },
        reset() {
            retries = 0;
        },
    };
}

function latestSocket(): FakeNativeWebSocket {
    return FakeNativeWebSocket.instances.at(-1)!;
}

describe("WebsocketTsTransport", () => {
    beforeEach(() => {
        vi.useFakeTimers();
        FakeNativeWebSocket.instances = [];
        vi.stubGlobal("WebSocket", FakeNativeWebSocket as unknown as typeof WebSocket);
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
    });

    describe("connection state", () => {
        it("reports connecting, then open, then closed", () => {
            const transport = new WebsocketTsTransportBuilder("ws://localhost/socket").build();
            const socket = latestSocket();

            expect([transport.isConnecting(), transport.isOpen(), transport.isClosed()]).toEqual([
                true,
                false,
                false,
            ]);

            socket.open();
            expect([transport.isConnecting(), transport.isOpen(), transport.isClosed()]).toEqual([
                false,
                true,
                false,
            ]);

            socket.serverClose(1006, "Abnormal closure");
            expect([transport.isConnecting(), transport.isOpen(), transport.isClosed()]).toEqual([
                false,
                false,
                true,
            ]);
        });
    });

    describe("lifecycle listeners", () => {
        function buildRecording(): { transport: WebsocketTransport; events: string[] } {
            const events: string[] = [];
            const transport = new WebsocketTsTransportBuilder("ws://localhost/socket")
                .withBackoff(fixedBackoff())
                .onOpen(() => events.push("open"))
                .onReconnect(() => events.push("reconnect"))
                .onRetry(() => events.push("retry"))
                .onClose(() => events.push("close"))
                .build();
            return { transport, events };
        }

        it("fires onOpen for the first connection and for every replacement", () => {
            const { events } = buildRecording();

            latestSocket().open();
            expect(events).toEqual(["open"]);

            latestSocket().serverClose(1006, "Abnormal closure");
            vi.advanceTimersByTime(RETRY_DELAY);
            latestSocket().open();

            expect(events.filter((event) => event === "open")).toHaveLength(2);
        });

        it("fires onReconnect only for a replacement, immediately before onOpen", () => {
            const { events } = buildRecording();

            latestSocket().open();
            expect(events).toEqual(["open"]);

            latestSocket().serverClose(1006, "Abnormal closure");
            vi.advanceTimersByTime(RETRY_DELAY);
            latestSocket().open();

            expect(events).toEqual(["open", "close", "retry", "reconnect", "open"]);
        });

        it("reports the socket as open while onOpen and onReconnect run", () => {
            const openStates: boolean[] = [];
            const transport: WebsocketTransport = new WebsocketTsTransportBuilder(
                "ws://localhost/socket",
            )
                .withBackoff(fixedBackoff())
                .onReconnect(() => openStates.push(transport.isOpen()))
                .onOpen(() => openStates.push(transport.isOpen()))
                .build();

            latestSocket().open();
            latestSocket().serverClose(1006, "Abnormal closure");
            vi.advanceTimersByTime(RETRY_DELAY);
            latestSocket().open();

            expect(openStates).toEqual([true, true, true]);
        });

        it("schedules each retry on the backoff", () => {
            const { events } = buildRecording();
            latestSocket().open();
            latestSocket().serverClose(1006, "Abnormal closure");

            vi.advanceTimersByTime(RETRY_DELAY - 1);
            expect(FakeNativeWebSocket.instances).toHaveLength(1);

            vi.advanceTimersByTime(1);
            expect(FakeNativeWebSocket.instances).toHaveLength(2);
            expect(events).toContain("retry");
        });

        it("forwards the close event and the message payload", async () => {
            const closeEvents: CloseEvent[] = [];
            const messages: unknown[] = [];
            new WebsocketTsTransportBuilder("ws://localhost/socket")
                .onClose((event) => closeEvents.push(event))
                .onMessage((event) => messages.push(event.data))
                .build();

            const socket = latestSocket();
            socket.open();
            socket.serverMessage(new Uint8Array([1, 2, 3]));
            socket.serverClose(4401, "Session ended");

            expect(messages).toHaveLength(1);
            expect(new Uint8Array(await (messages[0] as Blob).arrayBuffer())).toEqual(
                new Uint8Array([1, 2, 3]),
            );
            expect(closeEvents.map(({ code, reason }) => ({ code, reason }))).toEqual([
                { code: 4401, reason: "Session ended" },
            ]);
        });
    });

    describe("close is final", () => {
        it("attempts no reconnection after close()", () => {
            const transport = new WebsocketTsTransportBuilder("ws://localhost/socket")
                .withBackoff(fixedBackoff())
                .build();
            latestSocket().open();

            transport.close();
            vi.advanceTimersByTime(31_000);

            expect(FakeNativeWebSocket.instances).toHaveLength(1);
            expect(transport.isClosed()).toBe(true);
        });

        it("cancels a retry already scheduled by an earlier close", () => {
            const transport = new WebsocketTsTransportBuilder("ws://localhost/socket")
                .withBackoff(fixedBackoff())
                .build();
            latestSocket().open();

            latestSocket().serverClose(1006, "Abnormal closure");
            transport.close();
            vi.advanceTimersByTime(31_000);

            expect(FakeNativeWebSocket.instances).toHaveLength(1);
        });

        it("reconnects on a close the consumer did not initiate", () => {
            new WebsocketTsTransportBuilder("ws://localhost/socket")
                .withBackoff(fixedBackoff())
                .build();
            latestSocket().open();

            latestSocket().serverClose(1006, "Abnormal closure");
            vi.advanceTimersByTime(RETRY_DELAY);

            expect(FakeNativeWebSocket.instances).toHaveLength(2);
        });
    });

    describe("send never buffers", () => {
        it("writes to the socket while open", () => {
            const transport = new WebsocketTsTransportBuilder("ws://localhost/socket").build();
            const socket = latestSocket();
            socket.open();

            transport.send(new Uint8Array([1]));
            transport.send(new Uint8Array([2]));

            expect(socket.sent).toEqual([new Uint8Array([1]), new Uint8Array([2])]);
        });

        it("drops data sent while disconnected instead of replaying it on the next socket", () => {
            const transport = new WebsocketTsTransportBuilder("ws://localhost/socket")
                .withBackoff(fixedBackoff())
                .build();
            latestSocket().open();
            latestSocket().serverClose(1006, "Abnormal closure");

            transport.send(new Uint8Array([1]));
            vi.advanceTimersByTime(RETRY_DELAY);
            const replacement = latestSocket();
            replacement.open();

            expect(replacement.sent).toEqual([]);
        });
    });
});
