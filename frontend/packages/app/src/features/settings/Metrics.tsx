// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { VStack, Text } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { OcSwitch } from "@opencop/ui";
import { useMetric } from "../map/use-metric.hook";

function formatMetric(metric: string) {
    return metric.replace(/(Pf)/g, " per frame").replace(/Pct/, " %");
}

/** Component for toggling rendering metrics */
export function MetricToggles() {
    const { t } = useTranslation();
    const metrics = useMetric("render");
    if (!metrics) return null;
    const entries = Object.entries(metrics);

    return (
        <VStack w="full" align="start">
            <Text textStyle="sm">{t("mapSettings.metricsDescription")}</Text>
            {entries.map(([key, value]) => (
                <OcSwitch
                    key={key}
                    label={formatMetric(key).toUpperCase()}
                    description={t(`mapSettings.metricDescriptions.${key}` as never)}
                    checked={value}
                    onCheckedChange={() => window.__metrics.toggle("render", key)}
                />
            ))}
        </VStack>
    );
}
