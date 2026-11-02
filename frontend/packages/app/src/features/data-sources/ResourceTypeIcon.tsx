// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { TypeColorLabel } from "@opencop/ui";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { ENTITY_BADGE_CONFIG } from "./data-resources.util";
import type { ResourceEntityType } from "./data-resources.util";

interface ResourceTypeIconProps {
    type: ResourceEntityType;
}

/**
 * The colored type chip (DS / M) for a data-source-family entity, used as a drawer header
 * icon. Single source of truth for the per-type chip and its size.
 */
export function ResourceTypeIcon({ type }: ResourceTypeIconProps): ReactElement {
    const { t } = useTranslation();
    const config = ENTITY_BADGE_CONFIG[type];
    return (
        <TypeColorLabel
            abbr={config.abbr}
            color={config.color}
            label={t(config.labelKey)}
            size="md"
        />
    );
}
