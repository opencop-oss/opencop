// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, Stack, Text } from "@chakra-ui/react";
import type { ReactElement, ReactNode } from "react";

export interface DrawerSideNavItem<V extends string = string> {
    value: V;
    label: ReactNode;
    /** Optional leading icon. */
    icon?: ReactNode;
}

export interface DrawerSideNavProps<V extends string = string> {
    items: DrawerSideNavItem<V>[];
    value: V;
    onChange: (value: V) => void;
    "aria-label"?: string;
}

/**
 * Vertical navigation rail for entity drawers. Renders one tab per section
 * (e.g. Overview / Layers / Data Sources); the active item has no fill — a
 * glowing left accent bar and its icon lit in the active color, mirroring the
 * app's main sidebar. Keyboard support mirrors {@link ResourceTypeFilterTab}
 * but on the vertical axis (Up/Down/Home/End) with a roving tab index.
 */
export function DrawerSideNav<V extends string = string>({
    items,
    value,
    onChange,
    "aria-label": ariaLabel,
}: DrawerSideNavProps<V>): ReactElement {
    return (
        <Stack role="tablist" aria-orientation="vertical" aria-label={ariaLabel} gap={0.5} minW={0}>
            {items.map((item, i) => {
                const isActive = value === item.value;
                return (
                    <Flex
                        as="button"
                        key={item.value}
                        id={`tab-${item.value}`}
                        role="tab"
                        aria-selected={isActive}
                        aria-controls={`panel-${item.value}`}
                        tabIndex={isActive ? 0 : -1}
                        align="center"
                        gap={2.5}
                        w="full"
                        px={3}
                        py={2}
                        textAlign="left"
                        position="relative"
                        borderRadius="xs"
                        bg="transparent"
                        color={isActive ? "active" : "fg.muted"}
                        fontWeight={isActive ? "semibold" : "normal"}
                        cursor="pointer"
                        _hover={{ color: "active" }}
                        _focusVisible={{
                            outline: "2px solid",
                            outlineColor: "active",
                            outlineOffset: "-2px",
                            borderRadius: "sm",
                        }}
                        onClick={() => onChange(item.value)}
                        onKeyDown={(e) => {
                            let nextIndex: number | null = null;
                            if (e.key === "ArrowDown") {
                                nextIndex = (i + 1) % items.length;
                            } else if (e.key === "ArrowUp") {
                                nextIndex = (i - 1 + items.length) % items.length;
                            } else if (e.key === "Home") {
                                nextIndex = 0;
                            } else if (e.key === "End") {
                                nextIndex = items.length - 1;
                            }
                            if (nextIndex !== null) {
                                e.preventDefault();
                                const nextEl = (
                                    e.currentTarget.closest('[role="tablist"]') as HTMLElement
                                )?.querySelectorAll<HTMLElement>('[role="tab"]')[nextIndex];
                                nextEl?.focus();
                                nextEl?.click();
                            }
                        }}
                    >
                        {/* Glowing left accent bar — a drop-shadow glows the bar's shape
                            only (no box outline), mirroring the main sidebar indicator. */}
                        {isActive && (
                            <Box
                                aria-hidden
                                position="absolute"
                                left={0}
                                top={0}
                                bottom={0}
                                w="3px"
                                bg="active"
                                color="active"
                                filter="drop-shadow(0 0 4px currentColor)"
                            />
                        )}
                        {item.icon != null && (
                            <Box
                                fontSize="md"
                                flexShrink={0}
                                display="inline-flex"
                                // Light the icon in the active color when selected.
                                filter={isActive ? "drop-shadow(0 0 4px currentColor)" : undefined}
                            >
                                {item.icon}
                            </Box>
                        )}
                        <Text fontSize="sm" truncate>
                            {item.label}
                        </Text>
                    </Flex>
                );
            })}
        </Stack>
    );
}
