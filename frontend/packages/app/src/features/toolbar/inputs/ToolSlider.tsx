// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type ReactElement } from "react";
import type { SliderStyleSpec } from "../specs";
import { OcSlider } from "@opencop/ui";
import { Box } from "@chakra-ui/react";
import { useStyleValue, createStyleChangeHandler } from "../tool.hooks";

export function ToolSlider({
    spec,
    toolKey,
}: {
    spec: SliderStyleSpec;
    toolKey: string;
}): ReactElement {
    const value = useStyleValue(toolKey, spec.label, spec.input.defaultValue);
    const onChange = createStyleChangeHandler(spec.input, toolKey, spec.label);

    return (
        <Box w="2xs">
            <OcSlider
                label={spec.label}
                min={spec.input.min}
                max={spec.input.max}
                defaultValue={value}
                step={spec.input.step}
                units="px"
                onChange={onChange}
            />
        </Box>
    );
}
