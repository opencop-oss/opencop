// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Text, Slider } from "@chakra-ui/react";

interface OptionSliderProps {
    label: string;
    min: number;
    max: number;
    step: number;
    value: number;
    onChange: (value: number) => void;
    displayValue: string;
}

export function OptionSlider({
    label,
    min,
    max,
    step,
    value,
    onChange,
    displayValue,
}: OptionSliderProps) {
    return (
        <Flex color="fg" direction="column" gap="1">
            <Text fontSize="xs" fontWeight="500">
                {label}
            </Text>
            <Slider.Root
                min={min}
                max={max}
                step={step}
                value={[value]}
                onValueChange={(details) => onChange(details.value[0])}
            >
                <Slider.Control>
                    <Slider.Track bg="rgba(255, 255, 255, 0.1)">
                        <Slider.Range bg="rgba(35, 143, 220, 0.8)" />
                    </Slider.Track>
                    <Slider.Thumb index={0} boxSize={4}>
                        <Slider.HiddenInput />
                    </Slider.Thumb>
                </Slider.Control>
            </Slider.Root>
            <Text fontSize="xs" opacity={0.7}>
                {displayValue}
            </Text>
        </Flex>
    );
}
