// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useState } from "react";
import { HStack, NumberInput, Text } from "@chakra-ui/react";
import { OcSlider } from "./OcSlider.tsx";

function clampOpacity(opacity: number): number {
    return Math.min(100, Math.max(0, Math.round(opacity)));
}

export interface LayerOpacityControlProps {
    value: number;
    onChange: (value: number) => void;
    label: string;
    inputAriaLabel?: string;
}

export function LayerOpacityControl({
    value,
    onChange,
    label,
    inputAriaLabel,
}: LayerOpacityControlProps) {
    const [draftOpacity, setDraftOpacity] = useState(String(value));
    const [focused, setFocused] = useState(false);

    const displayValue = focused ? draftOpacity : String(value);

    function handleFocus() {
        setFocused(true);
        setDraftOpacity(String(value));
    }

    function handleBlur() {
        setFocused(false);
        const parsed = Number(draftOpacity);
        if (!Number.isFinite(parsed)) {
            setDraftOpacity(String(value));
            return;
        }
        const next = clampOpacity(parsed);
        onChange(next);
        setDraftOpacity(String(next));
    }

    return (
        <HStack w="full" align="center" gap="3">
            <Text textStyle="sm" fontWeight="500" color="fg.muted" minW="14">
                {label}
            </Text>
            <OcSlider
                min={0}
                max={100}
                step={1}
                value={value}
                onChange={(v) => onChange(clampOpacity(v))}
                ariaLabel={inputAriaLabel ?? label}
                rootProps={{ flex: "1" }}
            />
            <HStack gap="1.5" flexShrink={0}>
                <NumberInput.Root
                    size="xs"
                    value={displayValue}
                    min={0}
                    max={100}
                    step={1}
                    w="16"
                    onValueChange={(details) => {
                        setDraftOpacity(details.value);
                        if (Number.isFinite(details.valueAsNumber)) {
                            onChange(clampOpacity(details.valueAsNumber));
                        }
                    }}
                    onFocus={handleFocus}
                    onBlur={handleBlur}
                >
                    <NumberInput.Input
                        h="7"
                        textAlign="center"
                        fontSize="xs"
                        aria-label={inputAriaLabel ?? label}
                    />
                </NumberInput.Root>
                <Text fontSize="xs" color="fg.muted">
                    %
                </Text>
            </HStack>
        </HStack>
    );
}
