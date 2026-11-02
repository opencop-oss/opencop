// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Text, VStack, Slider, Checkbox, IconButton, Button } from "@chakra-ui/react";
import {
    MdPlayArrow,
    MdPause,
    MdFastForward,
    MdFastRewind,
    MdSkipNext,
    MdSkipPrevious,
} from "react-icons/md";
import { useTranslation } from "react-i18next";
import { useTrip } from "./TripContext";
import { formatRelativeTime, formatWallTime } from "./trip-format.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { TimeThresholdInput } from "./TimeThresholdInput";
import { DistanceThresholdInput } from "./DistanceThresholdInput";

/**
 * Internal controls content (mode toggle, speed, scrubber, time)
 * Used inside the HistorySection panel
 */
export function TripControlsContent({
    waypoints,
    displayName,
}: {
    waypoints: Array<{ coordinates: [number, number]; timestamp: number }>;
    displayName: string;
}) {
    const { t } = useTranslation();
    const trip = useTrip();
    const timezone = useUserTimezone();

    const elapsed = trip.currentTime - trip.startTime;
    const duration = trip.endTime - trip.startTime;
    const progress = duration > 0 ? (elapsed / duration) * 100 : 0;

    return (
        <VStack gap={3} align="stretch">
            {/* Path and Animation Controls */}
            <Flex gap={3} align="center" flexWrap="wrap">
                {/* Show Path Checkbox */}
                <Checkbox.Root
                    checked={trip.active}
                    onCheckedChange={(e) => {
                        if (e.checked) {
                            trip.activateTrip(waypoints, displayName);
                        } else {
                            trip.deactivateTrip();
                        }
                    }}
                >
                    <Checkbox.HiddenInput />
                    <Checkbox.Control />
                    <Checkbox.Label>
                        <Text fontSize="sm">{t("timeline.showPath")}</Text>
                    </Checkbox.Label>
                </Checkbox.Root>

                {/* Animation Toggle - Always visible, but only functional when trip is active */}
                <Checkbox.Root
                    checked={trip.showAnimation}
                    disabled={!trip.active}
                    opacity={!trip.active ? 0.5 : 1}
                    cursor={!trip.active ? "not-allowed" : "pointer"}
                    onCheckedChange={(e) => {
                        if (trip.active) {
                            trip.setShowAnimation(!!e.checked);
                        }
                    }}
                >
                    <Checkbox.HiddenInput />
                    <Checkbox.Control />
                    <Checkbox.Label>
                        <Text fontSize="sm">{t("timeline.showAnimation")}</Text>
                    </Checkbox.Label>
                </Checkbox.Root>

                {/* Zoom Controls - Always rendered to prevent layout shift */}
                {!trip.hasZoomed ? (
                    <Button
                        size="xs"
                        variant="outline"
                        visibility={trip.active ? "visible" : "hidden"}
                        onClick={() => {
                            if (trip.savedView) {
                                trip.markAsZoomed(trip.savedView);
                            } else {
                                // Trigger zoom by setting hasZoomed to true
                                trip.markAsZoomed({
                                    center: { lng: 0, lat: 0 },
                                    zoom: 0,
                                    bearing: 0,
                                    pitch: 0,
                                });
                            }
                        }}
                    >
                        {t("timeline.zoomToPath")}
                    </Button>
                ) : (
                    <Button
                        size="xs"
                        variant="outline"
                        visibility={trip.active ? "visible" : "hidden"}
                        onClick={() => trip.returnToSavedView()}
                    >
                        {t("timeline.returnToMap")}
                    </Button>
                )}
            </Flex>

            {/* Playback Controls (only when animation is enabled) */}
            {trip.showAnimation && (
                <>
                    {/* Time Scrubber */}
                    <VStack gap={2} align="stretch">
                        <Slider.Root
                            value={[progress]}
                            min={0}
                            max={100}
                            step={0.1}
                            onValueChange={(e) => {
                                const value = e.value[0];
                                const newTime = trip.startTime + (duration * value) / 100;
                                trip.seekTo(newTime);
                            }}
                        >
                            <Slider.Control position="relative">
                                <Slider.Track bg="whiteAlpha.300" h="6px">
                                    <Slider.Range bg="blue.400" />
                                </Slider.Track>

                                {/* Waypoint tick marks */}
                                {trip.waypoints.map((wp) => {
                                    const wpProgress =
                                        ((wp.timestamp - trip.startTime) / duration) * 100;
                                    return (
                                        <Flex
                                            key={wp.timestamp}
                                            position="absolute"
                                            left={`${wpProgress}%`}
                                            top="0"
                                            h="6px"
                                            w="2px"
                                            bg="orange.400"
                                            opacity={0.8}
                                            pointerEvents="none"
                                        />
                                    );
                                })}

                                <Slider.Thumbs
                                    w="12px"
                                    h="12px"
                                    bg="blue.400"
                                    borderRadius="full"
                                    cursor="pointer"
                                />
                            </Slider.Control>
                        </Slider.Root>

                        {/* Playback Controls + Time Display */}
                        <Flex justify="space-between" align="center" gap={2}>
                            {/* Left: Relative Time */}
                            <Text fontSize="xs" opacity={0.8} minW="16">
                                {formatRelativeTime(elapsed)}
                            </Text>

                            {/* Center: Playback Controls */}
                            <Flex gap={1} align="center" justify="center">
                                {/* Previous Waypoint */}
                                <IconButton
                                    size="xs"
                                    aria-label={t("playback.previousWaypoint")}
                                    onClick={() => trip.goToPreviousWaypoint()}
                                >
                                    <MdSkipPrevious />
                                </IconButton>

                                {/* Decrease Speed */}
                                <IconButton
                                    size="xs"
                                    aria-label={t("playback.slowDown")}
                                    onClick={() => trip.decreaseSpeed()}
                                    disabled={trip.speed === 2}
                                >
                                    <MdFastRewind />
                                </IconButton>

                                {/* Play/Pause */}
                                <IconButton
                                    size="xs"
                                    aria-label={
                                        trip.playing ? t("playback.pause") : t("playback.play")
                                    }
                                    onClick={() => trip.setPlaying(!trip.playing)}
                                >
                                    {trip.playing ? <MdPause /> : <MdPlayArrow />}
                                </IconButton>

                                {/* Increase Speed */}
                                <IconButton
                                    size="xs"
                                    aria-label={t("playback.speedUp")}
                                    onClick={() => trip.increaseSpeed()}
                                    disabled={trip.speed === 10}
                                >
                                    <MdFastForward />
                                </IconButton>

                                {/* Next Waypoint */}
                                <IconButton
                                    size="xs"
                                    aria-label={t("playback.nextWaypoint")}
                                    onClick={() => trip.goToNextWaypoint()}
                                >
                                    <MdSkipNext />
                                </IconButton>

                                {/* Speed Display */}
                                <Text fontSize="xs" fontWeight="bold" minW="8" textAlign="center">
                                    {trip.speed}×
                                </Text>
                            </Flex>

                            {/* Right: Wall Clock Time */}
                            <Text fontSize="xs" opacity={0.8} minW="16" textAlign="right">
                                {formatWallTime(trip.currentTime, timezone)}
                            </Text>
                        </Flex>
                    </VStack>
                </>
            )}
        </VStack>
    );
}

/**
 * Gap Detection settings content for the popover.
 * Allows configuring hide gaps option and time/distance thresholds for gap detection.
 */
export function GapDetectionSettings() {
    const { t } = useTranslation();
    const trip = useTrip();

    return (
        <VStack gap={3} align="stretch" minW="250px">
            {/* Hide Gaps Checkbox */}
            <Checkbox.Root
                checked={trip.hideGaps}
                onCheckedChange={(e) => trip.setHideGaps(!!e.checked)}
            >
                <Checkbox.HiddenInput />
                <Checkbox.Control />
                <Checkbox.Label>
                    <Text fontSize="sm">{t("timeline.hideGaps")}</Text>
                </Checkbox.Label>
            </Checkbox.Root>

            {/* Threshold Inputs */}
            <VStack gap={2} align="stretch">
                <TimeThresholdInput
                    thresholds={trip.gapThresholds}
                    onChange={trip.setGapThresholds}
                    label={t("timeline.timeThreshold")}
                />
                <DistanceThresholdInput
                    thresholds={trip.gapThresholds}
                    onChange={trip.setGapThresholds}
                    label={t("timeline.distanceThreshold")}
                />
            </VStack>
        </VStack>
    );
}
