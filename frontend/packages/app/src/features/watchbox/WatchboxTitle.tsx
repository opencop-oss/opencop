// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Heading } from "@chakra-ui/react";
import { EntityIconBox, Tooltip } from "@opencop/ui";
import { TbRadar } from "react-icons/tb";
import type { ReactNode } from "react";

export type WatchboxTitleProps = {
    title: string;
    /** Secondary content rendered on its own row beneath the name, e.g. status badges. */
    children?: ReactNode;
};

/**
 * Icon-plus-name heading shared by the watchbox panels. Deliberately mirrors
 * the event detail panel's header proportions, sharing the icon frame via
 * `EntityIconBox`.
 */
export function WatchboxTitle({ title, children }: WatchboxTitleProps) {
    return (
        <Flex direction="column" gap="3" pb="3">
            <Flex align="center" gap="3">
                <EntityIconBox color="active" fontSize="xl">
                    <TbRadar />
                </EntityIconBox>
                <Flex direction="column" gap="0.5" flex={1} minWidth={0}>
                    <Tooltip content={title} uppercase={false}>
                        <Heading
                            size="xl"
                            letterSpacing="1px"
                            fontWeight="extrabold"
                            color="bg.contrast"
                            truncate
                            minWidth={0}
                        >
                            {title.toUpperCase()}
                        </Heading>
                    </Tooltip>
                    {children ? (
                        <Flex align="center" gap="1" wrap="wrap">
                            {children}
                        </Flex>
                    ) : null}
                </Flex>
            </Flex>
        </Flex>
    );
}
