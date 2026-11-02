// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import type { ParseKeys } from "i18next";
import { useTranslation } from "react-i18next";
import { useMatches, Outlet } from "react-router";
import { PageLayout } from "~/layouts/PageLayout";

interface BreadcrumbLayoutProps {
    titleKey: ParseKeys;
    defaultSubtitleKey: ParseKeys;
    componentName: string;
    subNavigation?: ReactNode;
}

export function BreadcrumbLayout({
    titleKey,
    defaultSubtitleKey,
    componentName,
    subNavigation,
}: BreadcrumbLayoutProps) {
    const { t } = useTranslation();
    const matches = useMatches();

    const handleMatch = matches
        .slice()
        .reverse()
        .find((m) => (m.handle as { subtitleKey?: string } | undefined)?.subtitleKey)?.handle as
        | { subtitleKey?: ParseKeys }
        | undefined;

    return (
        <PageLayout
            title={t(titleKey)}
            subtitle={t(handleMatch?.subtitleKey ?? defaultSubtitleKey)}
            componentName={componentName}
            subNavigation={subNavigation}
        >
            <Outlet />
        </PageLayout>
    );
}
