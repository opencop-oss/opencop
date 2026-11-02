// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { Link } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { Link as RouterLink, useLocation } from "react-router";
import { Breadcrumbs, type BreadcrumbItem } from "@opencop/ui";

const SEGMENT_LABELS = {
    users: "adminNav.users",
    layers: "adminNav.layers",
    "data-sources": "adminNav.dataSource",
    mappers: "adminNav.mappers",
    imagery: "adminNav.imagery",
    banners: "adminNav.banners",
    "runtime-features": "adminNav.runtimeFeatures",
} as const;

type AdminSegment = keyof typeof SEGMENT_LABELS;

function isAdminSegment(s: string): s is AdminSegment {
    return s in SEGMENT_LABELS;
}

function renderLink({ href, children }: { href: string; children: ReactNode }) {
    return (
        <Link
            asChild
            fontSize="sm"
            color="active"
            cursor="pointer"
            _hover={{ textDecoration: "underline" }}
            display="inline-flex"
            alignItems="center"
            gap={1}
        >
            <RouterLink to={href}>{children}</RouterLink>
        </Link>
    );
}

export function AdminBreadcrumbs() {
    const { t } = useTranslation();
    const { pathname } = useLocation();

    const segments = pathname.split("/").filter(Boolean);
    const sectionSegment = segments[1] ?? null;

    const items: BreadcrumbItem[] = [{ label: t("adminPanel.title") }];

    if (sectionSegment) {
        items[0].href = "/admin";
        const label = isAdminSegment(sectionSegment)
            ? t(SEGMENT_LABELS[sectionSegment])
            : sectionSegment;
        items.push({ label });
    }

    return <Breadcrumbs items={items} renderLink={renderLink} />;
}
