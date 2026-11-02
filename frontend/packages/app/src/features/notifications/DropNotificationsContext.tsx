// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    createContext,
    use,
    useState,
    useCallback,
    useEffect,
    useRef,
    useMemo,
    type MutableRefObject,
    type ReactNode,
    type ReactElement,
} from "react";
import { useTranslation } from "react-i18next";
import { v4 as uuidv4 } from "uuid";
import scrollIntoView from "scroll-into-view-if-needed";
import { useNotifications, NOTIFICATIONS_QUERY_KEY } from "./notifications.queries";
import { convertBackendNotifications } from "./notification.util";
import { useNotificationSubscription } from "./use-notification-subscription.hook";
import { queryClient } from "~/queryClient";
import type { NotificationType } from "@opencop/api";
import { markNotificationAsRead, markAllNotificationsAsRead } from "@opencop/api";
import Logger from "js-logger";

const logger = Logger.get("notifications");

export type Latitude = number;
export type Longitude = number;
export type Coordinate = [Longitude, Latitude];

export type MapPing = {
    pitch: number;
    bearing: number;
    zoom: number;
    center: Coordinate;
};

export type DropNotification = {
    id: string;
    title: string;
    message: string;
    timestamp: Date;
    read: boolean;
    type: "info" | "warning" | "success" | "error" | "important";
    notificationType: NotificationType;
    mapPing?: MapPing;
};

export type EphemeralToast = {
    id: string;
    title: string;
    type: DropNotification["type"];
    description?: string;
    expiresAt: number;
    /**
     * Rich body rendered under `description`. Mounted in the root toast stack, outside
     * MapManagerProvider, and removed after TOAST_DISMISS_MS with no pause on hover or
     * focus and no live-region announcement.
     */
    content?: ReactElement;
};

export const TOAST_DISMISS_MS = 5000;

export type ShowToastOptions = Omit<EphemeralToast, "id" | "expiresAt">;

const MAX_VISIBLE_TOASTS = 3;

export type DropNotificationContextType = {
    dropNotifications: DropNotification[];
    toasts: EphemeralToast[];
    showToast: (opts: ShowToastOptions) => void;
    notificationsOpen: boolean;
    setNotificationsOpen: (value: boolean) => void;
    toggleDrawer: () => void;
    markAsRead: (id: string) => Promise<void>;
    markAllAsRead: () => Promise<void>;
    highlightNotification: (id: string) => void;
    highlightedId: string | null;
    notificationRefs: MutableRefObject<Map<string, HTMLDivElement>>;
    pendingNotificationId: string | null;
    setPendingNotificationId: (id: string | null) => void;
    openNotificationPanel: (id: string, toggleMapbar?: (type: string) => void) => void;
};

const DropNotificationContext = createContext<DropNotificationContextType | null>(null);

export function useDropNotifications(): DropNotificationContextType {
    const ctx = use(DropNotificationContext);
    if (!ctx)
        throw new Error("useDropNotifications must be used within a DropNotificationProvider.");
    return ctx;
}

type ProviderProps = {
    children?: ReactNode;
};

