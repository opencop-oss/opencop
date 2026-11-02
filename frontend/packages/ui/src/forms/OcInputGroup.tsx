// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type ReactElement, type ReactNode } from "react";
import { Flex, Group, type FlexProps, type GroupProps } from "@chakra-ui/react";
import { OcInputLabel } from "./OcInputLabel";

interface OcInputGroupProps {
    label?: string;
    labelEnd?: ReactNode;
    start?: ReactNode;
    children?: ReactNode[] | ReactNode;
    invalid?: boolean;
    rootProps?: FlexProps;
    groupProps?: GroupProps;
}

export function OcInputGroup({
    label,
    start,
    children,
    invalid,
    labelEnd,
    ...rest
}: OcInputGroupProps): ReactElement {
    return (
        <Flex w="full" flexDir="column" gap="1" {...rest.rootProps}>
            <Flex justify="space-between">
                <OcInputLabel label={label ?? ""} />
                {labelEnd}
            </Flex>
            <Group
                w="full"
                bg="bg.subtle"
                borderRadius="2px"
                borderColor={invalid ? "red.400" : "border.muted"}
                borderWidth="1px"
                attached
                {...rest.groupProps}
            >
                {start}
                {children}
            </Group>
        </Flex>
    );
}
