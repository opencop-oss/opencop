// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Box,
    Button,
    Card,
    HStack,
    Icon,
    IconButton,
    Menu,
    Portal,
    Text,
    type CardRootProps,
} from "@chakra-ui/react";
import { OcSwitch } from "@opencop/ui";
import { FaBellSlash } from "react-icons/fa";
import { FaEllipsisVertical } from "react-icons/fa6";
import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import type { Watchbox, WatchboxAlertingStatus } from "./watchbox.model";

/** Spelled out rather than interpolated: a template-literal key is invisible to the extractor. */
const statusKey = {
    ACTIVE: "watchbox.statuses.ACTIVE",
    DEGRADED: "watchbox.statuses.DEGRADED",
    FAILED: "watchbox.statuses.FAILED",
    DISABLED: "watchbox.statuses.DISABLED",
} as const satisfies Record<WatchboxAlertingStatus, string>;

// ACTIVE is only rendered for read-only viewers (see showStatusLabel below), but the map stays
// exhaustive so a new WatchboxAlertingStatus can't be added without TypeScript flagging a
// missing color here.
const statusTextColor = {
    ACTIVE: "fg.muted",
    DEGRADED: "type.warning",
    FAILED: "type.error",
    DISABLED: "fg.muted",
} as const satisfies Record<WatchboxAlertingStatus, string>;

export type WatchboxCardProps = {
    watchbox: Watchbox;
    onDeleteClick: () => void;
    onToggleEnabled: (enabled: boolean) => void;
    /** Whether this user has muted alert toasts for this watchbox — a personal
     *  preference, independent of server-side capabilities. */
    isMuted: boolean;
    onToggleMuted: (muted: boolean) => void;
    togglePending?: boolean;
    /** Opens this watchbox's detail view. */
    open: () => void;
} & CardRootProps;

export function WatchboxCard({
    watchbox,
    onDeleteClick,
    onToggleEnabled,
    isMuted,
    onToggleMuted,
    togglePending = false,
    open,
    ...rest
}: WatchboxCardProps) {
    const { t } = useTranslation();
    const switchRef = useRef<HTMLInputElement>(null);
    const wasTogglePendingRef = useRef(togglePending);
    useEffect(() => {
        // Chromium blurs a focused control as soon as it becomes disabled. This switch survives
        // the patch, so restore focus to the control the user activated when the request settles.
        if (wasTogglePendingRef.current && !togglePending) switchRef.current?.focus();
        wasTogglePendingRef.current = togglePending;
    }, [togglePending]);
    const canPatch = watchbox.capabilities?.canPatch ?? false;
    // The switch already says active/disabled by its position, so those labels would just repeat
    // it when canPatch. Without the switch, the label is the only visible status indicator;
    // DEGRADED/FAILED are operational-health states the switch can't express, so they always show.
    const showStatusLabel =
        !canPatch ||
        (watchbox.alertingStatus !== "ACTIVE" && watchbox.alertingStatus !== "DISABLED");
    // Preserve the status context main added to the detail opener without duplicating visible
    // labels beside the switch. Static rows expose the same information in their accessible name.
    const openLabel = canPatch
        ? undefined
        : [
              watchbox.name,
              t(watchbox.enabled ? "watchbox.enabled" : "watchbox.disabled"),
              t(statusKey[watchbox.alertingStatus]),
          ].join(" ");
    const muteLabel = t(isMuted ? "watchbox.unmute" : "watchbox.mute");

    return (
        <Card.Root
            size="sm"
            w="full"
            bg="bg.elevated"
            borderWidth="1px"
            borderColor="border"
            borderRadius="sm"
            boxShadow="none"
            cursor="pointer"
            _hover={{ borderColor: "active" }}
            onClick={open}
            {...rest}
        >
            <Card.Body>
                <HStack justify="space-between" gap="2">
                    {/* The name stays a real Button (rather than the row's onClick alone) so
                        keyboard and screen-reader users keep a focusable, labeled control;
                        the switch and actions menu stay siblings rather than nesting inside
                        it. Each stops propagation so it doesn't also re-trigger the row's
                        onClick. */}
                    <Button
                        variant="plain"
                        flex="1"
                        minW={0}
                        h="auto"
                        p={0}
                        justifyContent="flex-start"
                        fontWeight="normal"
                        aria-label={openLabel}
                        onClick={(e) => {
                            e.stopPropagation();
                            open();
                        }}
                    >
                        <Text as="span" truncate color="bg.contrast">
                            {watchbox.name}
                        </Text>
                    </Button>
                    {showStatusLabel && (
                        <Text fontSize="xs" color={statusTextColor[watchbox.alertingStatus]}>
                            {t(statusKey[watchbox.alertingStatus])}
                        </Text>
                    )}
                    {canPatch && (
                        <Box onClick={(e) => e.stopPropagation()}>
                            <OcSwitch
                                ref={switchRef}
                                checked={watchbox.enabled}
                                label={t("watchbox.toggle", { name: watchbox.name })}
                                srOnlyLabel
                                onCheckedChange={onToggleEnabled}
                                disabled={togglePending}
                                fullWidth={false}
                                rootProps={{ size: "sm" }}
                            />
                        </Box>
                    )}
                    {/* Wrapped like the switch above so opening the menu or picking an
                        item doesn't also trigger the row's onClick. Mute is never gated
                        on capabilities (a personal, client-only preference), so this menu
                        is always offered even when Delete — a server-mutating action — is
                        hidden. */}
                    <Box onClick={(e) => e.stopPropagation()}>
                        <Menu.Root>
                            <Menu.Trigger asChild>
                                <IconButton
                                    size="2xs"
                                    variant="ghost"
                                    aria-label={t("watchbox.moreActions", {
                                        name: watchbox.name,
                                    })}
                                >
                                    <FaEllipsisVertical />
                                </IconButton>
                            </Menu.Trigger>
                            <Portal>
                                <Menu.Positioner>
                                    <Menu.Content>
                                        {/* A plain Menu.Item with a swapping label ("Mute
                                            alerts"/"Unmute alerts"), not Menu.CheckboxItem: the
                                            label already conveys the current state to a screen
                                            reader, and this matches WatchboxDetail.tsx's menu. */}
                                        <Menu.Item
                                            value="mute"
                                            onClick={() => onToggleMuted(!isMuted)}
                                        >
                                            {muteLabel}
                                        </Menu.Item>
                                        {watchbox.capabilities?.canDelete && (
                                            <Menu.Item
                                                value="delete"
                                                color="type.error"
                                                onClick={onDeleteClick}
                                            >
                                                {t("watchbox.delete")}
                                            </Menu.Item>
                                        )}
                                    </Menu.Content>
                                </Menu.Positioner>
                            </Portal>
                        </Menu.Root>
                    </Box>
                </HStack>
                <Card.Description>
                    {/* Leads the description line, not the switch/menu row: sitting beside
                        those controls read as if it labeled the switch, since the switch
                        itself carries no visible label (srOnlyLabel). */}
                    {isMuted && (
                        <Text as="span" color="fg.muted">
                            <Icon
                                as={FaBellSlash}
                                aria-hidden
                                focusable={false}
                                color="fg.muted"
                                boxSize="3"
                                verticalAlign="-0.125em"
                                mr="1"
                            />
                            {t("watchbox.muted")}
                            {watchbox.description && " — "}
                        </Text>
                    )}
                    {watchbox.description}
                </Card.Description>
            </Card.Body>
        </Card.Root>
    );
}
