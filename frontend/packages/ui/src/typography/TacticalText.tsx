// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Text, type TextProps } from "@chakra-ui/react";
import type { ReactElement } from "react";

export function TacticalText({ children, ...rest }: TextProps): ReactElement {
    return (
        <Text fontFamily="mono" fontSize="xs" {...rest}>
            {children}
        </Text>
    );
}
