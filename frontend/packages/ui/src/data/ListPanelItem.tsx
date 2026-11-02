// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { Box, Flex, HStack } from "@chakra-ui/react";
import { TacticalText } from "../typography/TacticalText";
import { interactiveCardStyles } from "../surfaces/interactive-card.util";
import { Tooltip } from "../overlays/Tooltip";

export interface ListPanelItemProps {
    /** Row index — controls alternating background. */
    index: number;
    /** Whether this item is the active/selected one. */
    isSelected: boolean;
    /** Disables interaction (grayed out, not focusable). */
    disabled?: boolean;
    /** Called when the item is clicked or activated via keyboard. */
    onClick: () => void;
    /** Accessible label for screen readers. */
    ariaLabel: string;
    /** Content rendered absolutely in the top-right corner on hover (checkboxes, buttons, etc.). */
    actions?: ReactNode;
    /** When true, actions are always visible. When false (default), shown on hover only. */
    actionsVisible?: boolean;
    /** Content rendered in a right-justified row below the main body, in normal document flow. */
    bottomSlot?: ReactNode;
    /**
     * Always-visible content rendered at the right edge, vertically centred across the main
     * row and `bottomSlot`, mirroring `leftSlot`.
     */
    rightSlot?: ReactNode;
    /**
     * Always-visible content rendered at the left edge, vertically centred across the main
     * row and `bottomSlot`, so it sits beside both lines rather than above or below them.
     */
    leftSlot?: ReactNode;
    /** Override the auto-built tooltip (title · subtitle · detail). Pass a ReactNode for rich, structured content. */
    tooltipContent?: ReactNode;
    /** Primary display name. */
    title: string;
    /** Optional rich rendering for the primary display name. The string title remains the accessible/tooltip value. */
    titleContent?: ReactNode;
    /** Optional badge rendered next to the title (e.g. ResourceTypeBadge). */
    badge?: ReactNode;
    /** Optional first body line — shown in tooltip on hover. */
    subtitle?: string;
    /** Optional second body line — shown in tooltip on hover. */
    detail?: string;
    /** Use compact height instead of default. */
    compact?: boolean;
}

// A slot's own controls handle their clicks. The row button is one of them: it activates the
// row itself, so the card skips its clicks rather than activating the row twice.
const NESTED_CONTROL_SELECTOR = [
    "button",
    "a[href]",
    "input",
    "select",
    "textarea",
    "label",
    '[role="button"]',
    '[role="checkbox"]',
    '[role="switch"]',
    '[role="menuitem"]',
].join(",");

function isNestedControl(target: EventTarget, card: HTMLElement): boolean {
    if (!(target instanceof Element)) return false;
    const control = target.closest(NESTED_CONTROL_SELECTOR);
    return control !== null && control !== card && card.contains(control);
}

/**
 * Interactive list card used across panel lists (imagery tiles, map captures, terrain tiles).
 * Provides consistent hover/selected styles, keyboard navigation, a11y, content layout,
 * and an optional actions slot that appears on hover.
 *
 * The row is a native `<button>` wrapping only the title, so it never contains another
 * control: screen readers walk each slot's buttons, checkboxes and links as siblings of the
 * row button, not as its descendants. That button carries the accessible name, `aria-pressed`
 * and keyboard activation. A mouse click anywhere else on the card also activates the row,
 * unless it lands on one of the slots' own controls.
 */
export function ListPanelItem({
    index,
    isSelected,
    disabled,
    onClick,
    ariaLabel,
    actions,
    actionsVisible = false,
    bottomSlot,
    rightSlot,
    leftSlot,
    tooltipContent: tooltipContentProp,
    title,
    titleContent,
    badge,
    subtitle,
    detail,
    compact,
}: ListPanelItemProps) {
    const tooltipContent =
        tooltipContentProp ?? [title, subtitle, detail].filter(Boolean).join(" · ");

    const cardProps = {
        "data-panel-item": true,
        css: interactiveCardStyles(index, isSelected),
        cursor: disabled ? "not-allowed" : "pointer",
        opacity: disabled ? 0.4 : 1,
        // Mouse convenience for the whole card; keyboard and assistive tech reach the same
        // action through the row button below. Clicks on a slot's own control are left to it.
        onClick: (e: React.MouseEvent<HTMLElement>) => {
            if (disabled || isNestedControl(e.target, e.currentTarget)) return;
            onClick();
        },
        _hover: disabled ? {} : undefined,
    };

    const titleRow = (
        <Tooltip content={tooltipContent} uppercase={false}>
            <Flex
                as="button"
                data-panel-item-button=""
                // Not native `disabled`: that drops the pointer events the tooltip trigger needs.
                tabIndex={disabled ? -1 : 0}
                aria-pressed={isSelected}
                aria-disabled={disabled}
                aria-label={ariaLabel}
                onClick={() => {
                    if (!disabled) onClick();
                }}
                alignItems="center"
                gap="2"
                minW={0}
                flex={1}
                overflow="hidden"
                bg="transparent"
                borderWidth="0"
                p="0"
                textAlign="start"
                color="inherit"
                font="inherit"
                cursor="inherit"
                _focusVisible={{ outline: "none" }}
            >
                <TacticalText
                    as="span"
                    color="bg.contrast"
                    fontWeight="600"
                    overflow="hidden"
                    textOverflow="ellipsis"
                    whiteSpace="nowrap"
                    minW={0}
                >
                    {titleContent ?? title}
                </TacticalText>
                {badge && (
                    <Box as="span" flexShrink={0}>
                        {badge}
                    </Box>
                )}
            </Flex>
        </Tooltip>
    );

    const actionsEl = actions && (
        <Flex
            position="absolute"
            top="2"
            right="2"
            alignItems="center"
            gap="3"
            zIndex="1"
            opacity={actionsVisible ? 1 : 0}
            transition="opacity 0.15s ease-out"
            css={{
                "[data-panel-item]:hover &, [data-panel-item]:focus-within &": { opacity: 1 },
            }}
        >
            {actions}
        </Flex>
    );

    const body = (
        <>
            <Flex
                w="full"
                pl={leftSlot ? "0" : "3"}
                pr={rightSlot ? "0" : "3"}
                py={compact ? "1" : "3"}
                alignItems="center"
                gap="3"
                position="relative"
            >
                {titleRow}
            </Flex>
            {bottomSlot && (
                <HStack
                    w="full"
                    justify="flex-end"
                    pl={leftSlot ? "0" : compact ? "3" : "2"}
                    pr={rightSlot ? "0" : compact ? "3" : "2"}
                    pb={compact ? "1" : "2"}
                    gap="3"
                >
                    {bottomSlot}
                </HStack>
            )}
        </>
    );

    return (
        <Box {...cardProps}>
            {leftSlot || rightSlot ? (
                <Flex alignItems="center" gap="3">
                    {leftSlot && (
                        <Flex flexShrink={0} alignItems="center" pl="3">
                            {leftSlot}
                        </Flex>
                    )}
                    <Box flex="1" minW={0}>
                        {body}
                    </Box>
                    {rightSlot && (
                        <Flex flexShrink={0} alignItems="center" gap="2" pr="3">
                            {rightSlot}
                        </Flex>
                    )}
                </Flex>
            ) : (
                body
            )}
            {/* After the row content so Tab reaches the row button first; absolutely positioned, so it still sits top-right. */}
            {actionsEl}
        </Box>
    );
}
