// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { Box, Flex, Text } from "@chakra-ui/react";
import { TYPE_PALETTE } from "./type-colors.ts";
import type { TypePaletteKey } from "./type-colors.ts";

export interface TypeColorLabelProps {
    label: string;
    color: TypePaletteKey;
    abbr?: string;
    /**
     * Size of the abbreviation chip. "sm" (16px) suits dense table cells; "md" (24px) matches
     * the ~24px Tabler glyphs used in drawer-header icon slots. Default "sm". Ignored without `abbr`.
     */
    size?: "sm" | "md";
}

const ABBR_CHIP_SIZE = {
    sm: { box: 4, font: "9px" },
    md: { box: 6, font: "13px" },
} as const;

export function TypeColorLabel({
    label,
    color,
    abbr,
    size = "sm",
}: TypeColorLabelProps): ReactElement {
    const hex = TYPE_PALETTE[color];

    if (abbr) {
        const chip = ABBR_CHIP_SIZE[size];
        return (
            <Box
                as="span"
                role="img"
                aria-label={label}
                display="inline-flex"
                alignItems="center"
                justifyContent="center"
                border="1px solid"
                borderColor={hex}
                borderRadius="none"
                bg={`${hex}0D`}
                w={chip.box}
                h={chip.box}
                flexShrink={0}
                title={label}
            >
                <Text
                    as="span"
                    fontSize={chip.font}
                    fontWeight="bold"
                    color={hex}
                    letterSpacing="normal"
                    lineHeight="1"
                    userSelect="none"
                >
                    {abbr}
                </Text>
            </Box>
        );
    }

    return (
        <Flex align="center" gap={1.5}>
            <Box w="10px" h="10px" borderRadius="2px" bg={hex} flexShrink={0} />
            <Text color="inherit">{label}</Text>
        </Flex>
    );
}
