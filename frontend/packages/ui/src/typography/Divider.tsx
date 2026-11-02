// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, type BoxProps } from "@chakra-ui/react";
import type { ReactElement } from "react";

export interface DividerProps extends BoxProps {
    orientation?: "horizontal" | "vertical";
}

export function Divider({ orientation, ...rest }: DividerProps): ReactElement {
    return !orientation ? (
        <Box w="full" h="1px" bg="border" {...rest} />
    ) : (
        <Box w="1px" h="full" bg="border" {...rest} />
    );
}
