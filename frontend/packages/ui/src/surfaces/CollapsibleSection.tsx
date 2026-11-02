// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, HStack, IconButton, Text } from "@chakra-ui/react";
import { useId, type ReactNode } from "react";
import { TbChevronDown } from "react-icons/tb";
import { scrollbarStyles } from "./scrollbar.util";

export interface CollapsibleSectionProps {
    label: string;
    /** aria-label for the toggle button. */
    ariaLabel: string;
    isOpen: boolean;
    onToggle: () => void;
    /**
     * Numeric flex value for the outer wrapper when open.
     * Maps to `style={{ flex: "<flexSize> 1 0px" }}`.
     * When closed the wrapper collapses to `"0 0 auto"`.
     */
    flexSize: number;
    /** Minimum section height (px) when open. Defaults to 90. */
    minSectionH?: number;
    /** Optional controls rendered on the right side of the section header. */
    rightSlot?: ReactNode;
    /**
     * Makes the content Box a flex column container so children can use flex-fill
     * (e.g. a ResourceTable that needs to own its scroll container for sticky headers).
     */
    contentFlex?: boolean;
    children: ReactNode;
}

export function CollapsibleSection({
    label,
    ariaLabel,
    isOpen,
    onToggle,
    flexSize,
    minSectionH = 90,
    rightSlot,
    contentFlex = false,
    children,
}: CollapsibleSectionProps) {
    const contentId = useId();

    return (
        <Flex
            direction="column"
            overflow="hidden"
            minH={isOpen ? `${minSectionH}px` : "0px"}
            style={{ flex: isOpen ? `${flexSize} 1 0px` : "0 0 auto" }}
        >
            <HStack
                flexShrink={0}
                justify="space-between"
                px="1"
                borderBottom={!isOpen ? "1px solid" : "none"}
                borderColor="slate.borderBottomColor.muted"
            >
                <HStack className="group">
                    <IconButton
                        size="xs"
                        variant="plain"
                        onClick={onToggle}
                        aria-expanded={isOpen}
                        aria-controls={contentId}
                        aria-label={ariaLabel}
                        color="brand.100"
                        _groupHover={{ color: "brand.50" }}
                        rotate={isOpen ? "0deg" : "-90deg"}
                        transition="all 0.2s ease"
                    >
                        <TbChevronDown />
                    </IconButton>
                    <Text
                        textStyle="md"
                        color="brand.100"
                        _groupHover={{ color: "brand.50" }}
                        transition="all 0.2s ease"
                    >
                        {label}
                    </Text>
                </HStack>
                {rightSlot}
            </HStack>

            {isOpen && (
                <Box
                    id={contentId}
                    flex="1 1 0"
                    minH="0"
                    overflowY="auto"
                    display={contentFlex ? "flex" : undefined}
                    flexDirection={contentFlex ? "column" : undefined}
                    css={{ ...scrollbarStyles, scrollbarGutter: contentFlex ? "auto" : "stable" }}
                >
                    {children}
                </Box>
            )}
        </Flex>
    );
}
