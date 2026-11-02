// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, IconButton, parseColor } from "@chakra-ui/react";
import { OcColorPicker, Tooltip } from "@opencop/ui";
import { type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { MdBorderColor, MdFormatColorFill } from "react-icons/md";
import type { SwatchStyleSpec } from "../specs";
import { useStyleValue, createStyleChangeHandler } from "../tool.hooks";

/** Normalize a stored color to a hexa string for the trigger preview swatch. */
function toHexa(value: string): string {
    try {
        return parseColor(value).toString("hexa");
    } catch {
        return "#000000ff";
    }
}

export function ToolSwatch({
    spec,
    toolKey,
}: {
    spec: SwatchStyleSpec;
    toolKey: string;
}): ReactElement {
    const { t } = useTranslation();
    const currentValue = useStyleValue(toolKey, spec.label, spec.input.defaultValue);
    const onChange = createStyleChangeHandler(spec.input, toolKey, spec.label);

    const displayLabel = spec.labelKey
        ? t(spec.labelKey as never, { defaultValue: spec.label })
        : spec.label;
    const isFill = spec.label.toLowerCase().includes("fill");
    const Icon = isFill ? MdFormatColorFill : MdBorderColor;
    const preview = toHexa(currentValue);

    return (
        <OcColorPicker value={currentValue} onChange={onChange}>
            <IconButton active="iconHover" border="none" aria-label={displayLabel}>
                <Tooltip content={displayLabel}>
                    <Box
                        position="relative"
                        display="flex"
                        alignItems="center"
                        justifyContent="center"
                        mt="3px"
                    >
                        <Icon />
                        <Box
                            position="absolute"
                            bottom="-1px"
                            left="50%"
                            transform="translateX(-50%)"
                            w="17px"
                            h="5px"
                            backgroundImage="conic-gradient(#ccc 25%, #fff 25%, #fff 50%, #ccc 50%, #ccc 75%, #fff 75%)"
                            backgroundSize="5px 5px"
                            outline="1px solid"
                            outlineColor="fg.muted"
                            zIndex={1}
                        />
                        <Box
                            position="absolute"
                            bottom="-1px"
                            left="50%"
                            transform="translateX(-50%)"
                            w="17px"
                            h="5px"
                            bg={preview}
                            outline="1px solid"
                            outlineColor="fg.muted"
                            zIndex={2}
                        />
                    </Box>
                </Tooltip>
            </IconButton>
        </OcColorPicker>
    );
}
