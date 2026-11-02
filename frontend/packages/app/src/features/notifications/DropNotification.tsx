// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useState, useEffect, useCallback, useRef } from "react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { Badge, Float, Flex, Box } from "@chakra-ui/react";
import { FaTimes, FaMapMarkerAlt, FaBell, FaCheck } from "react-icons/fa";
import {
    useDropNotifications,
    TOAST_DISMISS_MS,
    type MapPing,
    type DropNotification as DropNotificationType,
    type EphemeralToast,
} from "./DropNotificationsContext";
import { useMapbarStore } from "~/features/mapbar/mapbar.store";
import { IconButton, ActionButton, Panel, TacticalText } from "@opencop/ui";
import { useNavigate, useLocation } from "react-router";
import { useViewActions } from "../map/core/slices/view/view.hook";
import { useNotificationPreferencesStore } from "./notification-preferences.store";

const LEFT_OFFSET = "74px";
const TOAST_STACK_GAP = 8;
const NOTIFICATION_DISMISS_MS = 5000;

export function DropNotification() {
    const { t } = useTranslation();
    const { toasts } = useDropNotifications();
    const showImmediately = useNotificationPreferencesStore((s) => s.showImmediately);

    if (!showImmediately && toasts.length === 0) return null;

    const getTypeColor = (type: string) => `type.${type}` as const;

    return (
        <Box
            data-testid="drop-notifications"
            position="fixed"
            top="14"
            left={LEFT_OFFSET}
            zIndex={9}
            display="flex"
            flexDirection="column"
            gap={`${TOAST_STACK_GAP}px`}
            width="350px"
            pointerEvents="none"
        >
            {showImmediately && <NotificationPopup />}
            {toasts.map((toast) => (
                <EphemeralToastCard
                    key={toast.id}
                    toast={toast}
                    getTypeColor={getTypeColor}
                    t={t}
                />
            ))}
        </Box>
    );
}

export function NotificationBadge() {
    const { dropNotifications } = useDropNotifications();
    const count = dropNotifications.filter((n) => !n.read).length;
    if (count === 0) return null;
    return (
        <Float placement="top-end" offsetX="1" offsetY="1">
            <Badge
                minW="14px"
                minH="14px"
                p="0"
                borderRadius="full"
                bg="active"
                color="bg"
                fontSize="8px"
                fontWeight="bold"
                display="flex"
                alignItems="center"
                justifyContent="center"
                lineHeight="1"
                pointerEvents="none"
                boxShadow="0 1px 4px rgba(0,0,0,0.5)"
            >
                {count > 99 ? "99+" : count}
            </Badge>
        </Float>
    );
}

