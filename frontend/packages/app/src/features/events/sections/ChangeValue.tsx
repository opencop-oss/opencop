// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Text, Square, VStack } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { useCoordFormat } from "~/features/coordinates/CoordFormatContext";
import { formatCoord } from "@opencop/utils";
import { isColorValue } from "./history.util";

export function ChangeValue({ value }: { value: unknown }) {
    const { t } = useTranslation();
    const { format } = useCoordFormat();
    if (typeof value === "string" && isColorValue(value)) {
        return (
            <Square
                size="4"
                borderRadius="sm"
                bg={value}
                border="1px solid"
                borderColor="whiteAlpha.300"
            />
        );
    }

    // Handle coordinate arrays - check if it's a flat array of [lng, lat] pairs
    if (Array.isArray(value) && value.length > 0) {
        let coordArray: number[][] | null = null;

        if (
            (value.length === 2 || value.length === 3) &&
            typeof value[0] === "number" &&
            typeof value[1] === "number"
        ) {
            coordArray = [value as number[]];
        } else {
            let candidateArray: unknown[] = value;

            // Polygon: [[[lon, lat], [lon, lat], ...]]
            const firstItem = value[0];
            if (Array.isArray(firstItem) && firstItem.length > 0 && Array.isArray(firstItem[0])) {
                // Extract the outer ring for display
                candidateArray = firstItem as unknown[];
            }

            const firstCoord = candidateArray[0];
            const isCoordArray =
                Array.isArray(firstCoord) &&
                firstCoord.length >= 2 &&
                typeof firstCoord[0] === "number" &&
                typeof firstCoord[1] === "number";

            if (isCoordArray) {
                coordArray = candidateArray as number[][];
            }
        }

        if (coordArray) {
            const shouldTruncate = coordArray.length > 3;
            const displayCoords = shouldTruncate ? coordArray.slice(0, 3) : coordArray;

            return (
                <VStack gap="4px" maxW="280px" align="stretch">
                    {displayCoords.map((coord, idx: number) => {
                        const lng = coord[0];
                        const lat = coord[1];
                        const formattedCoord = formatCoord(lat, lng, format);

                        return (
                            <Flex
                                // eslint-disable-next-line @eslint-react/no-array-index-key -- coordinates, position is identity
                                key={idx}
                                align="center"
                                gap={2}
                                px={2}
                                py={1}
                                bg="whiteAlpha.100"
                                borderRadius="sm"
                                fontSize="xs"
                                fontFamily="mono"
                            >
                                <Text fontWeight={600}>{formattedCoord}</Text>
                            </Flex>
                        );
                    })}
                    {shouldTruncate && (
                        <Text fontSize="xs" opacity={0.5} fontStyle="italic" pl={2}>
                            {t("events.moreCoordinates", { count: coordArray.length - 3 })}
                        </Text>
                    )}
                </VStack>
            );
        }

        // Fallback for simple arrays or deeply nested (Polygon with holes, etc.)
        const displayValue = `[${value
            .slice(0, 4)
            .map((v) => {
                if (Array.isArray(v)) return "[...]";
                return typeof v === "number" ? v.toFixed(4) : v;
            })
            .join(
                ", ",
            )}${value.length > 4 ? ` ${t("events.moreItems", { count: value.length - 4 })}` : ""}]`;

        return (
            <Text
                fontSize="xs"
                fontFamily="mono"
                fontWeight={500}
                maxW="200px"
                overflow="hidden"
                textOverflow="ellipsis"
                whiteSpace="nowrap"
            >
                {displayValue}
            </Text>
        );
    }

    return (
        <Text fontSize="xs" fontFamily="mono" fontWeight={500}>
            {String(value)}
        </Text>
    );
}
