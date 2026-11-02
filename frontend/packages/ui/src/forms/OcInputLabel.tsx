// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Text } from "@chakra-ui/react";
import type { TextProps } from "@chakra-ui/react";
import type { ReactElement } from "react";

export function OcInputLabel({ label, ...rest }: { label: string } & TextProps): ReactElement {
    return (
        <Text color="fg.muted" fontWeight="bold" fontSize="xs" {...rest}>
            {label.toUpperCase()}
        </Text>
    );
}
