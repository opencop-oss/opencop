// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Portal, Text } from "@chakra-ui/react";
import { DateTime } from "luxon";
import { useLayoutEffect, useRef, type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import type { HistogramHoverInfo } from "./histogram.types.ts";

const countFormatter = new Intl.NumberFormat(undefined, { useGrouping: true });

function formatTooltipTime(iso: string, zone: string): string {
    return DateTime.fromISO(iso, { zone }).toFormat("MMM d, yyyy  HH:mm:ss");
}

function formatDuration(startIso: string, endIso: string): string {
    const start = DateTime.fromISO(startIso);
    const end = DateTime.fromISO(endIso);
    const diff = end.diff(start, ["days", "hours", "minutes", "seconds"]);

    const parts: string[] = [];
    if (diff.days > 0) parts.push(`${diff.days}d`);
    if (diff.hours > 0) parts.push(`${diff.hours}h`);
    if (diff.minutes > 0) parts.push(`${diff.minutes}m`);
    if (parts.length === 0 && diff.seconds > 0) parts.push(`${Math.round(diff.seconds)}s`);
    return parts.length > 0 ? parts.join(" ") : "< 1s";
}

interface HistogramTooltipProps {
    hoverInfo: HistogramHoverInfo;
    /** Cursor position in viewport coordinates — drives the clamped placement. */
    mouseClient: { x: number; y: number };
    timezone: string;
}

/**
 * The histogram's cursor-following hover tooltip. Portaled to the body (fixed
 * position) so the panel's bounds don't clip it where the histogram meets the
 * map, then clamped horizontally against the window edges and flipped below the
 * cursor when there's no room above. Renders one row per source — swatch, name,
 * unique-event count, and activity (UPDATED) at the hovered bucket.
 */
export function HistogramTooltip({
    hoverInfo,
    mouseClient,
    timezone,
}: HistogramTooltipProps): ReactElement {
    const { t } = useTranslation();
    const tooltipRef = useRef<HTMLDivElement>(null);

    useLayoutEffect(() => {
        const tip = tooltipRef.current;
        if (!tip) return;
        const { width: tw, height: th } = tip.getBoundingClientRect();
        const M = 8;
        const left = Math.max(M, Math.min(mouseClient.x - tw / 2, window.innerWidth - tw - M));
        const above = mouseClient.y - 10 - th;
        const top = above >= M ? above : mouseClient.y + 14;
        tip.style.left = `${left}px`;
        tip.style.top = `${top}px`;
    }, [hoverInfo, mouseClient]);

    return (
        <Portal>
            <Box
                ref={tooltipRef}
                position="fixed"
                // Real position is set in a layout effect (clamped to the
                // viewport); these are just the pre-measure defaults.
                top="0"
                left="0"
                bg="gray.800/80"
                color="white"
                fontSize="xs"
                px="2.5"
                py="1.5"
                borderRadius="sm"
                whiteSpace="nowrap"
                pointerEvents="none"
                boxShadow="md"
                zIndex="tooltip"
                lineHeight="1.5"
            >
                <Text color="gray.400" fontSize="xs">
                    {formatTooltipTime(hoverInfo.start, timezone)}
                    {" → "}
                    {formatTooltipTime(hoverInfo.end, timezone)}
                    {"  ("}
                    {formatDuration(hoverInfo.start, hoverInfo.end)}
                    {")"}
                </Text>
                <Box display="flex" flexDir="column" gap="0.5" mt="1">
                    <Box display="flex" gap="2" color="gray.400" fontSize="2xs">
                        <Box flex="1" />
                        <Text minW="3rem" textAlign="right">
                            {t("historical.tooltipEvents", "Active events")}
                        </Text>
                        <Text minW="3rem" textAlign="right">
                            {t("historical.tooltipActivity", "Updates")}
                        </Text>
                    </Box>
                    {hoverInfo.perSource.map((row) => (
                        <Box key={row.sourceId} display="flex" alignItems="center" gap="2">
                            <Box w="10px" h="10px" flexShrink={0} bg={row.color} />
                            <Text flex="1" truncate maxW="11rem">
                                {row.name}
                            </Text>
                            <Text
                                minW="3rem"
                                textAlign="right"
                                fontWeight="semibold"
                                color={row.color}
                            >
                                {countFormatter.format(row.count)}
                            </Text>
                            <Text minW="3rem" textAlign="right" color={row.color}>
                                {countFormatter.format(row.activity)}
                            </Text>
                        </Box>
                    ))}
                </Box>
            </Box>
        </Portal>
    );
}
