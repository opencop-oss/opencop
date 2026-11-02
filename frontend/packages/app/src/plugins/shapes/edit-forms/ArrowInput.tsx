// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useGeometrySubscription } from "@opencop/ingest";
import { CoordinateInputField, useCoordinateInput } from "@opencop/ui";
import { VStack } from "@chakra-ui/react";
import { Arrow, type ArrowElement } from "@opencop/elements";
import { useTranslation } from "react-i18next";
import { MercatorCoordinate } from "maplibre-gl";

/** `readOnly`: the operator may not change the geometry; values still show and can be copied. */
export function ArrowInput({
    element,
    readOnly = false,
}: {
    element: ArrowElement;
    readOnly?: boolean;
}) {
    const { t } = useTranslation();
    useGeometrySubscription(element);
    const hasCompleteGeometry = element.geometry.numPositions >= 2;

    const midpoint = hasCompleteGeometry ? Arrow.midpoint(element) : null;
    const center = midpoint ? { lng: midpoint[0], lat: midpoint[1] } : undefined;
    const start = hasCompleteGeometry ? element.geometry.getLngLat(0) : undefined;
    const end = hasCompleteGeometry ? element.geometry.getLngLat(1) : undefined;

    const centerInput = useCoordinateInput(center, (v) => {
        if (!hasCompleteGeometry || !center) return;

        const currentCenter = MercatorCoordinate.fromLngLat(center);
        const nextCenter = MercatorCoordinate.fromLngLat({ lng: v.lng, lat: v.lat });

        element.geometry.translate(nextCenter.x - currentCenter.x, nextCenter.y - currentCenter.y);
    });
    const { error: centerError, ...centerInputProps } = centerInput;

    const startInput = useCoordinateInput(start, (v) => {
        if (!hasCompleteGeometry) return;
        element.geometry.setPos([v.lng, v.lat], 0);
    });
    const { error: startError, ...startInputProps } = startInput;

    const endInput = useCoordinateInput(end, (v) => {
        if (!hasCompleteGeometry) return;
        element.geometry.setPos([v.lng, v.lat], 1);
    });
    const { error: endError, ...endInputProps } = endInput;

    if (!hasCompleteGeometry) return null;

    return (
        <VStack w="full" gap="3">
            <CoordinateInputField
                label={t("coordinates.centralLocation")}
                error={centerError}
                readOnly={readOnly}
                showFormatSelector
                {...centerInputProps}
            />

            <CoordinateInputField
                label={t("coordinates.start")}
                error={startError}
                readOnly={readOnly}
                {...startInputProps}
            />

            <CoordinateInputField
                label={t("coordinates.end")}
                error={endError}
                readOnly={readOnly}
                {...endInputProps}
            />
        </VStack>
    );
}
