// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { HStack, IconButton, Popover, Portal, useDisclosure } from "@chakra-ui/react";
import type { ReactNode } from "react";
import { LuEllipsisVertical } from "react-icons/lu";
import { Tooltip } from "../overlays/Tooltip.tsx";
import { PopoverPanel } from "./PopoverPanel.tsx";

interface OverflowPopoverProps {
    /** Accessible name and tooltip for the "more" trigger. */
    label: string;
    /**
     * Renders the hidden items. `close` dismisses the popover, for items that
     * should close it once chosen.
     */
    renderItems: (close: () => void) => ReactNode;
    /** Extra attributes for the trigger, e.g. a `data-*` hook for focus restoration. */
    triggerProps?: { [dataAttr: `data-${string}`]: string };
}

/**
 * The "more" trigger that reveals items an {@link OverflowContainer} could not fit,
 * shown in a popover opened from a vertical-ellipsis button. Closed content stays
 * mounted under the `hidden` attribute, so items in it cannot take focus.
 */
export function OverflowPopover({ label, renderItems, triggerProps }: OverflowPopoverProps) {
    const { open, onClose, onToggle } = useDisclosure();

    return (
        <Popover.Root
            open={open}
            // Controlled, so Escape reaches onOpenChange and must be routed to onClose.
            onOpenChange={(d) => {
                if (!d.open) onClose();
            }}
            onInteractOutside={onClose}
            positioning={{ placement: "bottom-end" }}
        >
            <Popover.Trigger asChild>
                <IconButton
                    {...triggerProps}
                    size={{ base: "compact", lg: "md" }}
                    border="none"
                    active={open ? "icon" : "iconHover"}
                    aria-label={label}
                    onClick={onToggle}
                >
                    <Tooltip content={label} uppercase={false}>
                        <LuEllipsisVertical />
                    </Tooltip>
                </IconButton>
            </Popover.Trigger>
            <Portal>
                <Popover.Positioner>
                    <PopoverPanel p="1">
                        <HStack gap="0">{renderItems(onClose)}</HStack>
                    </PopoverPanel>
                </Popover.Positioner>
            </Portal>
        </Popover.Root>
    );
}
