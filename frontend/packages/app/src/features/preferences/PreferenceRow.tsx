// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Grid, GridItem, Text, VStack } from "@chakra-ui/react";
import type { ReactNode } from "react";

interface PreferenceRowProps {
    /** The label text for this preference */
    label: string;
    /** Optional description text shown below the label */
    description?: string;
    /** The control element (OcSelect, Switch, etc.) */
    children: ReactNode;
    /** Controls vertical alignment - "start" for multi-line labels, "center" for single line */
    alignItems?: "start" | "center";
}

/**
 * Reusable preference row component with consistent layout.
 * Creates a 2-column grid: label on left (with optional description), control on right.
 *
 * @example
 * ```tsx
 * <PreferenceRow label="Language">
 *   <OcSelect items={LANGUAGES} />
 * </PreferenceRow>
 *
 * <PreferenceRow
 *   label="Default Tile"
 *   description="Override app default basemap"
 *   alignItems="start"
 * >
 *   <OcSelect items={tiles} />
 * </PreferenceRow>
 * ```
 */
export function PreferenceRow({
    label,
    description,
    children,
    alignItems = "center",
}: PreferenceRowProps) {
    return (
        <Grid templateColumns="1fr auto" gap={4} alignItems={alignItems}>
            <GridItem>
                {description ? (
                    <VStack align="start" gap={1}>
                        <Text fontWeight="medium" color="fg.muted">
                            {label}
                        </Text>
                        <Text fontSize="sm" color="fg.muted">
                            {description}
                        </Text>
                    </VStack>
                ) : (
                    <Text fontWeight="medium" color="fg.muted">
                        {label}
                    </Text>
                )}
            </GridItem>
            <GridItem width="180px">{children}</GridItem>
        </Grid>
    );
}
