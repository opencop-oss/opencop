// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { SocketMessage } from "@opencop/proto";
import pako from "pako";
import Logger from "js-logger";
import { ExponentialBackoffWithJitter } from "./ExponentialBackoffWithJitter";
import type { WebsocketTransport, WebsocketTransportBuilder } from "./WebsocketTransport";
import type { Constructor } from "type-fest";
import { WebsocketTsTransportBuilder } from "./WebsocketTsTransport";

const logger = Logger.get("ws");

/**
 * Receives one message delivered on a destination.
 *
 * `payload` and `headers` are shared: every handler on the destination gets the same instances,
 * decoded once per frame. Treat them as immutable — writing to the payload corrupts what the
 * siblings are about to read.
 */
export type OCMPMessageHandler = (
    payload: Uint8Array,
    headers?: Readonly<Record<string, string>>,
) => unknown;

/**
 * The server's rejection codes, plus `SUBSCRIPTION_REVOKED` — the odd member. Every other code
 * answers a frame this client sent; a revocation is server-initiated and arrives unsolicited,
 * potentially long after the subscription.
 */
export type ProtocolErrorCode =
    | "INVALID_DESTINATION"
    | "INVALID_TOPIC"
    | "UNKNOWN_TOPIC"
    | "SEND_NOT_SUPPORTED"
    | "SUBSCRIBE_DENIED"
    | "SUBSCRIPTION_REVOKED"
    | "INVALID_FRAME"
    | "UNSUPPORTED_FRAME_TYPE"
    | "UNSUPPORTED_MESSAGE_TYPE";

/**
 * Which client frame provoked the error. `TEXT` is not a frame type — the protocol is binary-only.
 *
 * Not a correlation key: revocations hardcode `SUBSCRIBE` despite the client having sent nothing,
 * so read {@link ProtocolErrorCode} to tell a rejection from a revocation.
 */
export type RejectedOperation =
    | "SUBSCRIBE"
    | "UNSUBSCRIBE"
    | "HEARTBEAT"
    | "SEND"
    | "CONNECT"
    | "ERROR"
    | "UNKNOWN"
    | "TEXT";

/**
 * A decoded `ERROR` frame.
 *
 * `destination` is absent whenever the fault has nothing to attribute it to. Test the field rather
 * than inferring from the code — `INVALID_DESTINATION` carries it for a bad prefix but omits it
 * when the length limit was hit.
 */
export type OCMPProtocolError = {
    code: ProtocolErrorCode;
    operation: RejectedOperation;
    reason: string;
    destination?: string;
};

/** Receives errors attributed to one subscription. Return value treated as {@link OCMPMessageHandler}. */
export type OCMPErrorHandler = (error: OCMPProtocolError) => unknown;

/** Per-handler subscription settings. Each handler on a destination carries its own. */
export type OCMPSubscribeOptions = Partial<{
    /**
     * Protocol errors carrying a `rejected-destination` header naming this handler's destination —
     * the server sets it for SUBSCRIBE_DENIED, SUBSCRIPTION_REVOKED, UNKNOWN_TOPIC, INVALID_TOPIC,
     * and INVALID_DESTINATION when a destination was present. Destination-less errors reach only
     * {@link OCMPClientOptions.onProtocolError}.
     */
    onError: OCMPErrorHandler;
}>;

/**
 * One decoded inbound frame. `destination` is empty on every `ERROR` frame — an error's topic
 * travels in the `rejected-destination` header — so route on `type` before trusting it.
 */
interface InboundFrame {
    readonly type: SocketMessage.Type;
    readonly destination: string;
    readonly payload: Uint8Array;
    readonly headers: Readonly<Record<string, string>>;
}

/** Duck-typed: `instanceof Promise` misses polyfills, userland promises, and other realms. */
function isPromiseLike(value: unknown): value is PromiseLike<unknown> {
    return typeof (value as PromiseLike<unknown> | null | undefined)?.then === "function";
}

