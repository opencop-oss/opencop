// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, type FlexProps, type HeadingProps } from "@chakra-ui/react";
import type { ReactElement } from "react";
import { AccentHeading } from "./AccentHeading.tsx";

export interface SectionProps extends FlexProps {
    heading: string;
    /** Props for the heading, such as a smaller `size`. */
    headingProps?: HeadingProps;
}

/** A titled block in a panel: an accent heading over its children, stacked. */
export function Section({ heading, headingProps, children, ...rest }: SectionProps): ReactElement {
    return (
        <Flex direction="column" w="full" gap={2} {...rest}>
            <AccentHeading headingProps={headingProps}>{heading}</AccentHeading>
            {children}
        </Flex>
    );
}
