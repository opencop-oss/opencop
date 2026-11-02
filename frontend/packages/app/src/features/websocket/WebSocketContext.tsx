// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * App-level WebSocket provider. Use `useWebSocket()` to access in child components.
 *
 * Provides the OCMPClient and connection state, and bridges terminal session closure to auth.
 * Feature-specific subscriptions are owned by their respective providers
 * (ElementStoreProvider, DropNotificationProvider).
 */

import { createContext, use, useCallback, useMemo, type ReactNode } from "react";
import { useOCMPClient } from "./use-ocmp-client.hook";
import { SESSION_ENDED, type OCMPClient } from "@opencop/websocket";
import type { ConnectionState } from "./use-ocmp-client.hook";
import { useAuth } from "~/features/auth/AuthContext";

export type { ConnectionStatus, ConnectionState } from "./use-ocmp-client.hook";

export interface WebSocketContextType {
    client: OCMPClient;
    connection: ConnectionState;
}

const WebSocketContext = createContext<WebSocketContextType | null>(null);

export function useWebSocket(): WebSocketContextType {
    const ctx = use(WebSocketContext);
    if (!ctx) throw new Error("useWebSocket must be used within a WebSocketProvider.");
    return ctx;
}

/**
 * Initializes and exposes the `OCMPClient` handling the WebSocket connection.
 * Auth is handled via cookies (BFF pattern).
 */
export function WebSocketProvider({ children }: { children: ReactNode }): ReactNode {
    const { handleSessionExpiry } = useAuth();
    const handleClose = useCallback(
        (event: CloseEvent) => {
            if (event.code === SESSION_ENDED) handleSessionExpiry();
        },
        [handleSessionExpiry],
    );
    const [client, connection] = useOCMPClient({ onClose: handleClose });

    const contextValue = useMemo(() => ({ client: client!, connection }), [client, connection]);

    return <WebSocketContext value={contextValue}>{children}</WebSocketContext>;
}
