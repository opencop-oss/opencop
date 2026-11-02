// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Badge } from "@chakra-ui/react";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { STATUS_LABEL_KEY, STATUS_PALETTE } from "./status.const";
import type { RuntimeFeatureRowStatus } from "./runtime-features.rows";

/**
 * Status chip shared by the admin table and the drawer so the two cannot drift apart.
 *
 * `subtle` at the default size is the app's convention for a status badge in an admin table — see
 * the banner status column in `AdminBannersView`. State is carried by palette and label only, never
 * by variant or size, so these read as the same kind of object as every other status badge in the
 * admin surfaces. The label always carries the meaning on its own — colour is never the only
 * channel (WCAG SC 1.4.1).
 */
export function StatusBadge({ status }: { status: RuntimeFeatureRowStatus }): ReactElement {
    const { t } = useTranslation();
    return (
        <Badge colorPalette={STATUS_PALETTE[status]} variant="subtle">
            {t(STATUS_LABEL_KEY[status] as never)}
        </Badge>
    );
}
