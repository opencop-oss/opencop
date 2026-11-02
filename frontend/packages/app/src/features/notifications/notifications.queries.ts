// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from "@tanstack/react-query";
import type { OpencopNotification, Page } from "@opencop/api";
import { getNotifications } from "@opencop/api";

/**
 * Query key for the user's notifications list
 */
export const NOTIFICATIONS_QUERY_KEY = ["notifications"] as const;

/** Query key for one page of the notifications list; the defaults are the drop-notification page. */
export const notificationsPageQueryKey = (unreadOnly = false, page = 0, size = 20) =>
    [...NOTIFICATIONS_QUERY_KEY, unreadOnly, page, size] as const;

/**
 * Hook to fetch all notifications for the current user
 *
 * @param unreadOnly - If true, only return unread notifications
 * @param page - Page number (0-indexed)
 * @param size - Page size
 */
export const useNotifications = (unreadOnly = false, page = 0, size = 20) => {
    return useQuery<Page<OpencopNotification>, Error>({
        queryKey: notificationsPageQueryKey(unreadOnly, page, size),
        queryFn: () => getNotifications(unreadOnly, page, size),
        staleTime: 30000, // Consider data fresh for 30 seconds
    });
};