export interface OCMPClientOptions {
    builder?: Constructor<WebsocketTransportBuilder>;
    heartbeatInterval?: number;
    onOpen?: () => void;
    onClose?: (event: CloseEvent) => void;
    /** Transport failure — a WebSocket `error` event. For server-sent errors see `onProtocolError`. */
    onError?: (event: Event) => void;
    onRetry?: (attempt: number) => void;
    onReconnect?: () => void;
    onMaxRetries?: () => void;
    /**
     * Every protocol error, including ones also delivered to a subscription's `onError` — so a
     * revocation is seen twice, and both should not raise a user-facing notice. The only place a
     * destination-less fault can surface.
     */
    onProtocolError?: (error: OCMPProtocolError) => void;
}

export const MAX_RECONNECT_ATTEMPTS = 10;
/** Close code the server sends when it ends the session; retrying it would never succeed. */
export const SESSION_ENDED = 4401;

const NORMAL_CLOSURE = 1000;

/** Whether a close code means "stop", as opposed to one worth reconnecting after. */
export function isTerminalClose(code: number): boolean {
    return code === NORMAL_CLOSURE || code === SESSION_ENDED;
}

/**
 * OpenCOP Messaging Protocol Client.
 *
 * Delivers inbound frames to per-destination subscribers and sends application data over a
 * {@link WebsocketTransport}, owning the subscription and outbound state that each new connection
 * is restored from.
 */
export class OCMPClient {
    private heartbeatInterval: ReturnType<typeof setInterval> | null = null;
    private retryCount = 0;

    private readonly ws: WebsocketTransport;
    private readonly options: OCMPClientOptions;
    private readonly subscriptions: Map<string, Map<OCMPMessageHandler, OCMPSubscribeOptions>> =
        new Map();
    /** Application frames encoded while disconnected, replayed in order on the next open socket. */
    private readonly outbox: Uint8Array[] = [];

    constructor(url: string, options: OCMPClientOptions = {}) {
        this.options = options;
        const { builder = WebsocketTsTransportBuilder } = this.options;
        logger.info("Connecting to", url);

        this.ws = new builder(url)
            .withBackoff(new ExponentialBackoffWithJitter(1000, 30000))
            .onOpen(() => {
                this.retryCount = 0;
                this.startHeartbeat(this.options.heartbeatInterval);

                this.restoreSubscriptions();
                this.flushOutbox();

                this.options?.onOpen?.();
            })
            .onClose((ev) => {
                this.stopHeartbeat();
                // Closing is how the client declines reconnection; done before notifying, so a
                // consumer never sees a close it must guess the finality of.
                if (isTerminalClose(ev.code)) {
                    this.ws.close();
                }
                this.options?.onClose?.(ev);
            })
            .onError((ev) => {
                this.stopHeartbeat();
                this.options?.onError?.(ev);
            })
            .onRetry(() => {
                this.retryCount++;
                if (this.retryCount > MAX_RECONNECT_ATTEMPTS) {
                    this.ws.close();
                    this.options?.onMaxRetries?.();
                } else {
                    this.options?.onRetry?.(this.retryCount);
                }
            })
            // Notification only. A replacement connection is reconciled by `onOpen`, which the
            // transport contract guarantees follows.
            .onReconnect(() => {
                this.options?.onReconnect?.();
            })
            .onMessage((ev) => {
                this.handleFrame(ev.data);
            })
            .build();
    }

    /**
     * Attaches a handler to `destination`, returning a function that detaches it.
     *
     * Only the first handler on a destination sends a `SUBSCRIBE` — the server-side subscription is
     * per-socket and fan-out is local. Subscribing while disconnected registers without sending;
     * `restoreSubscriptions()` covers it on the next open socket.
     */
    subscribe(
        destination: string,
        handler: OCMPMessageHandler,
        options: OCMPSubscribeOptions = {},
    ): () => void {
        logger.debug("Subscribing to", destination);

        const handlers = this.subscriptions.get(destination);
        if (handlers) {
            handlers.set(handler, options);
            return () => this.unsubscribe(destination, handler);
        }

        this.subscriptions.set(destination, new Map([[handler, options]]));

        this.sendControlFrame({
            type: SocketMessage.Type.SUBSCRIBE,
            destination: destination,
        });

        return () => this.unsubscribe(destination, handler);
    }

