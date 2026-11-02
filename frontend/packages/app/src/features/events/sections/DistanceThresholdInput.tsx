// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Text, Input } from "@chakra-ui/react";
import { useState, useMemo } from "react";
import type { GapThresholds } from "./history.util";
import { useMeasurementSystem } from "~/features/measurements/use-measurement-system.hook";

interface DistanceThresholdInputProps {
    thresholds: GapThresholds;
    onChange: (thresholds: GapThresholds) => void;
    label: string;
}

export function DistanceThresholdInput({
    thresholds,
    onChange,
    label,
}: DistanceThresholdInputProps) {
    const {
        system: measurementSystem,
        breakdownDistance,
        combineDistance,
    } = useMeasurementSystem();
    const { large, small, largeUnit, smallUnit } = useMemo(
        () => breakdownDistance(thresholds.distanceMeters),
        [breakdownDistance, thresholds.distanceMeters],
    );

    const [editingLarge, setEditingLarge] = useState<string | null>(null);
    const [editingSmall, setEditingSmall] = useState<string | null>(null);

    const handleLargeBlur = () => {
        if (editingLarge !== null) {
            const value = Number.parseInt(editingLarge) || 0;
            onChange({
                ...thresholds,
                distanceMeters: combineDistance(value, small),
            });
            setEditingLarge(null);
        }
    };

    const handleSmallBlur = () => {
        if (editingSmall !== null) {
            const maxSmall = measurementSystem === "IMPERIAL" ? 5279 : 999;
            const value = Math.min(Math.max(Number.parseInt(editingSmall) || 0, 0), maxSmall);
            onChange({
                ...thresholds,
                distanceMeters: combineDistance(large, value),
            });
            setEditingSmall(null);
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
                value={editingLarge !== null ? editingLarge : large.toString()}
                onChange={(e) => setEditingLarge(e.target.value)}
                onBlur={handleLargeBlur}
                width="16"
            />
            <Text fontSize="xs" opacity={0.6}>
                {largeUnit}
            </Text>
            <Input
                type="text"
                size="xs"
                value={editingSmall !== null ? editingSmall : small.toString()}
                onChange={(e) => setEditingSmall(e.target.value)}
                onBlur={handleSmallBlur}
                width="16"
            />
            <Text fontSize="xs" opacity={0.6}>
                {smallUnit}
            </Text>
        </Flex>
    );
}
