// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect } from "react";
import type { OpencopNotification } from "@opencop/api";
import { NotificationType } from "@opencop/api";
import { useWebSocket } from "~/features/websocket/WebSocketContext";
import { decodeJsonPayload } from "./notification.util";
import { queryClient } from "~/queryClient";
import { NOTIFICATIONS_QUERY_KEY } from "./notifications.queries";
import { layerQueries } from "~/features/layers/layers.queries";
import Logger from "js-logger";

const logger = Logger.get("ws_notifications");
const destination = "/user/queue/notification";

/**
 * Subscribes to the WebSocket notification queue and writes incoming
 * notifications directly into the React Query cache.
 */
export function useNotificationSubscription(): void {
    const { client } = useWebSocket();

    useEffect(() => {
        const handler = (payload: Uint8Array) => {
            try {
                const notification = decodeJsonPayload<OpencopNotification>(payload);

                // When the user gains or loses layer access (e.g. via a group assignment),
                // invalidate myList so useFilteredSubscriptions recomputes WS destinations
                // and useSubscription automatically adds/removes the layer topic.
                if (notification.content?.type === NotificationType.LAYER_ACCESS) {
                    queryClient.invalidateQueries({ queryKey: layerQueries.myList().queryKey });
                }

                queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_KEY });
            } catch (error) {
                logger.error("Failed to process notification:", error);
            }
        };

        client.subscribe(destination, handler);
        return () => client.unsubscribe(destination, handler);
    }, [client]);
}
