// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { HStack, Text } from "@chakra-ui/react";
import { CopyButton, DetailTable, type DetailTableRow, CoordFormatSelect } from "@opencop/ui";
import { useTranslation } from "react-i18next";
import { useCoordFormat } from "~/features/coordinates/CoordFormatContext";
import { formatCoord } from "@opencop/utils";

export interface CoordinateDisplayProps {
    lat: number;
    lng: number;
    label?: string;
}

export function CoordinateDisplay({ lat, lng, label }: CoordinateDisplayProps) {
    const { t } = useTranslation();
    const { format, setFormat } = useCoordFormat();

    const formattedCoords = formatCoord(lat, lng, format);

    const rows: DetailTableRow[] = [
        {
            key: "position",
            label: label ?? t("events.fieldPosition"),
            value: (
                <HStack justify="space-between">
                    <Text color="bg.contrast" textStyle="xs">
                        {formattedCoords}
                    </Text>
                    <HStack gap="1">
                        <CoordFormatSelect value={format} onChange={setFormat} variant="inline" />
                        <CopyButton value={formattedCoords} />
                    </HStack>
                </HStack>
            ),
        },
    ];

    return <DetailTable rows={rows} />;
}
