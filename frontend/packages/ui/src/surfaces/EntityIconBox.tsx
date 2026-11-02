// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex } from "@chakra-ui/react";
import type { ReactNode } from "react";

/**
 * The dark square frame used as the icon slot in entity headers (event
 * detail, watchbox detail). Carries no icon content of its own — callers
 * render their own glyph, image, or text inside.
 */
export function EntityIconBox({
    borderColor = "active",
    color,
    fontSize,
    children,
}: {
    borderColor?: string;
    color?: string;
    fontSize?: string;
    children: ReactNode;
}) {
    return (
        <Flex
            w="12"
            h="12"
            backgroundColor="#0F1317"
            border="1px solid"
            borderColor={borderColor}
            borderRadius="sm"
            align="center"
            justify="center"
            flexShrink={0}
            overflowY="hidden"
            color={color}
            fontSize={fontSize}
        >
            {children}
        </Flex>
    );
}
