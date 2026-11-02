// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Low-level WebSocket hook with singleton behavior.
 * Most components should use `useWebSocket()` from WebSocketProvider instead.
 * @see ../README.md for architecture details
 */

import {
    isTerminalClose,
    MAX_RECONNECT_ATTEMPTS,
    OCMPClient,
    type OCMPClientOptions,
} from "@opencop/websocket";
import Logger from "js-logger";
import { useCallback, useEffect, useState, useSyncExternalStore } from "react";

export type ConnectionStatus =
    | "idle" // Initial state
    | "connecting" // First connection attempt
    | "connected" // WebSocket open and ready
    | "reconnecting" // Lost connection, attempting to reconnect
    | "disconnected" // Temporarily disconnected
    | "failed"; // Max retries exceeded, gave up

export interface ConnectionState {
    status: ConnectionStatus;
    reconnectAttempt: number;
    maxAttempts: number;
    error: Error | null;
}

const DEFAULT_CONNECTION_STATE: ConnectionState = {
    status: "idle",
    reconnectAttempt: 0,
    maxAttempts: MAX_RECONNECT_ATTEMPTS,
    error: null,
};

interface ClientEntry {
    client: OCMPClient;
    connection: ConnectionState;
    refCount: number;
    subscribers: Set<() => void>;
}

/** Module-level singleton. Not SSR-safe (client-only). */
let clientEntry: ClientEntry | null = null;

/** Disconnects and removes the client. For test cleanup only. */
export function clearClient(): void {
    clientEntry?.client.disconnect();
    clientEntry = null;
}

/** Get or create the singleton OCMPClient. Uses reference counting. */
function getOrCreateClient(options?: OCMPClientOptions): ClientEntry {
    const logger = Logger.get("ws");

    if (clientEntry) {
        clientEntry.refCount++;
        return clientEntry;
    }

    const entry: ClientEntry = {
        client: null as unknown as OCMPClient,
        connection: { ...DEFAULT_CONNECTION_STATE, status: "connecting" },
        refCount: 1,
        subscribers: new Set(),
    };

    const updateConnection = (updater: (prev: ConnectionState) => ConnectionState) => {
        entry.connection = updater(entry.connection);
        entry.subscribers.forEach((callback) => callback());
    };

    entry.client = new OCMPClient(import.meta.env.VITE_WEBSOCKET_URL, {
        heartbeatInterval: 15_000,
        onOpen: () => {
            logger.info("Connected");
            updateConnection((prev) => ({
                ...prev,
                status: "connected",
                reconnectAttempt: 0,
                error: null,
            }));
            options?.onOpen?.();
        },
        onClose: (event) => {
            const status = isTerminalClose(event.code) ? "disconnected" : "reconnecting";
            logger.warn(`Closed (code: ${event.code}), status: ${status}`);
            updateConnection((prev) => ({
                ...prev,
                status,
            }));
            options?.onClose?.(event);
        },
        onError: (event) => {
            logger.error("Error");
            options?.onError?.(event);
        },
        onRetry: (attempt) => {
            logger.info(`[WS] Reconnecting (attempt ${attempt}/${MAX_RECONNECT_ATTEMPTS})...`);
            updateConnection((prev) => ({
                ...prev,
                status: "reconnecting",
                reconnectAttempt: attempt,
            }));
            options?.onRetry?.(attempt);
        },
        onReconnect: () => {
            logger.info("Reconnected");
            updateConnection((prev) => ({
                ...prev,
                status: "connected",
                reconnectAttempt: 0,
                error: null,
            }));
            options?.onReconnect?.();
        },
        onMaxRetries: () => {
            logger.error("Max reconnection attempts reached");
            updateConnection((prev) => ({
                ...prev,
                status: "failed",
                error: new Error("Max reconnection attempts reached"),
            }));
            options?.onMaxRetries?.();
        },
        onProtocolError: (error) => {
            logger.warn(
                "Protocol error",
                error.code,
                error.operation,
                error.destination,
                error.reason,
            );
            options?.onProtocolError?.(error);
        },
    });

    clientEntry = entry;
    return entry;
}

/** Release a reference. When refCount reaches 0, disconnect and cleanup. */
function releaseClient(): void {
    if (!clientEntry) return;
    const logger = Logger.get("ws");

    clientEntry.refCount--;
    if (clientEntry.refCount === 0) {
        logger.info("All references released, disconnecting");
        clientEntry.client.disconnect();
        clientEntry = null;
    }
}

/**
 * Low-level hook to get the singleton OCMPClient.
 *
 * Multiple components share the same WebSocket connection.
 * Reference counting ensures the connection stays alive while any consumer
 * is mounted, and automatically disconnects when all consumers unmount.
 * Lifecycle options are captured only when the shared client is created;
 * later callers and rerenders do not replace those callbacks.
 *
 * @param options - Optional callbacks for WebSocket lifecycle events
 * @returns Tuple of [OCMPClient | null, ConnectionState]
 *
 * @example
 * const [client, connection] = useOCMPClient();
 *
 * @see useWebSocket - Preferred hook for most components
 */
export function useOCMPClient(options?: OCMPClientOptions): [OCMPClient | null, ConnectionState] {
    const [entry] = useState<ClientEntry | null>(() => getOrCreateClient(options));

    useEffect(() => {
        return () => releaseClient();
    }, []);

    const subscribe = useCallback(
        (callback: () => void) => {
            const current = entry ?? clientEntry;
            if (!current) return () => {};
            current.subscribers.add(callback);
            return () => current.subscribers.delete(callback);
        },
        [entry],
    );

    const getSnapshot = useCallback(
        () => (entry ?? clientEntry)?.connection ?? DEFAULT_CONNECTION_STATE,
        [entry],
    );

    const connection = useSyncExternalStore(subscribe, getSnapshot, () => DEFAULT_CONNECTION_STATE);

    return [entry?.client ?? null, connection];
}
