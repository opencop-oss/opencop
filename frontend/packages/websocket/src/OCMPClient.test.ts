// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { SocketMessage } from "@opencop/proto";
import Logger from "js-logger";
import { isTerminalClose, OCMPClient, SESSION_ENDED } from "./OCMPClient";
import type { WebsocketTransport, WebsocketTransportBuilder } from "./WebsocketTransport";

/**
 * A transport that does exactly what {@link WebsocketTransport} promises and nothing more.
 *
 * It never queues, never reconnects, and never decides an ordering — those belong to the real
 * transport, and the adapter's conformance to them is covered by `WebsocketTsTransport.test.ts`.
 * Tests here drive the client by emitting the events a connection would.
 */
class MockWebsocketTransport implements WebsocketTransport {
    private opened = false;
    private closed = false;
    private connecting = true;

    /** Frames that reached the wire, in order. */
    readonly sent: Array<string | ArrayBufferLike | Blob | ArrayBufferView> = [];
    closeCalls = 0;

    private readonly listeners: MockWebsocketTransportBuilder;

    constructor(listeners: MockWebsocketTransportBuilder) {
        this.listeners = listeners;
    }

    send(data: string | ArrayBufferLike | Blob | ArrayBufferView): void {
        if (!this.opened) return;
        this.sent.push(data);
    }

    close(): void {
        this.closeCalls++;
        this.connecting = false;
        this.opened = false;
        this.closed = true;
    }

    isOpen(): boolean {
        return this.opened;
    }

    isClosed(): boolean {
        return this.closed;
    }

    isConnecting(): boolean {
        return this.connecting;
    }

    emitOpen(): void {
        this.connecting = false;
        this.closed = false;
        this.opened = true;
        this.listeners.openListeners.forEach((listener) => listener());
    }

    emitReconnect(): void {
        this.listeners.reconnectListeners.forEach((listener) => listener());
    }

    emitClose(code: number): CloseEvent {
        this.connecting = false;
        this.opened = false;
        this.closed = true;
        const event = new CloseEvent("close", { code });
        this.listeners.closeListeners.forEach((listener) => listener(event));
        return event;
    }

    emitMessage(message: SocketMessage.$Properties): void {
        this.emitRawMessage(SocketMessage.encode(SocketMessage.create(message)).finish());
    }

    emitRawMessage(data: Uint8Array): void {
        const event = new MessageEvent("message", {
            data: new Blob([new Uint8Array(data).buffer]),
        });
        this.listeners.messageListeners.forEach((listener) => listener(event));
    }

    /** Forgets earlier frames, so an assertion can speak about one connection. */
    clearSent(): void {
        this.sent.length = 0;
    }

    decodeSentFrames(): SocketMessage[] {
        return this.sent.map((data) => SocketMessage.decode(data as Uint8Array));
    }
}

class MockWebsocketTransportBuilder implements WebsocketTransportBuilder {
    static instance?: MockWebsocketTransport;

    readonly openListeners: (() => void)[] = [];
    readonly reconnectListeners: (() => void)[] = [];
    readonly closeListeners: ((event: CloseEvent) => void)[] = [];
    readonly messageListeners: ((event: MessageEvent) => void)[] = [];

    withBackoff(): this {
        return this;
    }
    onOpen(listener: () => void): this {
        this.openListeners.push(listener);
        return this;
    }
    onClose(listener: (event: CloseEvent) => void): this {
        this.closeListeners.push(listener);
        return this;
    }
    onError(): this {
        return this;
    }
    onRetry(): this {
        return this;
    }
    onReconnect(listener: () => void): this {
        this.reconnectListeners.push(listener);
        return this;
    }
    onMessage(listener: (event: MessageEvent) => void): this {
        this.messageListeners.push(listener);
        return this;
    }
    build(): WebsocketTransport {
        MockWebsocketTransportBuilder.instance = new MockWebsocketTransport(this);
        return MockWebsocketTransportBuilder.instance;
    }
}

/** Clients built during a test, so teardown can stop the heartbeat each one starts on open. */
const activeClients: OCMPClient[] = [];

afterEach(() => {
    activeClients.splice(0).forEach((client) => client.disconnect());
    vi.restoreAllMocks();
});

