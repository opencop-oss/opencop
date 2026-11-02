// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { HStack, Text } from "@chakra-ui/react";
import { CopyButton, DetailTable, type DetailTableRow } from "@opencop/ui";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { layerQueries } from "~/features/layers/layers.queries";

export interface IdentityProps {
    identity: {
        displayName: string;
        objectId: string;
        id: string;
        kind: string;
        source: string;
        version: string;
    };
}

// Fields that should have copy buttons
const copyableFields = ["id", "objectId"];

function formatLabel(key: string): string {
    // Convert camelCase to UPPER CASE WITH SPACES
    return key
        .replace(/([A-Z])/g, " $1")
        .trim()
        .toUpperCase();
}

export function Identity({ identity }: IdentityProps) {
    const { t } = useTranslation();
    const { data: layerName } = useQuery({
        ...layerQueries.myList(),
        select: (layers) => layers.find((info) => info.layerId === identity.source)?.layerName,
    });
    const { data: sourceName } = useQuery({
        ...dataSourceQueries.myList(),
        select: (sources) => sources.find((info) => info.serverId === identity.source)?.serverName,
    });
    const sourceDisplay = layerName ?? sourceName ?? identity.source;

    const rows: DetailTableRow[] = Object.entries(identity).map(([key, value]) => {
        const label = t(`events.field${key.charAt(0).toUpperCase() + key.slice(1)}` as never, {
            defaultValue: formatLabel(key),
        });
        return {
            key,
            label,
            value: (
                <HStack justify="space-between">
                    <Text color="bg.contrast" textStyle="xs">
                        {key === "source" ? sourceDisplay : value}
                    </Text>
                    {/* Named by field: the rows' buttons would otherwise share one name. */}
                    {copyableFields.includes(key) && (
                        <CopyButton
                            value={value}
                            label={t("common.copyToClipboardValue", { value: label })}
                        />
                    )}
                </HStack>
            ),
        };
    });

    return <DetailTable rows={rows} />;
}
