// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo } from "react";
import { Box, HStack, Text } from "@chakra-ui/react";
import { OcSlider } from "@opencop/ui";
import { DateTime } from "luxon";
import { useTranslation } from "react-i18next";
import { buildScrubberDates, findSliderValue } from "./wms-date-scrubber.util";

interface WmsDateScrubberProps {
    timeMin: DateTime;
    timeMax: DateTime;
    step: string;
    selectedTime: string | null;
    name: string;
    onSelect: (isoString: string) => void;
}

export function WmsDateScrubber({
    timeMin,
    timeMax,
    step,
    selectedTime,
    name,
    onSelect,
}: WmsDateScrubberProps) {
    const { t } = useTranslation();

    const dates = useMemo(
        () => buildScrubberDates(timeMin, timeMax, step),
        [timeMin, timeMax, step],
    );

    const selectedDate = selectedTime ? selectedTime.split("T")[0] : null;

    const sliderValue = useMemo(() => findSliderValue(dates, selectedDate), [dates, selectedDate]);

    if (dates.length < 2) return null;

    const minLabel = timeMin.toFormat("MMM yyyy");
    const maxLabel = timeMax.toFormat("MMM yyyy");

    return (
        <Box px="2" pb="2">
            <OcSlider
                key={sliderValue}
                min={0}
                max={dates.length - 1}
                step={1}
                defaultValue={sliderValue}
                ariaLabel={t("imagery.overlay.selectTime", { name })}
                rootProps={{
                    h: "6",
                    getAriaValueText: ({ value }) => {
                        const iso = dates[value];
                        return iso ? DateTime.fromISO(iso).toFormat("MMM d, yyyy") : "";
                    },
                    onValueChangeEnd: (e) => {
                        const date = dates[e.value[0]];
                        if (date) onSelect(date + "T00:00:00.000Z");
                    },
                }}
            />
            <HStack justify="space-between" px="1">
                <Text textStyle="2xs" color="fg.subtle">
                    {minLabel}
                </Text>
                <Text textStyle="2xs" color="fg.subtle">
                    {maxLabel}
                </Text>
            </HStack>
        </Box>
    );
}
