// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Popover, Portal, Text } from "@chakra-ui/react";
import { type ReactNode, useId } from "react";
import { PopoverPanel } from "../surfaces/PopoverPanel.tsx";

export interface SettingsPopoverProps {
    /** Title displayed at the top of the popover body */
    title: string;
    /** Popover placement relative to the trigger (default: "bottom") */
    placement?: "bottom" | "bottom-start" | "bottom-end" | "top" | "top-start" | "top-end";
    /**
     * Must include a `<Popover.Trigger>` (or `<PopoverTrigger>`) as one of the
     * children so that Chakra can anchor the popover to the trigger element.
     */
    children: ReactNode;
    /** Content rendered inside the popover body, below the title. */
    content: ReactNode;
}

/**
 * Glass-styled settings popover.
 *
 * The consumer provides the trigger element as a child (wrapped in
 * `<Popover.Trigger asChild>`) and the popover body content via the
 * `content` prop.
 */
export function SettingsPopover({
    title,
    children,
    content,
    placement = "bottom",
}: SettingsPopoverProps) {
    const titleId = useId();

    return (
        <Popover.Root positioning={{ placement }}>
            {children}
            <Portal>
                <Popover.Positioner>
                    <PopoverPanel aria-labelledby={titleId} p="0">
                        <Popover.Body p="3">
                            <Text id={titleId} fontSize="xs" fontWeight="medium" mb="3">
                                {title}
                            </Text>
                            {content}
                        </Popover.Body>
                    </PopoverPanel>
                </Popover.Positioner>
            </Portal>
        </Popover.Root>
    );
}
