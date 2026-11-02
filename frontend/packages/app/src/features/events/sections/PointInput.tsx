// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useGeometrySubscription } from "@opencop/ingest";
import { CoordinateInputField, useCoordinateInput } from "@opencop/ui";
import type { Element, PointGeometry } from "@opencop/elements";
import { useTranslation } from "react-i18next";

/** `readOnly`: the operator may not change the geometry; values still show and can be copied. */
export function PointInput({
    element,
    readOnly = false,
}: {
    element: Element<PointGeometry>;
    readOnly?: boolean;
}) {
    const { t } = useTranslation();
    useGeometrySubscription(element);
    const currentLngLat = element.geometry.getLngLat(0);
    const res = useCoordinateInput(currentLngLat, (v) => {
        element.geometry.setPos([v.lng, v.lat], 0);
    });
    const { error, ...inputProps } = res;

    return (
        <CoordinateInputField
            label={t("coordinates.position")}
            error={error}
            showFormatSelector
            readOnly={readOnly}
            {...inputProps}
        />
    );
}
