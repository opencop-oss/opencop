// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Heading, type HeadingProps } from "@chakra-ui/react";
import type { ReactElement } from "react";

export function TacticalHeading({ children, ...rest }: HeadingProps): ReactElement {
    return (
        <Heading size="lg" fontFamily="mono" fontWeight="bold" {...rest}>
            {children}
        </Heading>
    );
}