/** Builds a client on the mock transport and hands back both. */
function createClient(options: Omit<ConstructorParameters<typeof OCMPClient>[1], "builder"> = {}): {
    client: OCMPClient;
    ws: MockWebsocketTransport;
} {
    const client = new OCMPClient("ws://localhost/socket", {
        ...options,
        builder: MockWebsocketTransportBuilder,
    });
    activeClients.push(client);
    return { client, ws: MockWebsocketTransportBuilder.instance! };
}

function typedDestinations(frames: SocketMessage[]): Array<{ type: number; destination: string }> {
    return frames.map(({ type, destination }) => ({ type, destination }));
}

describe("isTerminalClose", () => {
    it.each([
        { code: 1000, expected: true },
        { code: SESSION_ENDED, expected: true },
        { code: 1006, expected: false },
        { code: 1011, expected: false },
        { code: 4403, expected: false },
    ])("returns $expected for close code $code", ({ code, expected }) => {
        expect(isTerminalClose(code)).toBe(expected);
    });
});

describe("OCMPClient subscription ownership", () => {
    it("shares one wire subscription among distinct local subscribers", async () => {
        const { client, ws } = createClient();
        ws.emitOpen();
        const firstHandler = vi.fn();
        const secondHandler = vi.fn();

        client.subscribe("/topic/events", firstHandler);
        client.subscribe("/topic/events", secondHandler);

        expect(ws.decodeSentFrames()).toMatchObject([
            {
                type: SocketMessage.Type.SUBSCRIBE,
                destination: "/topic/events",
            },
        ]);

        const payload = new Uint8Array([1, 2, 3]);
        ws.emitMessage({
            type: SocketMessage.Type.SEND,
            destination: "/topic/events",
            payload,
        });

        await vi.waitFor(() => {
            expect(firstHandler).toHaveBeenCalledOnce();
            expect(secondHandler).toHaveBeenCalledOnce();
        });
        expect(firstHandler).toHaveBeenCalledWith(payload, {});
        expect(secondHandler).toHaveBeenCalledWith(payload, {});
    });

    it("uses an ordered subscriber snapshot with shared decoded inputs", async () => {
        const { client, ws } = createClient();
        ws.emitOpen();
        const deliveryOrder: string[] = [];
        const firstInputs: Array<[Uint8Array, Record<string, string> | undefined]> = [];
        const removedInputs: Array<[Uint8Array, Record<string, string> | undefined]> = [];
        const addedHandler = vi.fn(() => deliveryOrder.push("added"));
        const removedHandler = vi.fn((payload: Uint8Array, headers?: Record<string, string>) => {
            removedInputs.push([payload, headers]);
            deliveryOrder.push("removed");
        });
        const firstHandler = vi.fn((payload: Uint8Array, headers?: Record<string, string>) => {
            firstInputs.push([payload, headers]);
            deliveryOrder.push("first");
            client.unsubscribe("/topic/events", removedHandler);
            client.subscribe("/topic/events", addedHandler);
        });
        client.subscribe("/topic/events", firstHandler);
        client.subscribe("/topic/events", removedHandler);

        ws.emitMessage({
            type: SocketMessage.Type.SEND,
            destination: "/topic/events",
            payload: new Uint8Array([1, 2, 3]),
            headers: { classification: "SECRET" },
        });

        await vi.waitFor(() => expect(removedHandler).toHaveBeenCalledOnce());
        expect(deliveryOrder).toEqual(["first", "removed"]);
        expect(addedHandler).not.toHaveBeenCalled();
        expect(firstInputs[0]?.[0]).toBe(removedInputs[0]?.[0]);
        expect(firstInputs[0]?.[1]).toBe(removedInputs[0]?.[1]);

        // A second frame is what shows the mutations landed rather than being discarded.
        ws.emitMessage({
            type: SocketMessage.Type.SEND,
            destination: "/topic/events",
            payload: new Uint8Array([4, 5, 6]),
        });

        await vi.waitFor(() => expect(addedHandler).toHaveBeenCalledOnce());
        expect(deliveryOrder).toEqual(["first", "removed", "first", "added"]);
        expect(removedHandler).toHaveBeenCalledOnce();
    });

    it("isolates a synchronous subscriber failure without logging payload-derived details", async () => {
        const { client, ws } = createClient();
        const failingHandler = vi.fn((payload: Readonly<Uint8Array>) => {
            throw new Error(new TextDecoder().decode(Uint8Array.from(payload)));
        });
        const laterHandler = vi.fn();
        const errorSpy = vi.spyOn(Logger.get("ws"), "error").mockImplementation(() => undefined);
        ws.emitOpen();
        client.subscribe("/topic/events", failingHandler);
        client.subscribe("/topic/events", laterHandler);

        ws.emitMessage({
            type: SocketMessage.Type.SEND,
            destination: "/topic/events",
            payload: new TextEncoder().encode("classified-payload"),
            headers: { classification: "SECRET" },
        });

        await vi.waitFor(() => expect(laterHandler).toHaveBeenCalledOnce());
        expect(errorSpy.mock.calls).toEqual([
            ["Subscriber failed for destination:", "/topic/events"],
        ]);
    });

    it("observes rejected subscriber promises without logging retained delivery data", async () => {
        const { client, ws } = createClient();
        const deliveryOrder: string[] = [];
        const errorSpy = vi.spyOn(Logger.get("ws"), "error").mockImplementation(() => undefined);
        ws.emitOpen();
        client.subscribe("/topic/events", () => {
            deliveryOrder.push("pending");
            return new Promise(() => undefined);
        });
        client.subscribe("/topic/events", (payload, headers) => {
            deliveryOrder.push("rejecting");
            return Promise.reject({ payload, headers });
        });
        client.subscribe("/topic/events", () => {
            deliveryOrder.push("later");
        });

        ws.emitMessage({
            type: SocketMessage.Type.SEND,
            destination: "/topic/events",
            payload: new TextEncoder().encode("classified-payload"),
            headers: { classification: "SECRET" },
        });

        await vi.waitFor(() => {
            expect(deliveryOrder).toEqual(["pending", "rejecting", "later"]);
            expect(errorSpy.mock.calls).toEqual([
                ["Subscriber failed for destination:", "/topic/events"],
            ]);
        });
    });

    it("observes a rejected subscriber thenable", async () => {
        const { client, ws } = createClient();
        const failure = new Error("thenable subscriber failed");
        const laterHandler = vi.fn();
        const errorSpy = vi.spyOn(Logger.get("ws"), "error").mockImplementation(() => undefined);
        ws.emitOpen();
        client.subscribe("/topic/events", () => ({
            then: (_resolve: unknown, reject: (reason: unknown) => void) => reject(failure),
        }));
        client.subscribe("/topic/events", laterHandler);

        ws.emitMessage({
            type: SocketMessage.Type.SEND,
            destination: "/topic/events",
            payload: new Uint8Array([1, 2, 3]),
        });

        await vi.waitFor(() => {
            expect(laterHandler).toHaveBeenCalledOnce();
            expect(errorSpy).toHaveBeenCalledWith(
                "Subscriber failed for destination:",
                "/topic/events",
            );
        });
    });

    it("discards an invalid protobuf frame and delivers a later frame on the same connection", async () => {
        const { client, ws } = createClient();
        const handler = vi.fn();
        const errorSpy = vi.spyOn(Logger.get("ws"), "error").mockImplementation(() => undefined);
        ws.emitOpen();
        client.subscribe("/topic/events", handler);

        ws.emitRawMessage(new Uint8Array([0x12, 0x05, 0x61]));
        ws.emitMessage({
            type: SocketMessage.Type.SEND,
            destination: "/topic/events",
            payload: new Uint8Array([1, 2, 3]),
        });

        await vi.waitFor(() => {
            expect(errorSpy).toHaveBeenCalledOnce();
            expect(handler).toHaveBeenCalledOnce();
        });
        expect(errorSpy).toHaveBeenCalledWith(
            "Failed to process inbound frame:",
            expect.any(Error),
        );
        expect(ws.decodeSentFrames()).toHaveLength(1);
    });

    it("discards invalid compressed payloads without logging frame contents", async () => {
        const { client, ws } = createClient();
        const handler = vi.fn();
        const errorSpy = vi.spyOn(Logger.get("ws"), "error").mockImplementation(() => undefined);
        ws.emitOpen();
        client.subscribe("/topic/events", handler);

        ws.emitMessage({
            type: SocketMessage.Type.SEND,
            destination: "/topic/events",
            payload: new Uint8Array([0xde, 0xad, 0xbe, 0xef]),
            headers: { classification: "SECRET" },
            compressed: true,
        });
        ws.emitMessage({
            type: SocketMessage.Type.SEND,
            destination: "/topic/events",
            payload: new Uint8Array([1, 2, 3]),
        });

        await vi.waitFor(() => {
            expect(errorSpy).toHaveBeenCalledOnce();
            expect(handler).toHaveBeenCalledOnce();
        });
        expect(errorSpy.mock.calls[0]).toEqual([
            "Failed to process inbound frame:",
            expect.anything(),
        ]);
        expect(errorSpy.mock.calls.flat().map(String).join(" ")).not.toContain("SECRET");
    });

    it("warns when a valid message has no local subscribers", async () => {
        const { ws } = createClient();
        const warnSpy = vi.spyOn(Logger.get("ws"), "warn").mockImplementation(() => undefined);
        ws.emitOpen();

        ws.emitMessage({
            type: SocketMessage.Type.SEND,
            destination: "/topic/without-subscribers",
            payload: new Uint8Array([1, 2, 3]),
        });

        await vi.waitFor(() => {
            expect(warnSpy).toHaveBeenCalledWith(
                "No handlers for destination:",
                "/topic/without-subscribers",
            );
        });
    });

    it("restores destinations before invoking the onOpen callback", () => {
        const onOpen = vi.fn(() => {
            expect(MockWebsocketTransportBuilder.instance!.decodeSentFrames()).toMatchObject([
                {
                    type: SocketMessage.Type.SUBSCRIBE,
                    destination: "/topic/events",
                },
            ]);
        });
        const { client, ws } = createClient({ onOpen });

        client.subscribe("/topic/events", vi.fn());

        expect(ws.sent).toHaveLength(0);

        ws.emitOpen();

        expect(onOpen).toHaveBeenCalledOnce();
        expect(ws.sent).toHaveLength(1);
    });

    it("does not restore a destination removed before the first open", () => {
        const { client, ws } = createClient();
        const handler = vi.fn();

        client.subscribe("/topic/events", handler);
        client.unsubscribe("/topic/events", handler);
        ws.emitOpen();

        expect(ws.sent).toHaveLength(0);
    });

    it("restores only currently registered destinations on a replacement connection", () => {
        const onReconnect = vi.fn();
        const { client, ws } = createClient({ onReconnect });
        ws.emitOpen();
        const removedHandler = vi.fn();
        client.subscribe("/topic/removed", removedHandler);
        client.subscribe("/topic/retained", vi.fn());

        ws.emitClose(1006);
        client.unsubscribe("/topic/removed", removedHandler);
        client.subscribe("/topic/added", vi.fn());
        ws.clearSent();

        ws.emitReconnect();
        ws.emitOpen();

        expect(onReconnect).toHaveBeenCalledOnce();
        expect(typedDestinations(ws.decodeSentFrames())).toEqual([
            {
                type: SocketMessage.Type.SUBSCRIBE,
                destination: "/topic/retained",
            },
            {
                type: SocketMessage.Type.SUBSCRIBE,
                destination: "/topic/added",
            },
        ]);
    });

    it("collapses disconnected subscription churn to final destination state", () => {
        const { client, ws } = createClient();
        ws.emitOpen();
        ws.emitClose(1006);
        const firstHandler = vi.fn();
        const finalHandler = vi.fn();
        const inactiveHandler = vi.fn();

        client.subscribe("/topic/active", firstHandler);
        client.unsubscribe("/topic/active", firstHandler);
        client.subscribe("/topic/active", finalHandler);
        client.subscribe("/topic/inactive", inactiveHandler);
        client.unsubscribe("/topic/inactive", inactiveHandler);

        expect(ws.sent).toHaveLength(0);

        ws.emitOpen();

        expect(typedDestinations(ws.decodeSentFrames())).toEqual([
            {
                type: SocketMessage.Type.SUBSCRIBE,
                destination: "/topic/active",
            },
        ]);
    });

    it("restores active destinations before replaying queued sends in FIFO order", () => {
        const { client, ws } = createClient();
        ws.emitOpen();
        ws.emitClose(1006);

        client.subscribe("/topic/events", vi.fn());
        client.send("/queue/first", new Uint8Array([1]));
        client.send("/queue/second", new Uint8Array([2]));

        ws.emitOpen();

        expect(
            ws.decodeSentFrames().map(({ type, destination, payload }) => ({
                type,
                destination,
                payload: Array.from(payload),
            })),
        ).toEqual([
            {
                type: SocketMessage.Type.SUBSCRIBE,
                destination: "/topic/events",
                payload: [],
            },
            {
                type: SocketMessage.Type.SEND,
                destination: "/queue/first",
                payload: [1],
            },
            {
                type: SocketMessage.Type.SEND,
                destination: "/queue/second",
                payload: [2],
            },
        ]);
    });

    it("replays a queued send exactly once across repeated opens", () => {
        const { client, ws } = createClient();
        client.send("/queue/first", new Uint8Array([1]));

        ws.emitOpen();
        ws.emitClose(1006);
        ws.emitOpen();

        expect(
            ws.decodeSentFrames().map(({ type, destination }) => ({ type, destination })),
        ).toEqual([
            {
                type: SocketMessage.Type.SEND,
                destination: "/queue/first",
            },
        ]);
    });

    it("restores normally when the first connection attempt failed", () => {
        const onOpen = vi.fn();
        const onReconnect = vi.fn();
        const { client, ws } = createClient({ onOpen, onReconnect });
        client.subscribe("/topic/events", vi.fn());

        ws.emitClose(1006);
        ws.emitOpen();

        expect(onReconnect).not.toHaveBeenCalled();
        expect(onOpen).toHaveBeenCalledOnce();
        expect(typedDestinations(ws.decodeSentFrames())).toEqual([
            {
                type: SocketMessage.Type.SUBSCRIBE,
                destination: "/topic/events",
            },
        ]);
    });

    it("treats duplicate registration of one handler as idempotent", async () => {
        const { client, ws } = createClient();
        ws.emitOpen();
        const handler = vi.fn();

        client.subscribe("/topic/events", handler);
        client.subscribe("/topic/events", handler);

        expect(ws.decodeSentFrames()).toHaveLength(1);

        ws.emitMessage({
            type: SocketMessage.Type.SEND,
            destination: "/topic/events",
            payload: new Uint8Array([1, 2, 3]),
        });

        await vi.waitFor(() => expect(handler).toHaveBeenCalledOnce());
    });

    it("removes one local subscriber without tearing down the active destination", async () => {
        const { client, ws } = createClient();
        ws.emitOpen();
        const removedHandler = vi.fn();
        const remainingHandler = vi.fn();
        client.subscribe("/topic/events", removedHandler);
        client.subscribe("/topic/events", remainingHandler);

        client.unsubscribe("/topic/events", removedHandler);

        expect(ws.decodeSentFrames()).toHaveLength(1);

        ws.emitMessage({
            type: SocketMessage.Type.SEND,
            destination: "/topic/events",
            payload: new Uint8Array([1, 2, 3]),
        });

        await vi.waitFor(() => expect(remainingHandler).toHaveBeenCalledOnce());
        expect(removedHandler).not.toHaveBeenCalled();
    });

    it("tears down only the final exact handler registration", () => {
        const { client, ws } = createClient();
        ws.emitOpen();
        const handler = vi.fn();
        const absentHandler = vi.fn();
        client.subscribe("/topic/events", handler);

        client.unsubscribe("/topic/events", absentHandler);
        client.unsubscribe("/topic/events", handler);
        client.unsubscribe("/topic/events", handler);
        client.unsubscribe("/topic/unknown", absentHandler);

        expect(typedDestinations(ws.decodeSentFrames())).toEqual([
            {
                type: SocketMessage.Type.SUBSCRIBE,
                destination: "/topic/events",
            },
            {
                type: SocketMessage.Type.UNSUBSCRIBE,
                destination: "/topic/events",
            },
        ]);
    });
});

