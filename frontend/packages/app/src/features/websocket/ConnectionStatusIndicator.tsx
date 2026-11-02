// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Text } from "@chakra-ui/react";
import { FaSquare } from "react-icons/fa";
import { useTranslation } from "react-i18next";
import { Tooltip } from "@opencop/ui";
import {
    INTEGRATION_UNAVAILABLE,
    type ConnectionState,
    type ConnectionStateReason,
} from "@opencop/api";

// TODO add colors to theme
const STATUS_MAP = {
    CONNECTED: { color: "#789D7A", tooltipKey: "connection.active" },
    CONNECTING: { color: "#D4A35D", tooltipKey: "connection.connecting" },
    FAILED: { color: "#d45d5d", tooltipKey: "connection.failed" },
    DISCONNECTED: { color: "#d45d5d", tooltipKey: "connection.disconnected" },
    STOPPED: { color: "#d45d5d", tooltipKey: "connection.stopped" },
    NEW: { color: "var(--chakra-colors-gray-500)", tooltipKey: "connection.notStarted" },
} as const;

const DEFAULT_STATUS = {
    color: "var(--chakra-colors-gray-500)",
    tooltipKey: "connection.unknown",
} as const;

function getStatusInfo(state: ConnectionState) {
    if (state in STATUS_MAP) {
        return STATUS_MAP[state as keyof typeof STATUS_MAP];
    }
    return DEFAULT_STATUS;
}

/** Tooltips for the bounded reasons Core reports beside a `FAILED` state. */
const REASON_TOOLTIP_KEYS = {
    [INTEGRATION_UNAVAILABLE]: "connection.integrationUnavailable",
} as const;

interface ConnectionStatusIndicatorProps {
    state: ConnectionState;
    /** Why the state is `FAILED`, when Core reported a reason; replaces the generic tooltip. */
    reason?: ConnectionStateReason | null;
    /** Optional size for the circle icon (default: 8) */
    size?: number;
    /** Whether to show the status text label (default: true) */
    showLabel?: boolean;
    /** Font size for the label (default: "sm") */
    labelSize?: "xs" | "sm" | "md";
}

export function ConnectionStatusIndicator({
    state,
    reason,
    size = 8,
    showLabel = true,
    labelSize = "sm",
}: ConnectionStatusIndicatorProps) {
    const { t } = useTranslation();
    const { color, tooltipKey } = getStatusInfo(state);
    // Core sends a reason only beside FAILED; a stale reason must not relabel another state.
    const reasonKey =
        state === "FAILED" && reason && Object.hasOwn(REASON_TOOLTIP_KEYS, reason)
            ? REASON_TOOLTIP_KEYS[reason as keyof typeof REASON_TOOLTIP_KEYS]
            : undefined;

    return (
        <Tooltip content={t(reasonKey ?? tooltipKey)}>
            <Flex alignItems="center" gap={2} cursor="default">
                <FaSquare size={size} color={color} />
                {showLabel && (
                    <Text fontSize={labelSize} color={color}>
                        {state || "UNKNOWN"}
                    </Text>
                )}
            </Flex>
        </Tooltip>
    );
}
