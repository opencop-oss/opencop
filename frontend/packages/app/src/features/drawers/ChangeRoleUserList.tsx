// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, IconButton, Stack, Text } from "@chakra-ui/react";
import type { ReactElement } from "react";
import { useId } from "react";
import { useTranslation } from "react-i18next";
import { FaExclamationCircle, FaExclamationTriangle, FaMinus, FaPlus } from "react-icons/fa";
import { TbUser } from "react-icons/tb";
import { Tooltip, scrollbarStyles } from "@opencop/ui";
import type { ChangeRoleRow } from "./change-role.model";

/**
 * One side of the change-role shuttle: a titled list of members with a per-row
 * button that moves each member between the "to update" and "excluded (no change)"
 * sides. The `update` side flags any blocked member (they must be moved out before
 * Apply enables); the `excluded` side is always safe, so it never flags.
 */
export function ChangeRoleUserList({
    title,
    warning,
    rows,
    variant,
    blockedRowIds,
    blockedTooltip,
    emptyLabel,
    flexSize,
    onMove,
}: {
    title: string;
    /** Optional caution shown to the right of the title (e.g. an owner-downgrade warning). */
    warning?: string;
    rows: ChangeRoleRow[];
    variant: "update" | "excluded";
    blockedRowIds: Set<string>;
    blockedTooltip?: string;
    emptyLabel: string;
    /** Flex-grow share of the resizable container (maps to `flex: "<n> 1 0px"`). */
    flexSize: number;
    /** Moves a member: `toUpdate=false` excludes them, `true` includes them. */
    onMove: (id: string, toUpdate: boolean) => void;
}): ReactElement {
    const { t } = useTranslation();
    // Names the list region below so AT distinguishes "Selected users" from "Excluded"
    // (confusing the two is a permissions mistake — one list's members get re-roled).
    const titleId = useId();
    return (
        <Stack gap={1.5} minH="90px" overflow="hidden" style={{ flex: `${flexSize} 1 0px` }}>
            <Flex align="center" gap={2} flexShrink={0}>
                <Text id={titleId} fontSize="sm" fontWeight="medium" flexShrink={0}>
                    {title}
                </Text>
                {warning && (
                    <Flex align="center" gap={1} color="fg.warning" minW={0} ml="auto">
                        <Box flexShrink={0} display="inline-flex" fontSize="xs">
                            <FaExclamationTriangle />
                        </Box>
                        <Text fontSize="xs" fontStyle="italic" lineHeight="short">
                            {warning}
                        </Text>
                    </Flex>
                )}
            </Flex>
            {rows.length === 0 ? (
                <Text fontSize="xs" color="fg.muted" px={1} py={1} flexShrink={0}>
                    {emptyLabel}
                </Text>
            ) : (
                <Box
                    role="list"
                    aria-labelledby={titleId}
                    flex="1 1 0"
                    minH={0}
                    overflowY="auto"
                    pr={1}
                    css={scrollbarStyles}
                >
                    {rows.map((row) => {
                        const flagged = variant === "update" && blockedRowIds.has(row.id);
                        const icon = (
                            <Box
                                position="relative"
                                color={flagged ? "fg.warning" : "fg.muted"}
                                fontSize="lg"
                                flexShrink={0}
                                display="inline-flex"
                            >
                                <TbUser />
                                {flagged && (
                                    <Box
                                        position="absolute"
                                        bottom="-3px"
                                        right="-4px"
                                        fontSize="2xs"
                                        lineHeight="1"
                                        color="fg.warning"
                                        bg="bg.panel"
                                        borderRadius="full"
                                    >
                                        <FaExclamationCircle />
                                    </Box>
                                )}
                            </Box>
                        );
                        // Plain list row (not a table — there are no columns/headers to
                        // announce). A bottom border gives the same visual separators.
                        return (
                            <Flex
                                key={row.id}
                                role="listitem"
                                align="center"
                                gap={2}
                                py={2}
                                borderBottomWidth="1px"
                                borderColor="border.muted"
                            >
                                <Flex align="center" gap={2} minW={0} flex="1">
                                    {flagged && blockedTooltip ? (
                                        <Tooltip content={blockedTooltip}>
                                            {/* tabIndex so keyboard/AT users can focus the flag
                                                and hear why the row is blocked (Ark only sets
                                                aria-describedby once the trigger is focused). */}
                                            <Box
                                                as="span"
                                                display="inline-flex"
                                                tabIndex={0}
                                                aria-label={blockedTooltip}
                                            >
                                                {icon}
                                            </Box>
                                        </Tooltip>
                                    ) : (
                                        icon
                                    )}
                                    <Box minW={0}>
                                        <Text fontSize="sm" fontWeight="medium" truncate>
                                            {row.label}
                                        </Text>
                                        <Text fontSize="xs" color="fg.muted" truncate>
                                            {t("adminDialogs.currentRole", { role: row.role })}
                                        </Text>
                                    </Box>
                                </Flex>
                                <IconButton
                                    size="xs"
                                    // `plain` = no fill and no hover background box; the icon
                                    // color is the affordance (red to remove, the active accent
                                    // to add). On hover the icon glows its own color via a
                                    // drop-shadow (no rectangular box).
                                    variant="plain"
                                    color={variant === "update" ? "red.fg" : "active"}
                                    _hover={{ filter: "drop-shadow(0 0 4px currentColor)" }}
                                    aria-label={t(
                                        variant === "update"
                                            ? "adminDialogs.excludeUser"
                                            : "adminDialogs.includeUser",
                                        { name: row.label },
                                    )}
                                    onClick={() => onMove(row.id, variant === "excluded")}
                                >
                                    {variant === "update" ? <FaMinus /> : <FaPlus />}
                                </IconButton>
                            </Flex>
                        );
                    })}
                </Box>
            )}
        </Stack>
    );
}