    /**
     * Detaches a handler from `destination`.
     *
     * The `UNSUBSCRIBE` frame goes out only when the last handler leaves.
     */
    unsubscribe(destination: string, handler: OCMPMessageHandler): void {
        logger.debug("Unsubscribing from", destination);

        const handlers = this.subscriptions.get(destination);
        if (!handlers) return;

        handlers.delete(handler);

        if (handlers.size === 0) {
            this.subscriptions.delete(destination);
            this.sendControlFrame({
                type: SocketMessage.Type.UNSUBSCRIBE,
                destination: destination,
            });
        }
    }

    /**
     * Sends application data. The only buffered lane: frames queue while disconnected and replay
     * as exact bytes, so they must still be meaningful whenever they land.
     */
    send(destination: string, payload: Uint8Array | null, headers?: Record<string, string>): void {
        this.sendDataFrame({
            type: SocketMessage.Type.SEND,
            headers: headers,
            destination: destination,
            payload: payload,
        });
    }

    /** Close the socket gracefully. */
    disconnect(): void {
        logger.info("Disconnecting");
        this.stopHeartbeat();
        this.ws.close();
    }

    isOpen(): boolean {
        return this.ws.isOpen();
    }

    isClosed(): boolean {
        return this.ws.isClosed();
    }

    isConnecting(): boolean {
        return this.ws.isConnecting();
    }

    /**
     * Entry point for every inbound frame. Decoding is async, so this is the last place an
     * exception can be caught before it becomes an unhandled rejection.
     */
    private handleFrame = async (data: unknown): Promise<void> => {
        try {
            const frame = await this.decodeFrame(data);
            this.dispatchFrame(frame);
        } catch (error) {
            logger.error("Failed to process inbound frame:", error);
        }
    };

    /**
     * Decodes one frame, decompressing the payload if the server marked it compressed. Once per
     * frame, so handlers on a destination share these instances — see {@link OCMPMessageHandler}.
     */
    private async decodeFrame(data: unknown): Promise<InboundFrame> {
        const binaryBlob = data as Blob;
        const byteArray = new Uint8Array(await binaryBlob.arrayBuffer());
        const message = SocketMessage.decode(byteArray);

        return {
            type: message.type,
            destination: message.destination,
            payload: message.compressed ? pako.ungzip(message.payload) : message.payload,
            headers: message.headers,
        };
    }

    /**
     * Routes a frame by type.
     */
    private dispatchFrame(frame: InboundFrame): void {
        if (frame.type === SocketMessage.Type.ERROR) {
            this.dispatchProtocolError(frame);
            return;
        }
        this.dispatchMessage(frame);
    }

    /**
     * Delivers a decoded `ERROR` frame.
     *
     * Routes on the `rejected-destination` header, never `frame.destination`, which is empty here.
     * The client-wide sink fires first and unconditionally, so a destination-less fault — or one
     * whose subscription is already gone — stays visible rather than being dropped.
     */
    private dispatchProtocolError(frame: InboundFrame): void {
        const rejected: string | undefined = frame.headers["rejected-destination"];

        const error: OCMPProtocolError = {
            code: frame.headers.code as ProtocolErrorCode,
            operation: frame.headers.operation as RejectedOperation,
            destination: rejected,
            reason: new TextDecoder().decode(frame.payload),
        };

        let snapshot: [OCMPMessageHandler, OCMPSubscribeOptions][] = [];
        if (rejected) {
            const handlers = this.subscriptions.get(rejected);
            snapshot = [...(handlers ?? [])];
        }

        // Contained separately from the per-subscription handlers below: a throwing sink must not
        // stop the subscriptions that were actually revoked from hearing about it.
        try {
            this.options?.onProtocolError?.(error);
        } catch {
            logger.warn(`onProtocolError failed for error: `, error);
        }

        for (const [, { onError }] of snapshot) {
            if (!onError) continue;
            this.invokeSafely(rejected, () => onError(error));
        }
    }

