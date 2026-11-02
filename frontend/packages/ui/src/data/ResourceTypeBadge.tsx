// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { OcTag } from "../forms/OcTag";

export interface ResourceTypeBadgeProps {
    label: string;
    colorPalette: string;
}

export function ResourceTypeBadge({ label, colorPalette }: ResourceTypeBadgeProps): ReactElement {
    return (
        <OcTag size="sm" colorPalette={colorPalette}>
            {label}
        </OcTag>
    );
}
