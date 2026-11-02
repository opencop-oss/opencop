// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useGeometrySubscription } from "@opencop/ingest";
import type { CircleGeometry, Element } from "@opencop/elements";
import { useTranslation } from "react-i18next";
import { useNumericInput } from "./use-numeric-input.hook.ts";
import { useUnitState } from "./use-unit-state.hook.ts";
import { Field, HStack, NumberInput } from "@chakra-ui/react";
import { useId } from "react";
import {
    OcSelect,
    CoordinateInputField,
    useCoordinateInput,
    useDisplayPreferences,
} from "@opencop/ui";
import {
    formatDistance,
    fromMeters,
    toMeters,
    type DistanceUnit,
    type MeasurementSystem,
} from "@opencop/utils";

const UNIT_ITEMS: { label: string; value: DistanceUnit }[] = [
    { label: "m", value: "m" },
    { label: "km", value: "km" },
    { label: "mi", value: "mi" },
    { label: "ft", value: "ft" },
];

function getDefaultRadiusUnit(radiusMeters: number, system: MeasurementSystem): DistanceUnit {
    const unit = formatDistance(radiusMeters, system).unit;

    switch (unit) {
        case "km":
        case "mi":
        case "ft":
            return unit;
        default:
            return "m";
    }
}

// Round rather than floor: `m`/`km` do round-trip exactly (`setRadius` stores metres verbatim),
// but `ft`/`mi` take a conversion round-trip through turf's lossy `convertLength`
// (250 ft -> 76.19999756 m -> 249.99999999999997 ft), which would floor a whole unit off the
// value the user just typed.
const formatRadius = (v: number) => Math.round(v).toString();

/** `readOnly`: the operator may not change the geometry; values still show and can be copied. */
export function CircleInput({
    element,
    readOnly = false,
}: {
    element: Element<CircleGeometry>;
    readOnly?: boolean;
}) {
    const { t } = useTranslation();
    const { measurementSystem: system } = useDisplayPreferences();
    useGeometrySubscription(element);
    const lngLat = element.geometry.getLngLat(0);
    const geometryRadius = element.geometry.getRadius();
    const res = useCoordinateInput(lngLat, (v) => {
        element.geometry.setPos([v.lng, v.lat], 0);
    });
    const { error, ...inputProps } = res;
    const [unit, setUnit] = useUnitState<DistanceUnit>(
        `${element.key}:${system}`,
        getDefaultRadiusUnit(geometryRadius, system),
    );

    const radiusInputId = useId();
    const radiusInput = useNumericInput(
        fromMeters(geometryRadius, unit),
        (v) => element.geometry.setRadius(toMeters(v, unit)),
        formatRadius,
    );

    return (
        <>
            <CoordinateInputField
                label={t("coordinates.position")}
                error={error}
                showFormatSelector
                readOnly={readOnly}
                {...inputProps}
            />

            <HStack w="full" alignItems="end">
                <Field.Root readOnly={readOnly}>
                    <Field.Label htmlFor={radiusInputId} color="fg.muted" textStyle="xs">
                        {t("coordinates.radius")}
                    </Field.Label>
                    <NumberInput.Root
                        ids={{ input: radiusInputId }}
                        min={0}
                        readOnly={readOnly}
                        {...radiusInput}
                    >
                        {!readOnly && <NumberInput.Control />}
                        <NumberInput.Input />
                    </NumberInput.Root>
                </Field.Root>

                <Field.Root>
                    <Field.Label color="fg.muted" textStyle="xs">
                        {t("coordinates.unit")}
                    </Field.Label>
                    <OcSelect
                        value={unit}
                        items={UNIT_ITEMS}
                        size="md"
                        variant="outline"
                        controlProps={{ bg: "transparent" }}
                        onValueChange={(v) => setUnit(v as DistanceUnit)}
                    />
                </Field.Root>
            </HStack>
        </>
    );
}
