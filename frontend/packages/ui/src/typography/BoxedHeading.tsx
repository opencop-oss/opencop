// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Heading, type BoxProps, type HeadingProps } from "@chakra-ui/react";
import type { ReactElement, ReactNode } from "react";

export interface BoxedHeadingProps {
    rootProps?: BoxProps;
    headingProps?: HeadingProps;
    children?: ReactNode;
}

export function BoxedHeading({
    rootProps,
    headingProps,
    children,
}: BoxedHeadingProps): ReactElement {
    return (
        <Box
            p="1"
            w="full"
            bg="bg.surface"
            borderWidth="1px"
            borderColor="border"
            borderRadius="sm"
            {...rootProps}
        >
            <Heading
                size="2xl"
                fontWeight="extrabold"
                color="active"
                textAlign="start"
                {...headingProps}
            >
                {children?.toString().toUpperCase()}
            </Heading>
        </Box>
    );
}
