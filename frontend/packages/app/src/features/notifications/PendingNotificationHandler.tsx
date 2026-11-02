// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect } from "react";
import { useMapbarStore } from "~/features/mapbar/mapbar.store";
import { useDropNotifications } from "./DropNotificationsContext";

/** Opens the notification panel when a pending notification arrives. */
export function PendingNotificationHandler() {
    const { pendingNotificationId, openNotificationPanel, setPendingNotificationId } =
        useDropNotifications();
    const togglePanel = useMapbarStore((s) => s.togglePanel);

    useEffect(() => {
        if (pendingNotificationId) {
            openNotificationPanel(pendingNotificationId, togglePanel);
            setPendingNotificationId(null);
        }
    }, [pendingNotificationId, openNotificationPanel, setPendingNotificationId, togglePanel]);

    return null;
}
