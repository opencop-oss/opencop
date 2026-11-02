// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { ThemesPage } from "~/features/themes/ThemesPage";
import { PageLayout } from "~/layouts/PageLayout";

export function ThemeView() {
    return (
        <PageLayout
            title="Themes"
            subtitle="Component showcase with live examples and color annotations"
            componentName="ThemesPage"
        >
            <ThemesPage />
        </PageLayout>
    );
}
