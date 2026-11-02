// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Text, Box, VStack, VisuallyHidden } from "@chakra-ui/react";
import {
    FaBell,
    FaCheckDouble,
    FaSquare,
    FaLayerGroup,
    FaEnvelope,
    FaEnvelopeOpen,
} from "react-icons/fa";
import {
    useDropNotifications,
    type MapPing,
} from "~/features/notifications/DropNotificationsContext";
import { IconButton, scrollbarStyles, AccentHeading, TacticalText, Tooltip } from "@opencop/ui";
import { useNotificationPreferencesStore } from "./notification-preferences.store";
import { Link } from "react-router";
import { NotificationType } from "@opencop/api";
import { useTranslation } from "react-i18next";
import { useViewActions } from "../map/core/slices/view/view.hook";
import { useMap } from "react-map-gl/maplibre";

export function NotificationList() {
    const { t } = useTranslation();
    const mapRef = useMap();
    const { flyTo } = useViewActions();

    function handlePingClick(ping: MapPing | undefined): void {
        if (!ping || !mapRef.current?.getMap()) return;
        flyTo({
            pitch: ping.pitch,
            bearing: ping.bearing,
            zoom: ping.zoom,
            center: ping.center,
        });
    }

    const { dropNotifications, markAsRead, markAllAsRead, notificationRefs } =
        useDropNotifications();
    const showImmediately = useNotificationPreferencesStore((s) => s.showImmediately);
    const { toggleShowImmediately } = useNotificationPreferencesStore((s) => s.actions);

    const getTypeColor = (type: string) => {
        return `type.${type}` as const;
    };

    const formatTime = (timestamp: Date) => {
        const now = new Date();
        const diff = now.getTime() - timestamp.getTime();
        const minutes = Math.floor(diff / 60000);
        const hours = Math.floor(minutes / 60);
        const days = Math.floor(hours / 24);

        if (minutes < 1) return t("notifications.justNow");
        if (minutes < 60) return t("notifications.minutesAgo", { minutes });
        if (hours < 24) return t("notifications.hoursAgo", { hours });
        return t("notifications.daysAgo", { days });
    };

    return (
        <>
            <VStack gap="4" width="100%" align="stretch">
                <Flex gap="2" align="center" justify="space-between">
                    <AccentHeading>{t("notifications.title")}</AccentHeading>
                    <Flex gap="3" align="center">
                        <Tooltip content={t("notifications.showImmediately")}>
                            <IconButton
                                onClick={toggleShowImmediately}
                                aria-label={t("notifications.showImmediately")}
                                color={showImmediately ? "active" : undefined}
                                filter={
                                    showImmediately
                                        ? "drop-shadow(1px 1px 4px currentColor)"
                                        : undefined
                                }
                            >
                                <FaBell size={14} />
                            </IconButton>
                        </Tooltip>
                        {dropNotifications.length > 0 && (
                            <Tooltip content={t("notifications.markAllAsRead")}>
                                <IconButton
                                    onClick={markAllAsRead}
                                    aria-label={t("notifications.markAllAsRead")}
                                >
                                    <FaCheckDouble size={16} />
                                </IconButton>
                            </Tooltip>
                        )}
                    </Flex>
                </Flex>

                <Flex
                    direction="column"
                    overflowY="auto"
                    flex="1"
                    minHeight="0"
                    css={scrollbarStyles}
                >
                    {dropNotifications.length === 0 ? (
                        <Flex
                            p="2"
                            direction="column"
                            alignItems="center"
                            justifyContent="space-between"
                            gap="1"
                        >
                            <Text fontSize="md" fontWeight="500">
                                {t("notifications.noNotifications")}
                            </Text>
                            <Text fontSize="sm">{t("notifications.allCaughtUp")}</Text>
                        </Flex>
                    ) : (
                        dropNotifications.map((dropNotification, index) => {
                            // Unread notifications get a much more prominent background color
                            /* eslint-disable i18next/no-literal-string */
                            const bgColor = !dropNotification.read
                                ? "accent.900"
                                : index % 2 === 0
                                  ? "bg.elevated"
                                  : "bg.elevatedLight";
                            /* eslint-enable i18next/no-literal-string */

                            return (
                                <Flex
                                    key={dropNotification.id}
                                    ref={(el) => {
                                        if (el) {
                                            notificationRefs.current.set(dropNotification.id, el);
                                        } else {
                                            notificationRefs.current.delete(dropNotification.id);
                                        }
                                    }}
                                    cursor="pointer"
                                    direction="column"
                                    position="relative"
                                    py="1.5"
                                    px="3"
                                    width="100%"
                                    bg={bgColor}
                                    opacity={dropNotification.read ? 0.75 : 1}
                                    _hover={{
                                        bg: "whiteAlpha.200",
                                    }}
                                    transition="background-color 0.2s, opacity 0.2s"
                                    onClick={() => markAsRead(dropNotification.id)}
                                    borderBottom="1px solid"
                                    borderColor="border"
                                    role="article"
                                    aria-label={
                                        !dropNotification.read
                                            ? t("notifications.unreadNotification")
                                            : t("notifications.readNotification")
                                    }
                                >
                                    {/* Hidden status text for screen readers */}
                                    <VisuallyHidden>
                                        {!dropNotification.read
                                            ? t("notifications.unread")
                                            : t("notifications.read")}
                                    </VisuallyHidden>

                                    {/* Type indicator dot (left side) */}
                                    <Box
                                        position="absolute"
                                        top="10px"
                                        left="3"
                                        width="6px"
                                        height="6px"
                                        bg={getTypeColor(dropNotification.type)}
                                        color={getTypeColor(dropNotification.type)}
                                        filter="drop-shadow(0px 0px 4px currentColor)"
                                    >
                                        <FaSquare size={6} />
                                    </Box>

                                    {/* Read/Unread indicator icon (right side) */}
                                    <Box
                                        position="absolute"
                                        top="8px"
                                        right="3"
                                        color={dropNotification.read ? "fg.muted" : "active"}
                                        filter={
                                            dropNotification.read
                                                ? undefined
                                                : "drop-shadow(0px 0px 4px currentColor)"
                                        }
                                        aria-hidden="true"
                                    >
                                        {dropNotification.read ? (
                                            <FaEnvelopeOpen size={12} />
                                        ) : (
                                            <FaEnvelope size={12} />
                                        )}
                                    </Box>

                                    <Flex
                                        direction="row"
                                        justifyContent="space-between"
                                        align="center"
                                        mb="1"
                                    >
                                        <Text
                                            textStyle="sm"
                                            fontWeight={!dropNotification.read ? "bold" : "normal"}
                                            ml="4"
                                            color="bg.contrast"
                                        >
                                            {dropNotification.title}
                                        </Text>
                                    </Flex>

                                    <Text
                                        fontSize="xs"
                                        fontWeight="normal"
                                        ml="4"
                                        mb="1"
                                        color="bg.contrast"
                                        lineHeight="1.5"
                                    >
                                        {dropNotification.message}
                                    </Text>

                                    <Flex
                                        direction="row"
                                        justifyContent="space-between"
                                        alignItems="center"
                                        ml="4"
                                        mt="1"
                                    >
                                        <TacticalText
                                            fontWeight="normal"
                                            opacity={0.7}
                                            color="fg.muted"
                                        >
                                            {formatTime(dropNotification.timestamp)}
                                        </TacticalText>

                                        {(dropNotification.notificationType ===
                                            NotificationType.LAYER_ACCESS ||
                                            dropNotification.notificationType ===
                                                NotificationType.LAYER_SOURCE) && (
                                            <Link to="/layers" style={{ textDecoration: "none" }}>
                                                <TacticalText
                                                    fontWeight="bold"
                                                    color="active"
                                                    cursor="pointer"
                                                    _hover={{ textDecoration: "underline" }}
                                                    display="flex"
                                                    alignItems="center"
                                                    gap="1"
                                                >
                                                    <FaLayerGroup size={10} />
                                                    {t("notifications.viewLayers")}
                                                </TacticalText>
                                            </Link>
                                        )}
                                    </Flex>

                                    {dropNotification.mapPing && (
                                        <TacticalText
                                            fontWeight="bold"
                                            ml="4"
                                            mt="1"
                                            color="active"
                                            cursor="pointer"
                                            _hover={{ textDecoration: "underline" }}
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                handlePingClick(dropNotification.mapPing);
                                            }}
                                        >
                                            {t("notifications.viewPing", {
                                                lat: dropNotification.mapPing.center[0],
                                                lon: dropNotification.mapPing.center[1],
                                            })}{" "}
                                            →
                                        </TacticalText>
                                    )}
                                </Flex>
                            );
                        })
                    )}
                </Flex>
            </VStack>
        </>
    );
}