/** Mounted only when showImmediately is enabled — contains all popup state and rendering. */
function NotificationPopup() {
    const { t } = useTranslation();
    const { dropNotifications, markAsRead, openNotificationPanel } = useDropNotifications();
    const togglePanel = useMapbarStore((s) => s.togglePanel);
    const navigate = useNavigate();
    const location = useLocation();
    const { flyTo, setViewState } = useViewActions();

    const isOnMapPage = location.pathname === "/map";

    const [current, setCurrent] = useState<DropNotificationType | null>(null);
    const [currentId, setCurrentId] = useState<string | null>(null);
    const [isVisible, setIsVisible] = useState(false);
    const [isDismissing, setIsDismissing] = useState(false);
    const [dismissedIds, setDismissedIds] = useState<Set<string>>(() => new Set());
    const [expiresAt, setExpiresAt] = useState(0);

    const navigateToNotification = useCallback(
        (notificationId: string) => {
            if (isOnMapPage) {
                openNotificationPanel(notificationId, togglePanel);
            } else {
                openNotificationPanel(notificationId);
                navigate("/map");
            }
        },
        [isOnMapPage, openNotificationPanel, togglePanel, navigate],
    );

    const handleClose = useCallback(
        (shouldOpenPanel: boolean, id?: string) => {
            setCurrent((prev) => {
                if (prev) {
                    setDismissedIds((prevIds) => new Set(prevIds).add(prev.id));
                }
                return prev;
            });

            setIsDismissing(true);

            if (shouldOpenPanel && id) {
                navigateToNotification(id);
            }

            setTimeout(() => {
                setCurrentId(null);
                setIsVisible(false);
                setIsDismissing(false);
                setCurrent(null);
            }, 300);
        },
        [navigateToNotification],
    );

    const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    // TODO: refactor to derive state instead of syncing with useEffect
    /* eslint-disable react-hooks/set-state-in-effect, @eslint-react/hooks-extra/no-direct-set-state-in-use-effect */
    useEffect(() => {
        if (currentId) {
            const updatedCurrent = dropNotifications.find((n) => n.id === currentId);
            if (updatedCurrent) {
                setCurrent((prev) => {
                    if (prev && updatedCurrent !== prev) {
                        return updatedCurrent;
                    }
                    return prev;
                });
            }
            return;
        }

        const alertNotification = dropNotifications.find((n) => !n.read && !dismissedIds.has(n.id));

        if (alertNotification) {
            setCurrentId(alertNotification.id);
            setCurrent(alertNotification);
            setIsVisible(true);
            setIsDismissing(false);

            if (timerRef.current) clearTimeout(timerRef.current);
            setExpiresAt(Date.now() + NOTIFICATION_DISMISS_MS);
            timerRef.current = setTimeout(() => {
                timerRef.current = null;
                handleClose(false);
            }, NOTIFICATION_DISMISS_MS);
        }
    }, [dropNotifications, handleClose, dismissedIds, currentId]);
    /* eslint-enable react-hooks/set-state-in-effect, @eslint-react/hooks-extra/no-direct-set-state-in-use-effect */

    useEffect(() => {
        return () => {
            if (timerRef.current) clearTimeout(timerRef.current);
        };
    }, []);

    const handleMarkAsRead = () => {
        if (current) {
            markAsRead(current.id);
            handleClose(false);
        }
    };

    const handlePingClick = (ping: MapPing | undefined) => {
        if (!ping) return;
        const view = {
            latitude: ping.center[1],
            longitude: ping.center[0],
            zoom: ping.zoom,
            pitch: ping.pitch,
            bearing: ping.bearing,
        };
        if (isOnMapPage) {
            flyTo(view);
        } else {
            navigate("/map");
            setViewState(view);
        }
        handleClose(false);
    };

    const handleSwipe = (e: React.MouseEvent) => {
        const startX = e.clientX;
        const handleMove = (moveEvent: MouseEvent) => {
            if (startX - moveEvent.clientX > 100) {
                handleClose(false);
                document.removeEventListener("mousemove", handleMove);
            }
        };
        document.addEventListener("mousemove", handleMove);
        document.addEventListener(
            "mouseup",
            () => {
                document.removeEventListener("mousemove", handleMove);
            },
            { once: true },
        );
    };

    if (!current || !isVisible) return null;

    const getTypeColor = (type: string) => `type.${type}` as const;

    return (
        <NotificationCard
            notification={current}
            isDismissing={isDismissing}
            isVisible={isVisible}
            expiresAt={expiresAt}
            getTypeColor={getTypeColor}
            onSwipe={handleSwipe}
            onMarkAsRead={handleMarkAsRead}
            onClose={() => handleClose(false)}
            onViewLocation={() => {
                handlePingClick(current.mapPing);
                handleMarkAsRead();
            }}
            onViewNotification={() => navigateToNotification(current.id)}
            t={t}
        />
    );
}

function NotificationCard({
    notification,
    isDismissing,
    isVisible,
    expiresAt,
    getTypeColor,
    onSwipe,
    onMarkAsRead,
    onClose,
    onViewLocation,
    onViewNotification,
    t,
}: {
    notification: DropNotificationType;
    isDismissing: boolean;
    isVisible: boolean;
    expiresAt: number;
    getTypeColor: (type: string) => `type.${string}`;
    onSwipe: (e: React.MouseEvent) => void;
    onMarkAsRead: () => void;
    onClose: () => void;
    onViewLocation: () => void;
    onViewNotification: () => void;
    t: TFunction;
}) {
    const secondsLeft = useCountdown(expiresAt);
    const dismissSeconds = NOTIFICATION_DISMISS_MS / 1000;
    return (
        <Box
            pointerEvents="auto"
            css={{
                transition:
                    "transform 0.4s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.4s cubic-bezier(0.4, 0, 0.2, 1)",
                transform: isDismissing
                    ? "translateX(-450px) scale(0.9)"
                    : isVisible
                      ? "translateX(0) scale(1)"
                      : "translateX(100px) scale(0.95)",
                opacity: isVisible && !isDismissing ? 1 : 0,
            }}
            onMouseDown={onSwipe}
        >
            <Panel
                direction="column"
                gap="3"
                p="4"
                width="100%"
                cursor="pointer"
                position="relative"
                overflow="hidden"
                onClick={() => {
                    onMarkAsRead();
                    onViewNotification();
                }}
            >
                <Box
                    position="absolute"
                    left="0"
                    top="0"
                    bottom="0"
                    width="6px"
                    bg={getTypeColor(notification.type)}
                    color={getTypeColor(notification.type)}
                    filter="drop-shadow(-2px 0px 6px currentColor)"
                    css={{
                        clipPath: "polygon(0 0, 100% 0, 100% calc(100% - 8px), 0 100%)",
                    }}
                />

                <Flex justify="space-between" align="center">
                    <Flex align="center" gap="2" flex="1">
                        <Box
                            width="8px"
                            height="8px"
                            borderRadius="full"
                            flexShrink={0}
                            bg={getTypeColor(notification.type)}
                            color={getTypeColor(notification.type)}
                            filter="drop-shadow(0px 0px 4px currentColor)"
                        />
                        <TacticalText
                            fontWeight="bold"
                            overflow="hidden"
                            textOverflow="ellipsis"
                            whiteSpace="nowrap"
                            flex="1"
                            color="bg.contrast"
                        >
                            {notification.title}
                        </TacticalText>
                    </Flex>
                    <Flex gap="2" align="center">
                        <IconButton
                            onClick={(e) => {
                                e.stopPropagation();
                                onMarkAsRead();
                            }}
                            aria-label={t("notifications.markAsRead")}
                            size="xs"
                        >
                            <FaCheck size={10} />
                        </IconButton>
                        <IconButton
                            onClick={(e) => {
                                e.stopPropagation();
                                onClose();
                            }}
                            aria-label={t("notifications.close")}
                            size="xs"
                        >
                            <FaTimes size={10} />
                        </IconButton>
                    </Flex>
                </Flex>

                <Flex gap="2" justifyContent="space-between" alignItems="flex-end">
                    {notification.mapPing && (
                        <ActionButton
                            gap="1"
                            onClick={(e) => {
                                e.stopPropagation();
                                onViewLocation();
                            }}
                        >
                            <FaMapMarkerAlt size={10} />
                            <TacticalText>{t("notifications.viewLocation")}</TacticalText>
                        </ActionButton>
                    )}
                    <ActionButton
                        gap="1"
                        onClick={(e) => {
                            e.stopPropagation();
                            onViewNotification();
                        }}
                    >
                        <FaBell size={10} />
                        <TacticalText>{t("notifications.viewNotification")}</TacticalText>
                    </ActionButton>
                    <TacticalText opacity={0.7} color="fg.muted">
                        {formatTimestamp(notification.timestamp, t)}
                    </TacticalText>
                </Flex>

                <Box position="absolute" bottom="0" left="0" right="0" height="2px" bg="bg.muted">
                    <Box
                        height="100%"
                        bg={getTypeColor(notification.type)}
                        css={{
                            transition: "width 1s linear",
                            width: `${(secondsLeft / dismissSeconds) * 100}%`,
                        }}
                    />
                </Box>
            </Panel>
        </Box>
    );
}

