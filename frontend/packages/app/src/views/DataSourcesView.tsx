// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useTranslation } from "react-i18next";
import { MyDataSources } from "~/features/data-sources/MyDataSources";
import { PageLayout } from "~/layouts/PageLayout";

export function DataSourcesView() {
    const { t } = useTranslation();
    return (
        <PageLayout
            title={t("dataSources.title")}
            subtitle={t("dataSources.subtitle")}
            componentName="MyDataSources"
        >
            <MyDataSources />
        </PageLayout>
    );
}
