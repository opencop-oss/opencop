// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Heading, type BoxProps, type HeadingProps } from "@chakra-ui/react";
import { Divider, type DividerProps } from "./Divider";
import type { ReactElement, ReactNode } from "react";

export interface ActiveHeadingProps {
    rootProps?: BoxProps;
    headingProps?: HeadingProps;
    dividerProps?: DividerProps;
    children?: ReactNode;
}

export function ActiveHeading({
    rootProps,
    headingProps,
    dividerProps,
    children,
}: ActiveHeadingProps): ReactElement {
    return (
        <Box w="full" {...rootProps}>
            <Heading
                size="2xl"
                letterSpacing="2px"
                fontWeight="bold"
                color="active"
                textAlign="start"
                {...headingProps}
            >
                {children?.toString().toUpperCase()}
            </Heading>
            <Divider {...dividerProps} />
        </Box>
    );
}