describe("OCMPClient terminal session close", () => {
    it("closes the transport before forwarding a terminal close event", () => {
        const onClose = vi.fn((event: CloseEvent) => {
            expect(event.code).toBe(SESSION_ENDED);
            expect(MockWebsocketTransportBuilder.instance!.closeCalls).toBe(1);
        });
        const { ws } = createClient({ onClose });
        ws.emitOpen();

        const terminalEvent = ws.emitClose(SESSION_ENDED);

        expect(onClose).toHaveBeenCalledOnce();
        expect(onClose).toHaveBeenCalledWith(terminalEvent);
    });

    it.each([
        { code: 1000, label: "a normal closure" },
        { code: SESSION_ENDED, label: "an ended session" },
    ])("closes the transport after $label", ({ code }) => {
        const { ws } = createClient();
        ws.emitOpen();

        ws.emitClose(code);

        expect(ws.closeCalls).toBe(1);
    });

    it("leaves the transport open to reconnect after a non-terminal close", () => {
        const { ws } = createClient();
        ws.emitOpen();

        ws.emitClose(1006);

        expect(ws.closeCalls).toBe(0);
    });

    it("drops outbound work and does not resubscribe after SESSION_ENDED", () => {
        const clientRef: { current?: OCMPClient } = {};
        const onClose = vi.fn(() => {
            clientRef.current?.send("/topic/events", new Uint8Array([1, 2, 3]));
        });
        const { client, ws } = createClient({ onClose });
        clientRef.current = client;

        ws.emitOpen();
        client.subscribe("/topic/events", vi.fn());
        expect(ws.sent).toHaveLength(1);

        ws.emitClose(SESSION_ENDED);

        expect(onClose).toHaveBeenCalledOnce();
        expect(ws.sent).toHaveLength(1);
    });

    it("retains queue replay and active-destination resubscription after code 1006", () => {
        const { client, ws } = createClient();
        ws.emitOpen();
        client.subscribe("/topic/events", vi.fn());

        ws.emitClose(1006);
        client.send("/topic/events", new Uint8Array([1, 2, 3]));
        ws.clearSent();

        ws.emitOpen();

        expect(typedDestinations(ws.decodeSentFrames())).toEqual([
            {
                type: SocketMessage.Type.SUBSCRIBE,
                destination: "/topic/events",
            },
            {
                type: SocketMessage.Type.SEND,
                destination: "/topic/events",
            },
        ]);
    });
});

