// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Heading, type FlexProps, type HeadingProps } from "@chakra-ui/react";
import type { ReactElement, ReactNode } from "react";
import { Divider } from "./Divider.tsx";

export interface AccentHeadingProps {
    rootProps?: FlexProps;
    headingProps?: HeadingProps;
    children?: ReactNode;
    accent?: boolean;
}

export function AccentHeading({ children, ...rest }: AccentHeadingProps): ReactElement {
    return (
        <Flex align="center" gap="2" w="full" {...rest.rootProps}>
            <Heading
                size="sm"
                fontWeight="bold"
                letterSpacing="2px"
                color="fg"
                whiteSpace="nowrap"
                flexShrink={0}
                {...rest.headingProps}
                userSelect="none"
            >
                {typeof children === "string" ? children.toUpperCase() : children}
            </Heading>
            {(rest.accent ?? true) ? <Divider /> : null}
        </Flex>
    );
}
