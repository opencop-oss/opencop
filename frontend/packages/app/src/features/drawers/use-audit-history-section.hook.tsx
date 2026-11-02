// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { AuditEntityType } from "@opencop/api";
import { DrawerSectionHeader, type DrawerSideNavSection } from "@opencop/ui";
import { useTranslation } from "react-i18next";
import { FaHistory } from "react-icons/fa";
import { useIsAdmin } from "~/features/auth/use-is-admin.hook";
import { AuditHistorySection } from "./AuditHistorySection";

/**
 * The audit-history drawer section for one resource, or null when the viewer may not read it.
 *
 * A hook rather than a plain builder because the gate is part of the section: the ledger endpoint
 * is global-administrator only, so a non-admin must get no section at all instead of one that can
 * only render an error. Label, icon and description are defined here so callers cannot diverge.
 */
export function useAuditHistorySection(
    entityType: AuditEntityType,
    entityId: string,
): DrawerSideNavSection | null {
    const { t } = useTranslation();
    const isAdmin = useIsAdmin();

    if (!isAdmin) return null;

    return {
        key: "auditHistory",
        label: t("audit.sectionLabel"),
        icon: <FaHistory />,
        content: (
            <>
                <DrawerSectionHeader
                    title={t("audit.sectionLabel")}
                    description={t("drawerSections.description.auditHistory")}
                />
                <AuditHistorySection entityType={entityType} entityId={entityId} />
            </>
        ),
    };
}
