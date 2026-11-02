// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box } from "@chakra-ui/react";
import type { DetailTableRow } from "@opencop/ui";
import { useTranslation } from "react-i18next";
import { formatDateMilitary } from "~/date.util";
import { useUserLabel } from "~/features/users/use-user-label.hook";
import { LastEventInfoTooltip } from "./LastEventInfoTooltip";
import type { LastEventSubject } from "./LastEventInfoTooltip";

/** The audit metadata an admin entity may carry; every field is absent on pre-audit records. */
export interface AuditFields {
    createdAt?: string | null;
    createdBy?: string | null;
    updatedAt?: string | null;
    updatedBy?: string | null;
    lastEventAt?: string | null;
}

/**
 * Builds the created/updated/last-event rows for an entity, resolving the `createdBy`/`updatedBy`
 * user ids to usernames.
 *
 * A row whose value is absent is omitted rather than rendered as a dash, so a record predating
 * audit tracking shows nothing instead of a column of placeholders. Every timestamp renders at
 * the same granularity, which is the property that drifted while each drawer built these by hand.
 *
 * Pass `lastEventSubject` when the entity has `lastEventAt` and the resource type needs the
 * tooltip explaining what advances it; omitting it renders the plain label.
 */
export function useAuditRows(
    entity: AuditFields,
    timezone?: string,
    lastEventSubject?: LastEventSubject,
): DetailTableRow[] {
    const { t } = useTranslation();
    const createdByLabel = useUserLabel(entity.createdBy);
    const updatedByLabel = useUserLabel(entity.updatedBy);

    return [
        ...(entity.createdAt
            ? [
                  {
                      key: "createdAt",
                      label: t("common.createdAt"),
                      value: formatDateMilitary(entity.createdAt, timezone),
                  },
              ]
            : []),
        ...(createdByLabel
            ? [{ key: "createdBy", label: t("common.createdBy"), value: createdByLabel }]
            : []),
        ...(entity.updatedAt
            ? [
                  {
                      key: "updatedAt",
                      label: t("common.updatedAt"),
                      value: formatDateMilitary(entity.updatedAt, timezone),
                  },
              ]
            : []),
        ...(updatedByLabel
            ? [{ key: "updatedBy", label: t("common.updatedBy"), value: updatedByLabel }]
            : []),
        ...(entity.lastEventAt
            ? [
                  {
                      key: "lastEventAt",
                      label: lastEventSubject ? (
                          <Box as="span" display="inline-flex" alignItems="center" gap={1}>
                              <span>{t("common.lastEventAt")}</span>
                              <LastEventInfoTooltip subject={lastEventSubject} />
                          </Box>
                      ) : (
                          t("common.lastEventAt")
                      ),
                      value: formatDateMilitary(entity.lastEventAt, timezone),
                  },
              ]
            : []),
    ];
}
