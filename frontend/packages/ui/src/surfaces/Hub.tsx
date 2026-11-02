// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { Grid } from "@chakra-ui/react";

interface HubProps {
    children: ReactNode;
}

export function Hub({ children }: HubProps) {
    return (
        <Grid
            templateColumns={{
                base: "repeat(auto-fit, minmax(96px, 1fr))",
                md: "repeat(auto-fit, minmax(160px, 1fr))",
            }}
            gap={{ base: 2, md: 4 }}
            pt={4}
        >
            {children}
        </Grid>
    );
}
