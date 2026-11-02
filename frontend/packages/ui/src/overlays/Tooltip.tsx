// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Tooltip as ChakraTooltip, Portal } from "@chakra-ui/react";
import * as React from "react";

export interface TooltipProps extends ChakraTooltip.RootProps {
    portalled?: boolean;
    portalRef?: React.RefObject<HTMLElement>;
    content: React.ReactNode;
    contentProps?: ChakraTooltip.ContentProps;
    positionerProps?: React.ComponentProps<typeof ChakraTooltip.Positioner>;
    disabled?: boolean;
    uppercase?: boolean;
}

export function Tooltip({
    ref,
    children,
    disabled,
    portalled = true,
    content,
    contentProps,
    positionerProps,
    portalRef,
    openDelay = 300,
    uppercase = true,
    ...rest
}: TooltipProps & { ref?: React.Ref<HTMLDivElement> }) {
    if (disabled) return children;

    return (
        <ChakraTooltip.Root openDelay={openDelay} {...rest}>
            <ChakraTooltip.Trigger asChild>{children}</ChakraTooltip.Trigger>
            <Portal disabled={!portalled} container={portalRef}>
                <ChakraTooltip.Positioner zIndex={9999} {...positionerProps}>
                    <ChakraTooltip.Content
                        ref={ref}
                        bg="bg.glass"
                        fontWeight="bold"
                        backdropFilter="blur(4px)"
                        borderWidth="1px"
                        borderColor="border"
                        borderRadius="sm"
                        color="fg.muted"
                        {...contentProps}
                    >
                        {uppercase ? content?.toString().toUpperCase() : content}
                    </ChakraTooltip.Content>
                </ChakraTooltip.Positioner>
            </Portal>
        </ChakraTooltip.Root>
    );
}
