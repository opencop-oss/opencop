// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Drawer, Flex, HStack, Stack, Text } from "@chakra-ui/react";
import type { ReactElement, ReactNode } from "react";

export interface EntityDrawerHeaderProps {
    name: string;
    /** Optional entity type label, rendered as a muted subtitle under the name. Omit for no subtitle. */
    badgeLabel?: string;
    /** Optional type icon rendered to the left of the name, Azure-style. */
    icon?: ReactNode;
    /** Optional action controls (e.g. an icon toolbar) rendered on the right of the header. */
    actions?: ReactNode;
}

/**
 * Shared drawer header: type icon + name + muted subtitle on the left, an
 * optional action toolbar on the right. Used by {@link DrawerSideNavLayout} so
 * the header markup stays in one place.
 */
export function EntityDrawerHeader({
    name,
    badgeLabel,
    icon,
    actions,
}: EntityDrawerHeaderProps): ReactElement {
    return (
        <Drawer.Header borderBottomWidth="1px" borderBottomColor="border.muted">
            <Flex align="center" justify="space-between" gap={2} flex={1} minW={0} minH={9}>
                <Flex align="center" gap={3} minW={0} flex={1}>
                    {icon && (
                        <Flex align="center" color="fg.muted" fontSize="2xl" flexShrink={0}>
                            {icon}
                        </Flex>
                    )}
                    <Stack gap={0.5} minW={0}>
                        <Drawer.Title fontSize="xl" fontWeight="bold" lineHeight="1.2" truncate>
                            {name}
                        </Drawer.Title>
                        {badgeLabel ? (
                            <Text fontSize="sm" color="fg.muted" truncate>
                                {badgeLabel}
                            </Text>
                        ) : null}
                    </Stack>
                </Flex>
                {actions && (
                    <HStack gap={1} flexShrink={0}>
                        {actions}
                    </HStack>
                )}
            </Flex>
        </Drawer.Header>
    );
}
