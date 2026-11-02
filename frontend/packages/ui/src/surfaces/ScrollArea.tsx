// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { ScrollArea as ChakraScrollArea, type ScrollAreaContentProps } from "@chakra-ui/react";
import type { PropsWithChildren } from "react";

export interface ScrollAreaProps extends PropsWithChildren {
    contentProps?: ScrollAreaContentProps;
}

export function ScrollArea(props: ScrollAreaProps) {
    return (
        <ChakraScrollArea.Root
            css={{ "--scrollbar-margin": "-12px", overflow: "unset" }}
            w="full"
            h="full"
            variant="hover"
        >
            <ChakraScrollArea.Viewport>
                <ChakraScrollArea.Content {...props.contentProps}>
                    {props.children}
                </ChakraScrollArea.Content>
            </ChakraScrollArea.Viewport>
            <ChakraScrollArea.Scrollbar>
                <ChakraScrollArea.Thumb />
            </ChakraScrollArea.Scrollbar>
        </ChakraScrollArea.Root>
    );
}
