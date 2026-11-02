// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { TFunction } from "i18next";
import type { OpencopNotification } from "@opencop/api";
import { NotificationSeverityType, NotificationType } from "@opencop/api";
import type { DropNotification } from "~/features/notifications/DropNotificationsContext";
import Logger from "js-logger";

const logger = Logger.get("notifications");

/**
 * Validate that an unknown value is a valid OpencopNotification
 */
function isValidNotification(data: unknown): data is OpencopNotification {
    if (typeof data !== "object" || data === null) {
        return false;
    }

    const obj = data as Record<string, unknown>;

    // Check required fields exist (backend uses notificationId, type is inside content)
    if (
        typeof obj.notificationId !== "string" ||
        typeof obj.issuerUserId !== "string" ||
        typeof obj.targetUserId !== "string" ||
        typeof obj.severity !== "string" ||
        typeof obj.status !== "string" ||
        typeof obj.createdAt !== "string" ||
        typeof obj.content !== "object" ||
        obj.content === null
    ) {
        return false;
    }

    // Validate enum values
    if (
        !Object.values(NotificationSeverityType).includes(obj.severity as NotificationSeverityType)
    ) {
        return false;
    }

    const content = obj.content as Record<string, unknown>;
    if (
        typeof content.type !== "string" ||
        !Object.values(NotificationType).includes(content.type as NotificationType)
    ) {
        return false;
    }

    return true;
}

/**
 * Decode a Uint8Array payload as JSON with runtime validation.
 * Notifications use JSON encoding (Jackson) unlike events which use protobuf.
 */
export function decodeJsonPayload<T>(payload: Uint8Array): T {
    const text = new TextDecoder().decode(payload);
    const data = JSON.parse(text);

    // Only validate OpencopNotification types (structural check via type guard)
    // For other types, fall back to unsafe cast
    if (
        typeof data === "object" &&
        data !== null &&
        "notificationId" in data &&
        "severity" in data
    ) {
        if (!isValidNotification(data)) {
            throw new Error("Invalid notification structure received from WebSocket");
        }
    }

    return data as T;
}

/**
 * Convert OpencopNotification to DropNotification format.
 */
export function convertToDropNotification(
    notification: OpencopNotification,
    t: TFunction,
): Omit<DropNotification, "id" | "timestamp" | "read"> {
    const { content, severity } = notification;
    const type = content.type;

    // Defensive check
    if (!content) {
        logger.error("📬 [Notifications] Missing content in notification:", notification);
        return {
            title: "Notification",
            message: "You have a new notification",
            type: "info",
            notificationType: type,
        };
    }

    // Map severity to DropNotification type
    let dropType: DropNotification["type"];

    // Override severity for specific operation types
    const operation = content.operationType?.toLowerCase();
    if (operation === "removed" || operation === "deleted") {
        dropType = "warning";
    } else if (operation === "added") {
        dropType = "success";
    } else {
        // Use severity-based mapping for other operations
        switch (severity) {
            case NotificationSeverityType.SUCCESS:
                dropType = "success";
                break;
            case NotificationSeverityType.WARNING:
                dropType = "warning";
                break;
            case NotificationSeverityType.ERROR:
            case NotificationSeverityType.CRITICAL:
                dropType = "error";
                break;
            case NotificationSeverityType.IMPORTANT:
                dropType = "important";
                break;
            case NotificationSeverityType.INFO:
            case NotificationSeverityType.NONE:
            default:
                dropType = "info";
                break;
        }
    }

    // Generate title and message based on notification type
    let title: string;
    let message: string;

    const operationForMessage = content.operationType?.toLowerCase() || "modified";
    const role = content.role
        ? t("notifications.withRole", {
              role: content.role,
          })
        : "";

    switch (type) {
        case NotificationType.SOURCE_ACCESS:
            title = t("notifications.sourceAccessTitle");
            message = t("notifications.sourceAccessMessage", {
                operation: operationForMessage,
                role,
            });
            break;

        case NotificationType.LAYER_ACCESS:
            title = t("notifications.layerAccessTitle");
            message = t("notifications.layerAccessMessage", {
                operation: operationForMessage,
                role,
            });
            break;

        case NotificationType.LAYER_SOURCE:
            title = t("notifications.layerSourceTitle");
            if (operationForMessage === "added") {
                message = t("notifications.layerSourceAddedMessage", {
                    role,
                });
            } else if (operationForMessage === "removed" || operationForMessage === "deleted") {
                message = t("notifications.layerSourceRemovedMessage");
            } else {
                message = t("notifications.layerSourceModifiedMessage", {
                    operation: operationForMessage,
                    role,
                });
            }
            break;

        default:
            title = t("notifications.defaultTitle");
            message = t("notifications.defaultMessage");
            break;
    }

    return {
        title,
        message,
        type: dropType,
        notificationType: type,
    };
}

/**
 * Convert a full backend OpencopNotification (from API) to DropNotification format.
 * This includes id, timestamp, and read status from the backend.
 */
export function convertBackendNotification(
    notification: OpencopNotification,
    t: TFunction,
): DropNotification {
    const baseNotification = convertToDropNotification(notification, t);

    return {
        ...baseNotification,
        id: notification.notificationId,
        timestamp: new Date(notification.createdAt),
        read: notification.read,
    };
}

/**
 * Convert an array of backend notifications to DropNotification format
 */
export function convertBackendNotifications(
    notifications: OpencopNotification[],
    t: TFunction,
): DropNotification[] {
    return notifications.map((n) => convertBackendNotification(n, t));
}
