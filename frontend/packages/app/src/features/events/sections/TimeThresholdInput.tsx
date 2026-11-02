// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Text, Input } from "@chakra-ui/react";
import { useState } from "react";
import type { GapThresholds } from "./history.util";
import { msToHMS, hmsToMs } from "./trip-format.util";

interface TimeThresholdInputProps {
    thresholds: GapThresholds;
    onChange: (thresholds: GapThresholds) => void;
    label: string;
}

export function TimeThresholdInput({ thresholds, onChange, label }: TimeThresholdInputProps) {
    const { hours, minutes, seconds } = msToHMS(thresholds.timeMs);

    const [editingHours, setEditingHours] = useState<string | null>(null);
    const [editingMinutes, setEditingMinutes] = useState<string | null>(null);
    const [editingSeconds, setEditingSeconds] = useState<string | null>(null);

    const handleHoursBlur = () => {
        if (editingHours !== null) {
            const value = Number.parseInt(editingHours) || 0;
            onChange({
                ...thresholds,
                timeMs: hmsToMs(value, minutes, seconds),
            });
            setEditingHours(null);
        }
    };

    const handleMinutesBlur = () => {
        if (editingMinutes !== null) {
            const value = Math.min(Math.max(Number.parseInt(editingMinutes) || 0, 0), 59);
            onChange({
                ...thresholds,
                timeMs: hmsToMs(hours, value, seconds),
            });
            setEditingMinutes(null);
        }
    };

    const handleSecondsBlur = () => {
        if (editingSeconds !== null) {
            const value = Math.min(Math.max(Number.parseInt(editingSeconds) || 0, 0), 59);
            onChange({
                ...thresholds,
                timeMs: hmsToMs(hours, minutes, value),
            });
            setEditingSeconds(null);
        }
    };

    return (
        <Flex align="center" gap={2}>
            <Text fontSize="xs" opacity={0.8} whiteSpace="nowrap" minW="16">
                {label}
            </Text>
            <Input
                type="text"
                size="xs"
                value={editingHours !== null ? editingHours : hours.toString()}
                onChange={(e) => setEditingHours(e.target.value)}
                onBlur={handleHoursBlur}
                width="14"
            />
            {/* eslint-disable-next-line i18next/no-literal-string */}
            <Text fontSize="xs" opacity={0.6}>
                h
            </Text>
            <Input
                type="text"
                size="xs"
                value={editingMinutes !== null ? editingMinutes : minutes.toString()}
                onChange={(e) => setEditingMinutes(e.target.value)}
                onBlur={handleMinutesBlur}
                width="14"
            />
            {/* eslint-disable-next-line i18next/no-literal-string */}
            <Text fontSize="xs" opacity={0.6}>
                m
            </Text>
            <Input
                type="text"
                size="xs"
                value={editingSeconds !== null ? editingSeconds : seconds.toString()}
                onChange={(e) => setEditingSeconds(e.target.value)}
                onBlur={handleSecondsBlur}
                width="14"
            />
            {/* eslint-disable-next-line i18next/no-literal-string */}
            <Text fontSize="xs" opacity={0.6}>
                s
            </Text>
        </Flex>
    );
}
