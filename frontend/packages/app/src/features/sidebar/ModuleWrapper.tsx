// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, useToken } from "@chakra-ui/react";
import { useSidebar } from "./SidebarContext";

export function ModuleWrapper({
    children,
    padding,
    fixedHeight,
}: {
    children?: React.ReactNode;
    padding?: boolean;
    /** When true, constrains to parent height with no page-level scroll (for panel layouts). */
    fixedHeight?: boolean;
}) {
    const { width } = useSidebar();
    const [widthRem] = useToken("sizes", [width.toString()]);

    return (
        <Flex
            position="relative"
            left={`${width}`}
            height="100%"
            width={`calc(100% - ${widthRem})`}
            bg="bg"
            overflowY={fixedHeight ? "hidden" : "auto"}
        >
            <Box
                width="100%"
                height="100%"
                minWidth={0}
                minHeight={fixedHeight ? 0 : "fit-content"}
            >
                <Box
                    padding={padding ? "50px" : undefined}
                    height={fixedHeight ? "100%" : undefined}
                >
                    {children}
                </Box>
            </Box>
        </Flex>
    );
}
