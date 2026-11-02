// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Text, type TextProps } from "@chakra-ui/react";
import type { ReactElement } from "react";

export type LabelProps = TextProps;

export function Label(props: TextProps): ReactElement {
    return (
        <Text
            color="fg.muted"
            fontWeight="bold"
            letterSpacing="1px"
            textTransform="uppercase"
            fontSize="2xs"
        >
            {props.children}
        </Text>
    );
}
