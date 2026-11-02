// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Collapsible, Flex, Text } from "@chakra-ui/react";
import { AccentHeading } from "@opencop/ui";
import { useState, type ReactNode } from "react";
import { TbChevronDown, TbChevronRight } from "react-icons/tb";

export type WatchboxSectionProps = {
    title: string;
    /** Drives the section from outside; omit to let it track its own state. */
    open?: boolean;
    onOpenChange?: (open: boolean) => void;
    children: ReactNode;
    /**
     * "detail" renders the heading with the same accent-heading presentation
     * as the read-only event/watchbox detail panels. Defaults to the create/
     * edit form's muted, bordered heading so the form is unaffected.
     */
    variant?: "form" | "detail";
};

/**
 * A collapsible field group with a title and chevron. Controlled when `open`
 * is supplied, for callers that must force a section open (such as revealing
 * the field a validation error landed on). Self-managing otherwise, starting
 * open.
 */
export function WatchboxSection({
    title,
    open,
    onOpenChange,
    children,
    variant = "form",
}: WatchboxSectionProps) {
    const [selfOpen, setSelfOpen] = useState(true);
    const isOpen = open ?? selfOpen;
    const isDetail = variant === "detail";
    const chevron = isOpen ? <TbChevronDown /> : <TbChevronRight />;

    return (
        <Collapsible.Root
            open={isOpen}
            onOpenChange={(e) => {
                if (open === undefined) setSelfOpen(e.open);
                onOpenChange?.(e.open);
            }}
        >
            <Collapsible.Trigger
                w="full"
                cursor="pointer"
                userSelect="none"
                py={isDetail ? "2" : "1"}
                borderBottomWidth={isDetail ? undefined : "1px"}
                borderColor={isDetail ? undefined : "border"}
            >
                {isDetail ? (
                    <Flex align="center" gap="2" w="full">
                        <AccentHeading rootProps={{ flex: "1" }}>{title}</AccentHeading>
                        <Box color="fg.muted" fontSize="sm" flexShrink={0}>
                            {chevron}
                        </Box>
                    </Flex>
                ) : (
                    <Flex align="center" gap="1">
                        <Text
                            fontSize="xs"
                            fontWeight="600"
                            color="slate.text.muted"
                            textTransform="uppercase"
                        >
                            {title}
                        </Text>
                        <Box color="slate.text.muted" fontSize="sm">
                            {chevron}
                        </Box>
                    </Flex>
                )}
            </Collapsible.Trigger>
            <Collapsible.Content>
                {isDetail ? <Box pt="2">{children}</Box> : children}
            </Collapsible.Content>
        </Collapsible.Root>
    );
}
