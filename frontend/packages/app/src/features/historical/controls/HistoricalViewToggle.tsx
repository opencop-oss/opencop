// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Flex } from "@chakra-ui/react";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { FaSquare } from "react-icons/fa";
import { LuActivity, LuHistory } from "react-icons/lu";
import { Tooltip } from "@opencop/ui";
import { useConnectionStatus } from "~/features/websocket/use-connection-status.hook";
import { useIsDrawing, useIsEditing } from "~/features/map/interaction-runtime/session/selectors";
import { useHistoricalActions, useHistoricalMode } from "../data-plane/historical.store";

export function HistoricalViewToggle(): ReactElement {
    const { t } = useTranslation();
    const mode = useHistoricalMode();
    const { enterHistorical, exitHistorical } = useHistoricalActions();
    const isDrawing = useIsDrawing();
    const isEditing = useIsEditing();
    const { status, reconnectAttempt, maxAttempts } = useConnectionStatus();

    const historyLabel = t("historical.labelHistory");
    const liveLabel = t("historical.labelLive");

    const isHistorical = mode === "historical";
    // Block switching modes while a custody draw or a custody edit is active.
    // Drawing into the live store has no meaning over a historical snapshot,
    // and an in-progress edit holds unsaved geometry — the user must finish or
    // cancel (Esc) first. (Idle and custody-less sessions — a measurement, a
    // lasso, a watchbox polygon — switch freely: a session-local product is
    // dropped by the reset.)
    const isDisabled = isDrawing || isEditing;
    const disabledReason = t(
        "historical.modeToggleDrawActive",
        "Finish or cancel your active drawing or edit first",
    );

    const iconColor = isHistorical ? "gray.500" : `connection.${status}`;

    const connectionLabel = isHistorical
        ? t("historical.connectionPaused", "Live feed paused")
        : status === "connected"
          ? t("connection.active")
          : status === "connecting"
            ? t("connection.connecting")
            : status === "reconnecting"
              ? t("connection.reconnecting", { attempt: reconnectAttempt, max: maxAttempts })
              : status === "disconnected"
                ? t("connection.disconnected")
                : status === "failed"
                  ? t("connection.failed")
                  : t("connection.notConnected");

    return (
        <Flex h="full" gap="0" alignItems="center">
            {/* The wrapping span receives the hover events the disabled button
            blocks, so the "finish your drawing first" reason still surfaces. */}
            <Tooltip content={disabledReason} disabled={!isDisabled} uppercase={false}>
                <Box as="span" display="inline-flex">
                    <Button
                        size="2xs"
                        variant="plain"
                        borderRadius="sm"
                        px="2"
                        my="1"
                        fontSize="2xs"
                        fontFamily="mono"
                        fontWeight="normal"
                        bg={isHistorical ? "bg.subtle" : "transparent"}
                        color={isHistorical ? "fg" : "fg.muted"}
                        onClick={enterHistorical}
                        disabled={isDisabled}
                        aria-pressed={isHistorical}
                        aria-label={historyLabel}
                    >
                        <Tooltip content={historyLabel} uppercase={false}>
                            <Box
                                as={LuHistory}
                                display={{ base: "block", lg: "none" }}
                                boxSize="3.5"
                                flexShrink={0}
                            />
                        </Tooltip>
                        <Box as="span" display={{ base: "none", lg: "inline" }}>
                            {historyLabel}
                        </Box>
                    </Button>
                </Box>
            </Tooltip>
            <Tooltip content={disabledReason} disabled={!isDisabled} uppercase={false}>
                <Box as="span" display="inline-flex">
                    <Button
                        size="2xs"
                        variant="plain"
                        borderRadius="sm"
                        px="2"
                        my="1"
                        fontSize="2xs"
                        fontFamily="mono"
                        fontWeight="normal"
                        bg={!isHistorical ? "bg.subtle" : "transparent"}
                        color={!isHistorical ? "fg" : "fg.muted"}
                        onClick={exitHistorical}
                        disabled={isDisabled}
                        aria-pressed={!isHistorical}
                        aria-label={liveLabel}
                        gap="1"
                    >
                        <Tooltip content={liveLabel} uppercase={false}>
                            <Box
                                as={LuActivity}
                                display={{ base: "block", lg: "none" }}
                                boxSize="3.5"
                                flexShrink={0}
                            />
                        </Tooltip>
                        <Box as="span" display={{ base: "none", lg: "inline" }}>
                            {liveLabel}
                        </Box>
                        <Tooltip content={connectionLabel}>
                            <Box as={FaSquare} boxSize="6px" color={iconColor} />
                        </Tooltip>
                    </Button>
                </Box>
            </Tooltip>
        </Flex>
    );
}
