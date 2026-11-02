// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex } from "@chakra-ui/react";
import { LuCheck } from "react-icons/lu";
import { type ReactElement } from "react";
import type { WeightStyleSpec } from "../specs";
import { useStyleValue, createStyleChangeHandler } from "../tool.hooks";

const WEIGHT_PRESETS = [1, 2, 3, 4, 6, 8, 12, 16];

/**
 * Visual weight selector — shows a vertical list of horizontal lines
 * at preset thicknesses, matching Google Draw's weight picker.
 */
export function ToolWeightSelect({
    spec,
    toolKey,
}: {
    spec: WeightStyleSpec;
    toolKey: string;
}): ReactElement {
    const current = useStyleValue(toolKey, spec.label, spec.input.defaultValue);
    const onChange = createStyleChangeHandler(spec.input, toolKey, spec.label);

    return (
        <Flex direction="column" gap="0" py="1" minW="140px">
            {WEIGHT_PRESETS.map((weight) => {
                const selected = current === weight;
                return (
                    <Flex
                        key={weight}
                        align="center"
                        gap="2"
                        px="3"
                        py="2"
                        cursor="pointer"
                        _hover={{ bg: "whiteAlpha.200" }}
                        onClick={() => onChange(weight)}
                    >
                        <Flex w="16px" justify="center" flexShrink={0}>
                            {selected && (
                                <LuCheck color="var(--chakra-colors-cyan-400)" size={14} />
                            )}
                        </Flex>
                        <svg
                            width="100%"
                            height={Math.max(weight, 4)}
                            viewBox={`0 0 100 ${Math.max(weight, 4)}`}
                        >
                            <line
                                x1="0"
                                y1={Math.max(weight, 4) / 2}
                                x2="100"
                                y2={Math.max(weight, 4) / 2}
                                stroke="currentColor"
                                strokeWidth={weight}
                            />
                        </svg>
                    </Flex>
                );
            })}
        </Flex>
    );
}
