// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useWebSocket } from "./WebSocketContext";

/**
 * Hook to access WebSocket connection status.
 *
 * Provides convenient boolean flags and status information
 * for components that need to react to connection state.
 */
export function useConnectionStatus() {
    const { connection } = useWebSocket();

    return {
        status: connection.status,
        isConnected: connection.status === "connected",
        isConnecting: connection.status === "connecting",
        isReconnecting: connection.status === "reconnecting",
        isDisconnected: connection.status === "disconnected",
        isFailed: connection.status === "failed",
        reconnectAttempt: connection.reconnectAttempt,
        maxAttempts: connection.maxAttempts,
        error: connection.error,
    };
}
