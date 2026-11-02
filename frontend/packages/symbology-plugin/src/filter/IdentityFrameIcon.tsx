// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box } from "@chakra-ui/react";
import { AffiliationFrameIcon } from "@opencop/ui";
import type { StandardIdentityFrame } from "@opencop/symbology";

export type IdentityFrameIconProps = {
    frame: StandardIdentityFrame;
    color: string;
};

/**
 * The affiliation frame for one standard identity, tinted to its affiliation colour.
 * Decorative — the option's label carries the meaning.
 */
export function IdentityFrameIcon({ frame, color }: IdentityFrameIconProps) {
    return (
        <Box as="span" aria-hidden="true" color={color} display="inline-flex" w="5" flexShrink={0}>
            <AffiliationFrameIcon frame={frame} />
        </Box>
    );
}
