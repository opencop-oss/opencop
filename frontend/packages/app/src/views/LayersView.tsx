// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useTranslation } from "react-i18next";
import { MyLayers } from "~/features/layers/MyLayers";
import { PageLayout } from "~/layouts/PageLayout";

export function LayersView() {
    const { t } = useTranslation();
    return (
        <PageLayout
            title={t("layers.title")}
            subtitle={t("layers.subtitle")}
            componentName="MyLayers"
        >
            <MyLayers />
        </PageLayout>
    );
}