    /**
     * Fans one message out to every handler on its destination.
     */
    private dispatchMessage(frame: InboundFrame): void {
        const dest = frame.destination;
        const handlers = this.subscriptions.get(dest);
        if (!handlers) {
            logger.warn("No handlers for destination:", dest);
            return;
        }

        const snapshot = [...handlers];
        for (const [handler] of snapshot) {
            this.invokeSafely(dest, () => handler(frame.payload, frame.headers));
        }
    }

    /**
     * Shared containment: one handler's failure never escapes or blocks its siblings.
     */
    private invokeSafely(destination: string, call: () => unknown): void {
        try {
            const result = call();
            if (isPromiseLike(result)) {
                void Promise.resolve(result).catch(() => this.logHandlerFailure(destination));
            }
        } catch {
            this.logHandlerFailure(destination);
        }
    }

    /**
     * Sends application data, queueing it while disconnected. Replay is of exact bytes rather than
     * current intent, so a queued frame must still be meaningful whenever it lands.
     */
    private sendDataFrame(message: SocketMessage.$Properties): void {
        const frame = this.encodeFrame(message);
        if (!this.isOpen()) {
            this.outbox.push(frame);
            return;
        }
        this.ws.send(frame);
    }

    /**
     * Sends a control frame, dropping it if the socket is not open.
     *
     * `SUBSCRIBE`, `UNSUBSCRIBE`, and `HEARTBEAT` express current intent, so queueing them would
     * assert something stale on arrival. `restoreSubscriptions()` covers what a new socket is owed.
     */
    private sendControlFrame(message: SocketMessage.$Properties): void {
        if (!this.isOpen()) return;

        this.ws.send(this.encodeFrame(message));
    }

    /**
     * Replays queued application frames in FIFO order. Drained into a local batch first so a
     * handler that sends during replay appends behind it rather than into the batch in flight.
     */
    private flushOutbox(): void {
        const queued = this.outbox.splice(0, this.outbox.length);
        for (const frame of queued) {
            this.ws.send(frame);
        }
    }

    /** Encodes a frame to its protobuf wire bytes. */
    private encodeFrame(message: SocketMessage.$Properties): Uint8Array {
        return SocketMessage.encode(SocketMessage.create(message)).finish();
    }

    /**
     * (Re)starts the heartbeat. Stopping first keeps it idempotent, which matters because `onOpen`
     * fires again for every replacement connection.
     */
    private startHeartbeat(interval: number = 15_000): void {
        this.stopHeartbeat();
        this.heartbeatInterval = setInterval(() => {
            this.sendControlFrame({
                type: SocketMessage.Type.HEARTBEAT,
            });
        }, interval);
    }

    /** Stop the heartbeat. */
    private stopHeartbeat(): void {
        if (this.heartbeatInterval) {
            clearInterval(this.heartbeatInterval);
            this.heartbeatInterval = null;
        }
    }

    /**
     * Re-sends a `SUBSCRIBE` for every registered destination after the socket opens. Derived from
     * the registry rather than a replay buffer, so destinations dropped while disconnected are
     * never sent.
     */
    private restoreSubscriptions(): void {
        for (const destination of this.subscriptions.keys()) {
            this.sendControlFrame({
                type: SocketMessage.Type.SUBSCRIBE,
                destination,
            });
        }
    }

    private logHandlerFailure(destination: string): void {
        logger.error("Subscriber failed for destination:", destination);
    }
}