export function DropNotificationProvider({ children }: ProviderProps) {
    const { t } = useTranslation();

    // Subscribe to real-time notifications via WebSocket
    useNotificationSubscription();

    // Fetch notifications directly from React Query cache
    const { data: notificationsPage } = useNotifications();

    // Convert backend notifications to DropNotification format
    const dropNotifications = useMemo<DropNotification[]>(() => {
        if (!notificationsPage?.content) return [];
        return convertBackendNotifications(notificationsPage.content, t);
    }, [notificationsPage, t]);

    // Ephemeral toast queue (local-only, not persisted)
    const [toasts, setToasts] = useState<EphemeralToast[]>([]);
    const toastTimersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

    const showToast = useCallback((opts: ShowToastOptions) => {
        const toast: EphemeralToast = {
            id: uuidv4(),
            ...opts,
            expiresAt: Date.now() + TOAST_DISMISS_MS,
        };
        setToasts((prev) => {
            // Cancel timers for any toasts that will be evicted
            const evicted = prev.slice(0, Math.max(0, prev.length - (MAX_VISIBLE_TOASTS - 1)));
            for (const t of evicted) {
                const tid = toastTimersRef.current.get(t.id);
                if (tid !== undefined) clearTimeout(tid);
                toastTimersRef.current.delete(t.id);
            }
            return [...prev.slice(-(MAX_VISIBLE_TOASTS - 1)), toast];
        });

        const tid = setTimeout(() => {
            setToasts((prev) => prev.filter((t) => t.id !== toast.id));
            toastTimersRef.current.delete(toast.id);
        }, TOAST_DISMISS_MS);
        toastTimersRef.current.set(toast.id, tid);
    }, []);

    // Clean up toast timers on unmount
    useEffect(() => {
        const timers = toastTimersRef.current;
        return () => {
            for (const tid of timers.values()) clearTimeout(tid);
            timers.clear();
        };
    }, []);

    // UI-only state
    const [notificationsOpen, setNotificationsOpen] = useState(false);
    const [pendingNotificationId, setPendingNotificationId] = useState<string | null>(null);

    const toggleDrawer = useCallback(() => {
        setNotificationsOpen((prev) => !prev);
    }, []);

    const [highlightedId, setHighlightedId] = useState<string | null>(null);
    // eslint-disable-next-line @eslint-react/naming-convention/ref-name -- plural "Refs" already conveys ref-ness
    const notificationRefs = useRef<Map<string, HTMLDivElement>>(new Map());

    const highlightNotification = useCallback((id: string) => {
        // Highlight the notification

        setHighlightedId(id);

        // Scroll to the notification
        const element = notificationRefs.current.get(id);
        if (element) {
            scrollIntoView(element, {
                scrollMode: "if-needed",
                behavior: "smooth",
                block: "center",
                inline: "nearest",
            });

            // Wait for scroll animation to complete before blinking
            setTimeout(() => {
                let blinkCount = 0;
                const blinkInterval = setInterval(() => {
                    if (blinkCount % 2 === 0) {
                        // On - show blue (using outline and box-shadow to avoid layout shift)
                        element.style.outline = "2px solid #238fdc";
                        element.style.outlineOffset = "-2px";
                        element.style.boxShadow = "0 0 20px 4px rgba(35, 143, 220, 0.6)";
                    } else {
                        // Off - hide
                        element.style.outline = "none";
                        element.style.boxShadow = "none";
                    }
                    blinkCount++;

                    if (blinkCount >= 6) {
                        // 3 full blinks (on/off/on/off/on/off)
                        clearInterval(blinkInterval);
                        // Clean up
                        element.style.outline = "";
                        element.style.outlineOffset = "";
                        element.style.boxShadow = "";
                    }
                }, 400); // 400ms for each on/off state
            }, 600); // Wait 600ms for scroll to complete
        }

        // Remove highlight after 3 seconds (600ms scroll + 2400ms blink)
        setTimeout(() => setHighlightedId(null), 3000);
    }, []);

    const markAsRead = useCallback(async (id: string) => {
        try {
            await markNotificationAsRead(id);
            // Refetch notifications after marking as read
            await queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_KEY });
        } catch (error) {
            logger.error("Failed to mark notification as read:", error);
        }
    }, []);

    const markAllAsRead = useCallback(async () => {
        try {
            // Mark all notifications as read (fetches unread and marks each individually)
            await markAllNotificationsAsRead();

            // Refetch notifications after marking all as read
            await queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_KEY });
        } catch (error) {
            logger.error("Failed to mark all notifications as read:", error);
        }
    }, []);

    // Opens notification panel and highlights specific notification
    const openNotificationPanel = useCallback(
        (id: string, toggleMapbar?: (type: string) => void) => {
            if (toggleMapbar) {
                // If on map page, open the panel immediately
                toggleMapbar("notifications");
                setTimeout(() => {
                    highlightNotification(id);
                }, 500);
            } else {
                // If not on map page, set pending ID for when we navigate there
                setPendingNotificationId(id);
            }
        },
        [highlightNotification],
    );

    const contextValue = useMemo(
        () => ({
            dropNotifications,
            toasts,
            showToast,
            notificationsOpen,
            setNotificationsOpen,
            toggleDrawer,
            markAsRead,
            markAllAsRead,
            highlightNotification,
            notificationRefs,
            highlightedId,
            pendingNotificationId,
            setPendingNotificationId,
            openNotificationPanel,
        }),
        [
            dropNotifications,
            toasts,
            showToast,
            notificationsOpen,
            toggleDrawer,
            markAsRead,
            markAllAsRead,
            highlightNotification,
            highlightedId,
            pendingNotificationId,
            openNotificationPanel,
        ],
    );

    return <DropNotificationContext value={contextValue}>{children}</DropNotificationContext>;
}
