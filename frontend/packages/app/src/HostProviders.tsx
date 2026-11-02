// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { QueryClientProvider, type QueryClient } from "@tanstack/react-query";
import { AuthProvider } from "./features/auth/AuthContext";
import { WebSocketProvider } from "./features/websocket/WebSocketContext";
import { DropNotificationProvider } from "./features/notifications/DropNotificationsContext";
import { HostFilterFieldsProvider } from "./features/filter-fields/HostFilterFieldsProvider";
// IngestProvider isn't in the `@opencop/ingest` barrel.
import { IngestProvider, type IngestProviderProps } from "../../ingest/src/react/IngestContext";

export interface HostProvidersProps {
    readonly queryClient: QueryClient;
    readonly ingestPolicy: IngestProviderProps["policy"];
    /**
     * App start-up side effects (source registration, native and plugin installation). Rendered
     * inside `IngestProvider` and before `HostFilterFieldsProvider`, so contributions register
     * their filter fields before that provider's subtree renders rather than during it.
     */
    readonly effects?: ReactNode;
    readonly children?: ReactNode;
}

/**
 * The contexts every authenticated surface of the host renders inside: data fetching, auth,
 * the WebSocket, ingest, filter fields and drop notifications. Contexts only — the router, the
 * theme and error boundaries belong to whoever mounts this, and mounting it runs none of
 * the app's start-up effects.
 */
export function HostProviders({
    queryClient,
    ingestPolicy,
    effects,
    children,
}: HostProvidersProps) {
    return (
        <QueryClientProvider client={queryClient}>
            <AuthProvider>
                <WebSocketProvider>
                    <IngestProvider policy={ingestPolicy}>
                        {effects}
                        <HostFilterFieldsProvider>
                            <DropNotificationProvider>{children}</DropNotificationProvider>
                        </HostFilterFieldsProvider>
                    </IngestProvider>
                </WebSocketProvider>
            </AuthProvider>
        </QueryClientProvider>
    );
}
