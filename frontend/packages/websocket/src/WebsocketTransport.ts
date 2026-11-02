// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Application-facing websocket transport.
 *
 * This interface isolates application code from teh underlying websocket implementation
 * and exposes only the transport semantics the application relies on.
 */
export interface WebsocketTransport {
    isOpen(): boolean;
    isClosed(): boolean;
    isConnecting(): boolean;

    /**
     * Sends immediately.
     *
     * Data is dropped if the socket is not open. The transport does not buffer or replay the messages.
     *
     * @param data
     */
    send(data: string | ArrayBufferLike | Blob | ArrayBufferView): void;

    /**
     * Closes the socket permanently.
     *
     * After close(), the transport will not attempt to reconnect.
     */
    close(code?: number): void;
}

/**
 * Configures and creates a {@link WebsocketTransport}.
 *
 * This is the application-facing construction boundary for the underlying
 * websocket implementation.
 */
export interface WebsocketTransportBuilder {
    /** Applies a retry/backoff policy to the transport, defined by {@link Backoff}. */
    withBackoff(backoff?: Backoff): this;

    /** Called after every successful connection. */
    onOpen(listener: () => void): this;
    /** Called after close. */
    onClose(listener: (event: CloseEvent) => void): this;
    /** Called after every _transport_ error */
    onError(listener: (event: Event) => void): this;
    /** Called when a connect attempt it going to be retried. */
    onRetry(listener: () => void): this;
    /** Called when reconnecting, before the subsequent {@link onOpen} callback. */
    onReconnect(listener: () => void): this;
    /** called after every message. */
    onMessage(listener: (ev: MessageEvent) => void): this;
    /** Builds the transport. */
    build(): WebsocketTransport;
}

/**
 * Defines the retry/backoff policy used by a {@link WebsocketTransport}.
 */
export interface Backoff {
    retries: number;
    current: number;
    next(): number;
    reset(): void;
}
