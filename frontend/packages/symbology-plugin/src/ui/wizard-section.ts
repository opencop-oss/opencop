// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { SectionProps } from "@opencop/ui";

/** How the symbol wizards lay out a `Section`: wider spacing, centred, a smaller heading. */
export const WIZARD_SECTION = {
    gap: "4",
    alignItems: "center",
    headingProps: { size: "xs" },
} as const satisfies Partial<SectionProps>;
