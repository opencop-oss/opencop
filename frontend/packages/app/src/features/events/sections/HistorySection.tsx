// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Text, Icon, Spinner, VStack, IconButton, Box, Popover } from "@chakra-ui/react";
import { useState, useMemo, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { FaSortAmountDown, FaSortAmountUp } from "react-icons/fa";
import { LuSettings2 } from "react-icons/lu";
import { useQuery } from "@tanstack/react-query";
import { eventQueries } from "../events.queries";
import { ApiError } from "@opencop/api";
import { AccentHeading, SettingsPopover, Tooltip } from "@opencop/ui";
import {
    transformFeaturesToHistory,
    extractTripWaypoints,
    calculateAdaptiveGapThresholds,
} from "./history.util";
import { HistoryControls } from "./HistoryControls";
import { HistoryTimeline } from "./HistoryTimeline";
import { TripControlsContent, GapDetectionSettings } from "./TripControls";
import { toMilliseconds } from "~/date.util";
import { useTrip } from "./TripContext";
import { useEventDetails } from "../use-event-details.hook";

type SortOrder = "newest" | "oldest";

interface HistoryParams {
    fromDate: string;
    toDate: string;
    maxPoints: number;
}

export function HistorySection() {
    const { t } = useTranslation();
    const { details } = useEventDetails();
    const trip = useTrip();
    const { gapThresholds, setGapThresholds, activateTrip, deactivateTrip } = trip;
    const sourceId = details?.source ?? "";
    const objectId = details?.objectId ?? "";
    const [sortOrder, setSortOrder] = useState<SortOrder>("newest");

    const [params, setParams] = useState<HistoryParams>(() => ({
        fromDate: new Date(Date.now() - toMilliseconds({ days: 1 })).toISOString(),
        toDate: new Date().toISOString(),
        maxPoints: 5000,
    }));

    const {
        data: rawData,
        isLoading,
        error,
    } = useQuery(
        eventQueries.history(sourceId, objectId, {
            from: params.fromDate,
            to: params.toDate,
            maxPoints: params.maxPoints,
        }),
    );

    const history = useMemo(() => {
        if (!rawData) return [];
        return transformFeaturesToHistory(rawData.data);
    }, [rawData]);

    const tripWaypoints = useMemo(() => {
        if (!rawData) return [];
        return extractTripWaypoints(rawData.data, gapThresholds);
    }, [rawData, gapThresholds]);

    // Update gap thresholds when time range changes
    useEffect(() => {
        const adaptiveThresholds = calculateAdaptiveGapThresholds(params.fromDate, params.toDate);
        setGapThresholds(adaptiveThresholds);
    }, [params.fromDate, params.toDate, setGapThresholds]);

    // Activate trip when waypoints are available
    useEffect(() => {
        if (tripWaypoints.length >= 2) {
            activateTrip(tripWaypoints, details?.displayName ?? "Trip");
        }
    }, [tripWaypoints, activateTrip, details?.displayName]);

    const sortedHistory = useMemo(() => {
        return [...history].sort((a, b) => {
            const diff = new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime();
            return sortOrder === "newest" ? diff : -diff;
        });
    }, [history, sortOrder]);

    // Cleanup: deactivate trip when component unmounts or objectId changes
    useEffect(() => {
        return () => {
            deactivateTrip();
        };
    }, [objectId, deactivateTrip]);

    if (!details) return null;

    function renderContent() {
        if (isLoading) {
            return (
                <VStack align="center" justify="center" py={8} flex={1}>
                    <Spinner size="md" />
                    <Text fontSize="sm" opacity={0.6} mt={2}>
                        {t("timeline.loadingHistory")}
                    </Text>
                </VStack>
            );
        }

        if (error) {
            const isNoData = error instanceof ApiError && error.status === 404;
            return (
                <VStack align="center" justify="center" py={8} flex={1}>
                    <Text
                        fontSize="sm"
                        opacity={isNoData ? 0.6 : undefined}
                        color={isNoData ? undefined : "red.400"}
                    >
                        {isNoData ? t("timeline.noHistory") : t("timeline.failedToLoad")}
                    </Text>
                    <Text fontSize="xs" opacity={0.6}>
                        {isNoData
                            ? t("timeline.noHistoryDescription")
                            : error instanceof Error
                              ? error.message
                              : t("errors.unknownError")}
                    </Text>
                </VStack>
            );
        }

        if (sortedHistory.length === 0) {
            return (
                <VStack align="center" justify="center" py={8} flex={1}>
                    <Text fontSize="sm" opacity={0.6}>
                        {t("timeline.noHistory")}
                    </Text>
                    <Text fontSize="xs" opacity={0.6}>
                        {t("timeline.noHistoryDescription")}
                    </Text>
                </VStack>
            );
        }

        return (
            <>
                {rawData?.sampling && (
                    <Flex
                        gap={3}
                        fontSize="xs"
                        opacity={0.6}
                        flexWrap="wrap"
                        align="center"
                        justify="space-between"
                    >
                        <Flex gap={3} flexWrap="wrap" align="center">
                            <Text>
                                {t("timeline.samplingInfo", {
                                    used: rawData.sampling.usedPointsReturned,
                                    total: rawData.sampling.totalPointsFound,
                                })}
                                {rawData.sampling.usedPointsReturned <
                                    rawData.sampling.totalPointsFound && (
                                    <Text as="span" opacity={0.8} ml={1}>
                                        {t("timeline.sampled")}
                                    </Text>
                                )}
                            </Text>
                            <Text opacity={0.5}>•</Text>
                            <Text>
                                {t("timeline.strategy", { name: rawData.sampling.strategy })}
                            </Text>
                        </Flex>

                        {/* Sort Toggle */}
                        <Flex
                            as="button"
                            align="center"
                            gap={2}
                            px={2}
                            py={1}
                            borderRadius="md"
                            bg="glass.subtle"
                            cursor="pointer"
                            transition="all 0.2s ease-out"
                            _hover={{ bg: "whiteAlpha.200" }}
                            onClick={() =>
                                setSortOrder((prev) => (prev === "newest" ? "oldest" : "newest"))
                            }
                        >
                            <Icon
                                as={sortOrder === "newest" ? FaSortAmountDown : FaSortAmountUp}
                                boxSize={3}
                            />
                            <Text fontSize="xs" fontWeight={500}>
                                {sortOrder === "newest"
                                    ? t("timeline.newestFirst")
                                    : t("timeline.oldestFirst")}
                            </Text>
                        </Flex>
                    </Flex>
                )}
                <HistoryTimeline entries={sortedHistory} />
            </>
        );
    }

    return (
        <VStack gap="4" paddingBottom={3} height="100%" align="stretch">
            {/* Timeline Settings */}
            <HistoryControls
                maxPoints={params.maxPoints}
                onApply={(fromDate, toDate, maxPoints) => {
                    deactivateTrip();
                    setParams({ fromDate, toDate, maxPoints });
                }}
            />

            {/* Trip Playback Controls */}
            {tripWaypoints.length >= 2 && (
                <VStack gap={3} align="stretch">
                    <AccentHeading>
                        <Flex align="center" gap={2}>
                            {t("timeline.tripPlayback")}
                            {trip.active && (
                                <SettingsPopover
                                    title={t("timeline.gapDetection")}
                                    placement="bottom-start"
                                    content={<GapDetectionSettings />}
                                >
                                    <Tooltip content={t("timeline.gapDetection")}>
                                        <Box as="span" display="inline-flex">
                                            <Popover.Trigger asChild>
                                                <IconButton
                                                    aria-label={t("timeline.gapDetection")}
                                                    size="xs"
                                                    variant="ghost"
                                                >
                                                    <LuSettings2 />
                                                </IconButton>
                                            </Popover.Trigger>
                                        </Box>
                                    </Tooltip>
                                </SettingsPopover>
                            )}
                        </Flex>
                    </AccentHeading>
                    <TripControlsContent
                        waypoints={tripWaypoints}
                        displayName={details?.displayName || "Trip"}
                    />
                </VStack>
            )}

            {/* History Log */}
            <VStack gap={3} align="stretch">
                <AccentHeading>{t("timeline.historyLog")}</AccentHeading>
                {renderContent()}
            </VStack>
        </VStack>
    );
}
