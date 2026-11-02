// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Chart, useChart } from "@chakra-ui/charts";
import { Area, AreaChart } from "recharts";
import { Card, Stat as ChakraStat, HStack, type CardRootProps } from "@chakra-ui/react";
import { Tooltip } from "../overlays/Tooltip";
import type { RingBuffer } from "./stat.utils";

const COLOR_GOOD = "#34d399"; // emerald-400
const COLOR_WARN = "#fbbf24"; // amber-400
const COLOR_BAD = "#f87171"; // red-400

const TEXT_COLOR_GOOD = "#00ffaa";
const TEXT_COLOR_WARN = "#ffb700";
const TEXT_COLOR_BAD = "#ff0000";

const colors = {
    good: {
        color: COLOR_GOOD,
        text: TEXT_COLOR_GOOD,
    },
    warn: {
        color: COLOR_WARN,
        text: TEXT_COLOR_WARN,
    },
    bad: {
        color: COLOR_BAD,
        text: TEXT_COLOR_BAD,
    },
} as const;

/** Threshold color for the current value text + stroke. */
function thresholdColor(value: number, good: number, warn: number, higherIsBetter: boolean) {
    if (higherIsBetter) {
        if (value >= good) return colors.good;
        if (value >= warn) return colors.warn;
        return colors.bad;
    }
    if (value <= good) return colors.good;
    if (value <= warn) return colors.warn;
    return colors.bad;
}

// ---------------------------------------------------------------------------
// Stat component
// ---------------------------------------------------------------------------

export type StatProps = {
    label: string;
    value: number;
    unit: string;
    buffer: RingBuffer;
    /** "good" threshold — green when past this. */
    good: number;
    /** "warn" threshold — yellow when past this, red beyond. */
    warn: number;
    /** If true, higher values are better (e.g. FPS). Default: false (lower is better). */
    higherIsBetter?: boolean;
    desc?: string;
} & CardRootProps;

export function Stat({
    label,
    value,
    unit,
    buffer,
    good,
    warn,
    higherIsBetter: hib = false,
    desc,
    ...cardProps
}: StatProps) {
    const color = thresholdColor(value, good, warn, hib);
    const data = buffer.map((v) => ({ value: v }));
    const chart = useChart({
        data,
        series: [{ name: "value" }],
    });

    return (
        <Card.Root
            overflow="hidden"
            h="20"
            borderRadius="4px"
            borderColor="gray.600"
            {...cardProps}
        >
            <Card.Body p="0" position="relative">
                <ChakraStat.Root py="1" px="2" gap="0" size="sm" zIndex={1}>
                    <HStack>
                        <Tooltip content={desc}>
                            <ChakraStat.Label textStyle="2xs">{label}</ChakraStat.Label>
                        </Tooltip>
                        <ChakraStat.ValueText color={color.text}>
                            {value.toFixed(2) + " " + unit}
                        </ChakraStat.ValueText>
                    </HStack>
                </ChakraStat.Root>
                <Chart.Root pos="absolute" bottom="0" w="full" h="2/3" chart={chart}>
                    <AreaChart
                        data={chart.data}
                        responsive
                        margin={{ top: 0, right: 0, left: 0, bottom: 0 }}
                    >
                        <defs>
                            <Chart.Gradient
                                id="fill-grad"
                                stops={[
                                    {
                                        offset: "0%",
                                        color: color.color,
                                        opacity: 1,
                                    },
                                    {
                                        offset: "50%",
                                        color: color.color,
                                        opacity: 0.8,
                                    },
                                    {
                                        offset: "100%",
                                        color: color.color,
                                        opacity: 0.3,
                                    },
                                ]}
                            />
                        </defs>

                        {chart.series.map((item) => (
                            <Area
                                key={item.name}
                                dataKey={chart.key(item.name)}
                                isAnimationActive={false}
                                fill={`url(#fill-grad)`}
                                stroke={`url(#fill-grad)`}
                                strokeWidth={1.5}
                            />
                        ))}
                    </AreaChart>
                </Chart.Root>
            </Card.Body>
        </Card.Root>
    );
}
