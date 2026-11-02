// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Badge, HStack } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import type { WatchboxAlertingStatus } from "./watchbox.model";

/** Spelled out rather than interpolated: a template-literal key is invisible to the extractor. */
const statusKey = {
    ACTIVE: "watchbox.statuses.ACTIVE",
    DEGRADED: "watchbox.statuses.DEGRADED",
    FAILED: "watchbox.statuses.FAILED",
    DISABLED: "watchbox.statuses.DISABLED",
} as const satisfies Record<WatchboxAlertingStatus, string>;

const statusPalette = {
    ACTIVE: "green",
    DEGRADED: "orange",
    FAILED: "red",
    DISABLED: "gray",
} as const satisfies Record<WatchboxAlertingStatus, string>;

export type WatchboxStatusBadgesProps = {
    enabled: boolean;
    alertingStatus: WatchboxAlertingStatus;
};

/**
 * The enabled/health badge pair. The two are independent: a watchbox can be
 * enabled but DEGRADED, so both are always shown rather than collapsed into
 * one indicator.
 */
export function WatchboxStatusBadges({ enabled, alertingStatus }: WatchboxStatusBadgesProps) {
    const { t } = useTranslation();

    return (
        <HStack as="span" gap="1">
            <Badge size="xs" variant="subtle" colorPalette={enabled ? "green" : "gray"}>
                {enabled ? t("watchbox.enabled") : t("watchbox.disabled")}
            </Badge>
            <Badge size="xs" variant="subtle" colorPalette={statusPalette[alertingStatus]}>
                {t(statusKey[alertingStatus])}
            </Badge>
        </HStack>
    );
}
