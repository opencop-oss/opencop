// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, HStack, Text } from "@chakra-ui/react";
import { LayerOpacityControl, OcDateTimePicker } from "@opencop/ui";
import { DateTime } from "luxon";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { WmsDateScrubber } from "./WmsDateScrubber";

const FALLBACK_MIN = DateTime.fromISO("2000-01-01T00:00:00Z");

interface ImageryEntryDetailsProps {
    id: string;
    open: boolean;
    name: string;
    opacity: number;
    onOpacityChange: (value: number) => void;
    hasTimeControls: boolean;
    time: string | null;
    timeMin: DateTime | null;
    timeMax: DateTime | null;
    step: string | null;
    onTimeChange: (time: string | null) => void;
}

export function ImageryEntryDetails({
    id,
    open,
    name,
    opacity,
    onOpacityChange,
    hasTimeControls,
    time,
    timeMin,
    timeMax,
    step,
    onTimeChange,
}: ImageryEntryDetailsProps) {
    const { t } = useTranslation();

    const min = timeMin ?? FALLBACK_MIN;
    const max = timeMax ?? DateTime.now();
    const startTime = useMemo(
        () => (time ? DateTime.fromISO(time.split("T")[0]) : max),
        [time, max],
    );

    return (
        <Box
            id={id}
            style={{
                maxHeight: open ? "220px" : "0px",
                overflow: "hidden",
                transition: "max-height 0.25s ease",
            }}
        >
            <Box px="3">
                <LayerOpacityControl
                    value={opacity}
                    onChange={onOpacityChange}
                    label={t("imagery.overlay.opacity")}
                    inputAriaLabel={`${name} ${t("imagery.overlay.opacity").toLowerCase()} value`}
                />
                {hasTimeControls && (
                    <>
                        <HStack
                            w="full"
                            align="center"
                            gap="3"
                            pt="2"
                            pb={step !== null && timeMin !== null && timeMax !== null ? "1" : "0"}
                        >
                            <Text
                                textStyle="sm"
                                fontWeight="500"
                                color="fg.muted"
                                minW="14"
                                flexShrink={0}
                            >
                                {t("imagery.overlay.time")}
                            </Text>
                            <Box flex="1">
                                <OcDateTimePicker
                                    min={min}
                                    max={max}
                                    startTime={startTime}
                                    typeable
                                    onChange={(date) => {
                                        // Use the calendar date components as UTC midnight so the
                                        // date part of the stored ISO string matches what the user sees.
                                        onTimeChange(`${date.toISODate()}T00:00:00.000Z`);
                                    }}
                                    inputProps={{
                                        size: "xs",
                                        "aria-label": t("imagery.overlay.selectTime", { name }),
                                    }}
                                />
                            </Box>
                        </HStack>
                        {step !== null && timeMin !== null && timeMax !== null && (
                            <WmsDateScrubber
                                timeMin={timeMin}
                                timeMax={timeMax}
                                step={step}
                                selectedTime={time}
                                name={name}
                                onSelect={onTimeChange}
                            />
                        )}
                    </>
                )}
            </Box>
        </Box>
    );
}
