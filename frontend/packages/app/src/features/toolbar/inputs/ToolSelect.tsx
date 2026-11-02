// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import type { SelectStyleSpec } from "../specs";
import { OcTabSelect } from "@opencop/ui";
import { Box } from "@chakra-ui/react";
import { useStyleValue, createStyleChangeHandler } from "../tool.hooks";

export function ToolSelect({
    spec,
    toolKey,
}: {
    spec: SelectStyleSpec;
    toolKey: string;
}): ReactElement {
    const value = useStyleValue(toolKey, spec.label, spec.input.defaultValue);
    const onChange = createStyleChangeHandler(spec.input, toolKey, spec.label);

    return (
        <Box w="2xs">
            <OcTabSelect
                label={spec.label}
                options={spec.input.items.map((i) => ({ label: i, value: i }))}
                defaultValue={value}
                onChange={onChange}
            />
        </Box>
    );
}
