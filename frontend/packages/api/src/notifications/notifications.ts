// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch } from "../client";
import type { Page } from "../common";

/**
 * Notification types matching the Java backend models
 */

export const NotificationType = {
    SOURCE_ACCESS: "SOURCE_ACCESS",
    LAYER_ACCESS: "LAYER_ACCESS",
    LAYER_SOURCE: "LAYER_SOURCE",
} as const;

export type NotificationType = (typeof NotificationType)[keyof typeof NotificationType];

export const NotificationSeverityType = {
    SUCCESS: "SUCCESS",
    WARNING: "WARNING",
    ERROR: "ERROR",
    CRITICAL: "CRITICAL",
    IMPORTANT: "IMPORTANT",
    INFO: "INFO",
    NONE: "NONE",
} as const;

export type NotificationSeverityType =
    (typeof NotificationSeverityType)[keyof typeof NotificationSeverityType];

export const NotificationStatus = {
    CREATED: "CREATED",
    PENDING: "PENDING",
    SENT: "SENT",
    FAILED: "FAILED",
} as const;

export type NotificationStatus = (typeof NotificationStatus)[keyof typeof NotificationStatus];

export const AccessOperationType = {
    ADDED: "ADDED",
    REMOVED: "REMOVED",
    MODIFIED: "MODIFIED",
} as const;

export type AccessOperationType = (typeof AccessOperationType)[keyof typeof AccessOperationType];

interface BaseNotificationContent {
    type: NotificationType;
    operationType?: AccessOperationType;
    role?: string;
    templateKey?: string;
}

interface SourceAccessContent extends BaseNotificationContent {
    type: typeof NotificationType.SOURCE_ACCESS;
    sourceId: string;
}

interface LayerAccessContent extends BaseNotificationContent {
    type: typeof NotificationType.LAYER_ACCESS;
    layerId: string;
}

interface LayerSourceContent extends BaseNotificationContent {
    type: typeof NotificationType.LAYER_SOURCE;
    affectedId: string; // layerId
    targetId: string; // sourceId
}

interface BaseNotification {
    notificationId: string;
    correlationId?: string | null;
    issuerUserId: string;
    targetUserId: string;
    severity: NotificationSeverityType;
    status: NotificationStatus;
    createdAt: string;
    read: boolean;
    readAt?: string | null;
}

interface SourceAccessNotification extends BaseNotification {
    content: SourceAccessContent;
}

interface LayerAccessNotification extends BaseNotification {
    content: LayerAccessContent;
}

interface LayerSourceNotification extends BaseNotification {
    content: LayerSourceContent;
}

export type OpencopNotification =
    | SourceAccessNotification
    | LayerAccessNotification
    | LayerSourceNotification;

/**
 * Get all notifications for the current user
 * Returns paginated response from Spring Data
 * @param unreadOnly - If true, only return unread notifications
 * @param page - Page number (0-indexed)
 * @param size - Page size
 */
export async function getNotifications(
    unreadOnly = false,
    page = 0,
    size = 20,
): Promise<Page<OpencopNotification>> {
    const params = new URLSearchParams({
        unreadOnly: String(unreadOnly),
        page: String(page),
        size: String(size),
    });
    return apiFetch<Page<OpencopNotification>>(`/notifications?${params}`);
}

/**
 * Mark a notification as read
 * @param notificationId - ID of the notification to mark as read
 */
export async function markNotificationAsRead(notificationId: string): Promise<void> {
    return apiFetch(`/notifications/${notificationId}/read`, {
        method: "POST",
    });
}

/**
 * Mark all notifications as read
 * Note: Backend doesn't have a bulk endpoint, so this fetches all unread notifications
 * and marks them individually in batches to avoid saturating the server — see
 * docs/best-practices/data-fetching/react-query.md § "Bulk per-item requests".
 */
export async function markAllNotificationsAsRead(): Promise<void> {
    // Fetch all unread notifications
    const unreadPage = await getNotifications(true, 0, 1000);

    // Mark each one as read in batches of 10 to limit concurrency
    const BATCH_SIZE = 10;
    for (let i = 0; i < unreadPage.content.length; i += BATCH_SIZE) {
        const batch = unreadPage.content.slice(i, i + BATCH_SIZE);
        await Promise.all(
            batch.map((notification) => markNotificationAsRead(notification.notificationId)),
        );
    }
}