function useCountdown(expiresAt: number): number {
    const [remaining, setRemaining] = useState(() =>
        Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000)),
    );

    useEffect(() => {
        const interval = setInterval(() => {
            const secs = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));
            setRemaining(secs);
            if (secs <= 0) clearInterval(interval);
        }, 1000);
        return () => clearInterval(interval);
    }, [expiresAt]);

    return remaining;
}

function EphemeralToastCard({
    toast,
    getTypeColor,
    t,
}: {
    toast: EphemeralToast;
    getTypeColor: (type: string) => `type.${string}`;
    t: TFunction;
}) {
    const secondsLeft = useCountdown(toast.expiresAt);
    const dismissSeconds = TOAST_DISMISS_MS / 1000;

    return (
        <Box
            pointerEvents="auto"
            css={{
                transition: "transform 0.3s ease-out, opacity 0.3s ease-out",
                transform: "translateX(0)",
                opacity: 1,
                animation: "slideInFromLeft 0.3s ease-out",
            }}
        >
            <Panel
                direction="column"
                gap="2"
                p="3"
                width="100%"
                position="relative"
                overflow="hidden"
            >
                <Box
                    position="absolute"
                    left="0"
                    top="0"
                    bottom="0"
                    width="6px"
                    bg={getTypeColor(toast.type)}
                    color={getTypeColor(toast.type)}
                    filter="drop-shadow(-2px 0px 6px currentColor)"
                    css={{
                        clipPath: "polygon(0 0, 100% 0, 100% calc(100% - 8px), 0 100%)",
                    }}
                />

                <Flex align="center" gap="2">
                    <Box
                        width="8px"
                        height="8px"
                        borderRadius="full"
                        flexShrink={0}
                        bg={getTypeColor(toast.type)}
                        color={getTypeColor(toast.type)}
                        filter="drop-shadow(0px 0px 4px currentColor)"
                    />
                    <TacticalText
                        fontWeight="bold"
                        overflow="hidden"
                        textOverflow="ellipsis"
                        whiteSpace="nowrap"
                        flex="1"
                        color="bg.contrast"
                    >
                        {toast.title}
                    </TacticalText>
                    <TacticalText fontSize="xs" color="fg.muted" flexShrink={0}>
                        {t("notifications.secondsLeft", { seconds: secondsLeft })}
                    </TacticalText>
                </Flex>

                {toast.description && (
                    <TacticalText fontSize="xs" color="fg.muted" pl="5">
                        {toast.description}
                    </TacticalText>
                )}

                {toast.content}

                <Box position="absolute" bottom="0" left="0" right="0" height="2px" bg="bg.muted">
                    <Box
                        height="100%"
                        bg={getTypeColor(toast.type)}
                        css={{
                            transition: "width 1s linear",
                            width: `${(secondsLeft / dismissSeconds) * 100}%`,
                        }}
                    />
                </Box>
            </Panel>
        </Box>
    );
}

function formatTimestamp(date: Date, t: TFunction): string {
    const diff = Date.now() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return t("notifications.justNow");
    if (minutes < 60) return t("notifications.minutesAgo", { minutes });
    if (hours < 24) return t("notifications.hoursAgo", { hours });
    return t("notifications.daysAgo", { days });
}