describe("OCMPClient protocol errors", () => {
    /** An `ERROR` frame as the server sends it: no destination, the topic in a header. */
    function errorFrame({
        code = "SUBSCRIBE_DENIED",
        operation = "SUBSCRIBE",
        rejected,
        reason = "",
    }: {
        code?: string;
        operation?: string;
        rejected?: string;
        reason?: string;
    }): SocketMessage.$Properties {
        return {
            type: SocketMessage.Type.ERROR,
            destination: "",
            headers: {
                code,
                operation,
                ...(rejected === undefined ? {} : { "rejected-destination": rejected }),
            },
            payload: new TextEncoder().encode(reason),
        };
    }

    it("delivers a decoded error to both the client-wide sink and the named subscription", async () => {
        const onProtocolError = vi.fn();
        const onError = vi.fn();
        const { client, ws } = createClient({ onProtocolError });
        ws.emitOpen();
        client.subscribe("/topic/events", vi.fn(), { onError });

        ws.emitMessage(
            errorFrame({
                code: "SUBSCRIPTION_REVOKED",
                operation: "SUBSCRIBE",
                rejected: "/topic/events",
                reason: "clearance withdrawn",
            }),
        );

        await vi.waitFor(() => {
            expect(onProtocolError).toHaveBeenCalledOnce();
            expect(onError).toHaveBeenCalledOnce();
        });
        const expected = {
            code: "SUBSCRIPTION_REVOKED",
            operation: "SUBSCRIBE",
            destination: "/topic/events",
            reason: "clearance withdrawn",
        };
        expect(onProtocolError).toHaveBeenCalledWith(expected);
        expect(onError).toHaveBeenCalledWith(expected);
    });

    it("routes on the rejected-destination header without delivering the frame as a message", async () => {
        const onError = vi.fn();
        const messageHandler = vi.fn();
        const { client, ws } = createClient();
        ws.emitOpen();
        client.subscribe("/topic/events", messageHandler, { onError });

        ws.emitMessage(errorFrame({ rejected: "/topic/events", reason: "denied" }));

        await vi.waitFor(() => expect(onError).toHaveBeenCalledOnce());
        expect(messageHandler).not.toHaveBeenCalled();
    });

    it("reports a destination-less fault to the client-wide sink alone", async () => {
        const onProtocolError = vi.fn();
        const onError = vi.fn();
        const { client, ws } = createClient({ onProtocolError });
        ws.emitOpen();
        client.subscribe("/topic/events", vi.fn(), { onError });

        ws.emitMessage(errorFrame({ code: "INVALID_DESTINATION", reason: "destination too long" }));

        await vi.waitFor(() => expect(onProtocolError).toHaveBeenCalledOnce());
        expect(onProtocolError).toHaveBeenCalledWith({
            code: "INVALID_DESTINATION",
            operation: "SUBSCRIBE",
            destination: undefined,
            reason: "destination too long",
        });
        expect(onError).not.toHaveBeenCalled();
    });

    it("reports an error whose subscription is already gone", async () => {
        const onProtocolError = vi.fn();
        const { ws } = createClient({ onProtocolError });
        ws.emitOpen();

        ws.emitMessage(errorFrame({ rejected: "/topic/unsubscribed", reason: "denied" }));

        await vi.waitFor(() => expect(onProtocolError).toHaveBeenCalledOnce());
        expect(onProtocolError).toHaveBeenCalledWith(
            expect.objectContaining({ destination: "/topic/unsubscribed" }),
        );
    });

    it("still notifies the revoked subscription when the client-wide sink throws", async () => {
        const onProtocolError = vi.fn(() => {
            throw new Error("sink failed");
        });
        const onError = vi.fn();
        const warnSpy = vi.spyOn(Logger.get("ws"), "warn").mockImplementation(() => undefined);
        const { client, ws } = createClient({ onProtocolError });
        ws.emitOpen();
        client.subscribe("/topic/events", vi.fn(), { onError });

        ws.emitMessage(errorFrame({ rejected: "/topic/events", reason: "denied" }));

        await vi.waitFor(() => expect(onError).toHaveBeenCalledOnce());
        expect(warnSpy).toHaveBeenCalledWith(
            expect.stringContaining("onProtocolError failed"),
            expect.anything(),
        );
    });

    it("isolates a throwing subscription handler from its siblings", async () => {
        const failing = vi.fn(() => {
            throw new Error("subscriber failed");
        });
        const later = vi.fn();
        const errorSpy = vi.spyOn(Logger.get("ws"), "error").mockImplementation(() => undefined);
        const { client, ws } = createClient();
        ws.emitOpen();
        client.subscribe("/topic/events", vi.fn(), { onError: failing });
        client.subscribe("/topic/events", vi.fn(), { onError: later });

        ws.emitMessage(errorFrame({ rejected: "/topic/events", reason: "denied" }));

        await vi.waitFor(() => expect(later).toHaveBeenCalledOnce());
        expect(errorSpy).toHaveBeenCalledWith(
            "Subscriber failed for destination:",
            "/topic/events",
        );
    });

    it("skips subscribers registered without an error handler", async () => {
        const onError = vi.fn();
        const errorSpy = vi.spyOn(Logger.get("ws"), "error").mockImplementation(() => undefined);
        const { client, ws } = createClient();
        ws.emitOpen();
        client.subscribe("/topic/events", vi.fn());
        client.subscribe("/topic/events", vi.fn(), { onError });

        ws.emitMessage(errorFrame({ rejected: "/topic/events", reason: "denied" }));

        await vi.waitFor(() => expect(onError).toHaveBeenCalledOnce());
        // Skipped, not invoked and swallowed — an absent handler must not log a failure.
        expect(errorSpy).not.toHaveBeenCalled();
    });

    it("still notifies a revoked subscription that the client-wide sink unsubscribes", async () => {
        const onError = vi.fn();
        const handler = vi.fn();
        const clientRef: { current?: OCMPClient } = {};
        const onProtocolError = vi.fn(() => {
            clientRef.current?.unsubscribe("/topic/events", handler);
        });
        const { client, ws } = createClient({ onProtocolError });
        clientRef.current = client;
        ws.emitOpen();
        client.subscribe("/topic/events", handler, { onError });

        ws.emitMessage(
            errorFrame({
                code: "SUBSCRIPTION_REVOKED",
                rejected: "/topic/events",
                reason: "clearance withdrawn",
            }),
        );

        await vi.waitFor(() => expect(onProtocolError).toHaveBeenCalledOnce());
        expect(onError).toHaveBeenCalledOnce();
    });
});
