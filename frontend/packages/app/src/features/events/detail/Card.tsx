// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, HStack, VStack, type BoxProps, type StackProps } from "@chakra-ui/react";
import type { ReactElement, ReactNode } from "react";
import { Label } from "./Label";

interface CardProps {
    label: string;
    endElement?: ReactNode;
    children?: ReactNode;
    rootProps?: BoxProps;
    stackProps?: StackProps;
}

export function Card(props: CardProps): ReactElement {
    return (
        <Box
            p="2"
            w="full"
            borderRadius="sm"
            borderWidth=".5px"
            borderColor="border"
            {...props.rootProps}
        >
            <VStack align="start" {...props.stackProps}>
                <HStack w="full" justify="space-between">
                    <Label>{props.label}</Label>
                    {props.endElement}
                </HStack>
                {props.children}
            </VStack>
        </Box>
    );
}
