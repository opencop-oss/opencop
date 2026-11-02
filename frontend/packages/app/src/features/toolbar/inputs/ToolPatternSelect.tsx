// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex } from "@chakra-ui/react";
import { LuCheck } from "react-icons/lu";
import { type ReactElement } from "react";
import type { PatternStyleSpec } from "../specs";
import { useStyleValue, createStyleChangeHandler } from "../tool.hooks";

// SVG dash patterns for each stroke pattern type.
const PATTERN_DASHES: Record<string, string> = {
    solid: "",
    dotted: "2,4",
    dashed: "8,6",
    outlined: "16,6,4,6",
};

export function ToolPatternSelect({
    spec,
    toolKey,
}: {
    spec: PatternStyleSpec;
    toolKey: string;
}): ReactElement {
    const current = useStyleValue(toolKey, spec.label, spec.input.defaultValue);
    const onChange = createStyleChangeHandler(spec.input, toolKey, spec.label);

    return (
        <Flex direction="column" gap="0" py="1" minW="140px">
            {spec.input.items.map((pattern) => {
                const selected = current === pattern;
                return (
                    <Flex
                        key={pattern}
                        align="center"
                        gap="2"
                        px="3"
                        py="3"
                        cursor="pointer"
                        _hover={{ bg: "whiteAlpha.200" }}
                        onClick={() => onChange(pattern)}
                    >
                        <Flex w="16px" justify="center" flexShrink={0}>
                            {selected && (
                                <LuCheck color="var(--chakra-colors-cyan-400)" size={14} />
                            )}
                        </Flex>
                        <svg width="100%" height="4" viewBox="0 0 120 4">
                            <line
                                x1="0"
                                y1="2"
                                x2="120"
                                y2="2"
                                stroke="currentColor"
                                strokeWidth="2"
                                strokeDasharray={PATTERN_DASHES[pattern] || ""}
                            />
                        </svg>
                    </Flex>
                );
            })}
        </Flex>
    );
}
