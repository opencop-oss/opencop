// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { Heading } from "@chakra-ui/react";

export function PanelSectionHeading({ children }: { children: ReactNode }) {
    return (
        <Heading
            as="h3"
            size="xs"
            color="fg.muted"
            textTransform="uppercase"
            letterSpacing="wide"
            mb="2"
        >
            {children}
        </Heading>
    );
}
