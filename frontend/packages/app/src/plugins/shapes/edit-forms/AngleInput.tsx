// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useGeometrySubscription } from "@opencop/ingest";
import type { AngleElement, Position2D } from "@opencop/elements";
import { Angle } from "@opencop/elements";
import { Checkbox, Field, HStack, NumberInput, VisuallyHidden, VStack } from "@chakra-ui/react";
import { MercatorCoordinate } from "maplibre-gl";
import type { TFunction } from "i18next";
import { useId } from "react";
import { useTranslation } from "react-i18next";
import * as turf from "@turf/turf";
import {
    OcSelect,
    CoordinateInputField,
    useCoordinateInput,
    useDisplayPreferences,
} from "@opencop/ui";
import { useNumericInput } from "./use-numeric-input.hook.ts";
import { useUnitState } from "./use-unit-state.hook.ts";
import { bearingForAngle, compassBearing, normalizeBearing } from "./angle-input.util.ts";
import {
    MILS_PER_DEGREE,
    formatDistance,
    fromMeters,
    toMeters,
    type AngleUnit as MapAngleUnit,
    type DistanceUnit,
    type MeasurementSystem,
} from "@opencop/utils";

/*
 * Some helpers below (LENGTH_UNIT_ITEMS, defaultLengthUnit, NumberField, LabeledSelect) overlap
 * with CircleInput. Deliberately kept local rather than lifted to a shared module: CircleInput
 * is the only sibling event input that shares any of it — the others (Point/Arrow/Motion/
 * *ThresholdInput) have no unit list or numeric-geometry field — so a shared abstraction would
 * dedupe exactly two call sites and add module surface to do it. Merge the two onto shared helpers
 * if a third geometry input ever needs the same pattern. The hooks already went the other way:
 * `useNumericInput` and `useUnitState` are consumed by CircleInput too, so both sit in their own
 * files.
 */

/** The subset of {@link MapAngleUnit} a single numeric field can show (excludes the combined form). */
type AngleValueUnit = Exclude<MapAngleUnit, "both">;

const LENGTH_UNIT_ITEMS: { label: string; value: DistanceUnit }[] = [
    { label: "m", value: "m" },
    { label: "km", value: "km" },
    { label: "mi", value: "mi" },
    { label: "ft", value: "ft" },
];

const formatInteger = (v: number) => Math.round(v).toString();
const formatOneDecimal = (v: number) => v.toFixed(1);

function angleUnitItems(t: TFunction): { label: string; value: AngleValueUnit }[] {
    return [
        { label: t("mapSettings.angleUnitsShort.degrees"), value: "degrees" },
        { label: t("mapSettings.angleUnitsShort.mils"), value: "mils" },
    ];
}

function defaultLengthUnit(meters: number, system: MeasurementSystem): DistanceUnit {
    const unit = formatDistance(meters, system).unit;
    return unit === "km" || unit === "mi" || unit === "ft" ? unit : "m";
}

/** `readOnly`: the operator may not change the geometry; values still show and can be copied. */
export function AngleInput({
    element,
    readOnly = false,
}: {
    element: AngleElement;
    readOnly?: boolean;
}) {
    const { t } = useTranslation();
    const {
        measurementSystem: system,
        angleUnit: mapAngleUnit,
        angleOrientation: mapOrientation,
    } = useDisplayPreferences();
    useGeometrySubscription(element);

    // Geometry is [arm0, vertex, arm1]; incomplete until all three points are placed.
    const complete = element.geometry.numPositions >= 3;
    const vertexLngLat = complete ? element.geometry.getLngLat(1) : undefined;
    const vertex = complete ? (element.geometry.getPos(1) as Position2D) : undefined;
    const arm0 = complete ? (element.geometry.getPos(0) as Position2D) : undefined;
    const arm1 = complete ? (element.geometry.getPos(2) as Position2D) : undefined;

    const leg0Meters = vertex && arm0 ? turf.distance(vertex, arm0, { units: "meters" }) : 0;
    const leg1Meters = vertex && arm1 ? turf.distance(vertex, arm1, { units: "meters" }) : 0;
    const leg0Bearing = vertex && arm0 ? compassBearing(vertex, arm0) : 0;
    const leg1Bearing = vertex && arm1 ? compassBearing(vertex, arm1) : 0;
    // Full [0, 360) directional sweep, matching the arc AngleLayer draws on the map (not the
    // direction-agnostic [0, 180] interior angle from Angle.getAngle). Collinear arms read "360.0"
    // under cw and "0.0" under ccw (float noise on a zero sweep); left alone because the map's arc
    // label derives from the same expression, so collapsing it only here would desync the two.
    const angleDegrees = complete ? Angle.getDirectionalAngle(element, mapOrientation) : 0;
    // A fact about the geometry, not a stored flag: a map drag that breaks equality automatically
    // unchecks and re-enables Segment 2 rather than leaving a stale "matched" claim on screen.
    // Full-precision metres, not the display unit — two segments both rendering `3` in `mi` differ.
    const matched = Math.abs(leg0Meters - leg1Meters) < 0.5; // metres; floats from turf.distance
    // Link intent, held alongside `matched` because a derived boolean has no unset path: an
    // unchecking click would write nothing and the controlled `checked` would snap straight back.
    // `&& matched` keeps the map-drag auto-uncheck; Segment 2's own setter clears the intent, so
    // the field can never re-lock under an edit.
    const [linked, setLinked] = useUnitState<"on" | "off">(`${element.key}:link`, "off");
    const lengthsLinked = linked === "on" && matched;

    const [lengthUnit, setLengthUnit] = useUnitState<DistanceUnit>(
        `${element.key}:${system}`,
        defaultLengthUnit(Math.max(leg0Meters, leg1Meters), system),
    );
    const [angleUnit, setAngleUnit] = useUnitState<AngleValueUnit>(
        `${element.key}:${mapAngleUnit}`,
        mapAngleUnit === "mils" ? "mils" : "degrees",
    );

    const vertexInput = useCoordinateInput(vertexLngLat, (v) => {
        // Defensive: unreachable once `complete` gates rendering (below) — kept for TS narrowing.
        if (!vertexLngLat) return;
        const from = MercatorCoordinate.fromLngLat(vertexLngLat);
        const to = MercatorCoordinate.fromLngLat({ lng: v.lng, lat: v.lat });
        element.geometry.translate(to.x - from.x, to.y - from.y);
    });
    const { error: vertexError, ...vertexInputProps } = vertexInput;

    /** Move arm `index` (0 or 2) to `meters` away from the vertex along `bearing` (deg CW from N). */
    function placeArm(index: 0 | 2, meters: number, bearing: number) {
        // `!vertex` is defensive — unreachable once `complete` gates rendering (below), kept for
        // TS narrowing. `meters <= 0` is reachable: it guards a zero-length segment from
        // collapsing the arm onto the vertex, which would otherwise lose its bearing.
        if (!vertex || meters <= 0) return;
        const dest = turf.destination(vertex, meters, bearing, { units: "meters" });
        element.geometry.setPos(dest.geometry.coordinates as Position2D, index);
    }

    // Each leg field rotates or extends its own arm around the vertex; the other arm is left
    // untouched, so the angle re-derives from the new geometry. Exception: while segment lengths
    // are matched, editing segment 1's length drives segment 2's arm to the same distance.
    const leg0Length = useNumericInput(
        fromMeters(leg0Meters, lengthUnit),
        (v) => {
            const meters = toMeters(v, lengthUnit);
            placeArm(0, meters, leg0Bearing);
            if (lengthsLinked) placeArm(2, meters, leg1Bearing);
        },
        formatInteger,
    );
    const leg1Length = useNumericInput(
        fromMeters(leg1Meters, lengthUnit),
        (v) => {
            // Editing segment 2 is only possible while the link is released, so this drops the
            // intent that a map drag left behind — otherwise retyping equality re-latches the
            // field mid-edit and eats the keystroke that reached it.
            setLinked("off");
            placeArm(2, toMeters(v, lengthUnit), leg1Bearing);
        },
        formatInteger,
    );
    const leg0BearingInput = useNumericInput(
        leg0Bearing,
        (v) => placeArm(0, leg0Meters, normalizeBearing(v)),
        formatOneDecimal,
    );
    const leg1BearingInput = useNumericInput(
        leg1Bearing,
        (v) => placeArm(2, leg1Meters, normalizeBearing(v)),
        formatOneDecimal,
    );

    // Vertex angle: arm 0 stays as the fixed reference leg; arm 1 rotates (keeping its length)
    // to hit the target directional angle, per the map's cw/ccw angle orientation setting.
    const displayAngle = angleUnit === "mils" ? angleDegrees * MILS_PER_DEGREE : angleDegrees;
    const angleInput = useNumericInput(
        displayAngle,
        (v) => {
            // Defensive: unreachable once `complete` gates rendering (below) — kept for TS narrowing.
            if (!vertex || !arm0) return;
            const target = (((angleUnit === "mils" ? v / MILS_PER_DEGREE : v) % 360) + 360) % 360;
            const arm0Bearing = turf.bearing(vertex, arm0);
            placeArm(2, leg1Meters, bearingForAngle(arm0Bearing, target, mapOrientation));
        },
        formatOneDecimal,
    );

    const angleUnits = angleUnitItems(t);

    if (!complete) return null;

    return (
        <>
            <CoordinateInputField
                label={t("coordinates.position")}
                error={vertexError}
                showFormatSelector
                readOnly={readOnly}
                {...vertexInputProps}
            />

            <VStack w="full" gap="1.5" alignItems="stretch">
                <HStack w="full" alignItems="end">
                    <NumberField
                        label={t("coordinates.segment1")}
                        field={leg0Length}
                        readOnly={readOnly}
                    />

                    <NumberField
                        label={t("coordinates.segment2")}
                        field={leg1Length}
                        readOnly={readOnly || lengthsLinked}
                        readOnlyHint={
                            lengthsLinked ? t("coordinates.segment2LinkedHint") : undefined
                        }
                    />

                    <LabeledSelect
                        label={t("coordinates.unit")}
                        value={lengthUnit}
                        items={LENGTH_UNIT_ITEMS}
                        onChange={setLengthUnit}
                    />
                </HStack>

                <Checkbox.Root
                    checked={lengthsLinked}
                    readOnly={readOnly}
                    onCheckedChange={(e) => {
                        setLinked(e.checked === true ? "on" : "off");
                        if (e.checked === true) placeArm(2, leg0Meters, leg1Bearing);
                    }}
                >
                    <Checkbox.HiddenInput />
                    <Checkbox.Control borderColor="fg.muted" />
                    <Checkbox.Label fontSize="sm" fontWeight="normal" color="fg.muted">
                        {t("coordinates.matchLengths")}
                    </Checkbox.Label>
                </Checkbox.Root>
            </VStack>

            <HStack w="full" alignItems="end">
                <NumberField
                    grow
                    label={t("coordinates.segment1Bearing")}
                    field={leg0BearingInput}
                    readOnly={readOnly}
                />
                <NumberField
                    grow
                    label={t("coordinates.segment2Bearing")}
                    field={leg1BearingInput}
                    readOnly={readOnly}
                />
            </HStack>

            <HStack w="full" alignItems="end">
                <NumberField
                    grow
                    label={t("coordinates.angle")}
                    field={angleInput}
                    readOnly={readOnly}
                />
                <LabeledSelect
                    grow
                    label={t("coordinates.angleUnit")}
                    value={angleUnit}
                    items={angleUnits}
                    onChange={setAngleUnit}
                />
            </HStack>
        </>
    );
}

function NumberField({
    label,
    field,
    grow,
    readOnly,
    readOnlyHint,
}: {
    label: string;
    field: ReturnType<typeof useNumericInput>;
    /** Grow to share the row's width evenly with sibling `grow` fields. */
    grow?: boolean;
    /**
     * Render non-editable — e.g. a value driven by another field. Deliberately `readOnly` rather
     * than `disabled`: the value is still meaningful, so it stays focusable, copyable, and in the
     * tab order, and keeps normal text contrast.
     */
    readOnly?: boolean;
    /** Why the field is read-only. Announced via `aria-describedby` while `readOnly` is set. */
    readOnlyHint?: string;
}) {
    const inputId = useId();
    const hintId = useId();
    const describedBy = readOnly && readOnlyHint ? hintId : undefined;
    return (
        <Field.Root flex={grow ? "1" : undefined} readOnly={readOnly}>
            <Field.Label htmlFor={inputId} color="fg.muted" textStyle="xs">
                {label}
            </Field.Label>
            <NumberInput.Root
                ids={{ input: inputId }}
                min={0}
                readOnly={readOnly}
                width={grow ? "full" : undefined}
                {...field}
            >
                {!readOnly && <NumberInput.Control />}
                <NumberInput.Input aria-describedby={describedBy} />
            </NumberInput.Root>
            {describedBy && <VisuallyHidden id={describedBy}>{readOnlyHint}</VisuallyHidden>}
        </Field.Root>
    );
}

function LabeledSelect<T extends string>({
    label,
    value,
    items,
    onChange,
    grow,
}: {
    label: string;
    value: T;
    items: { label: string; value: T }[];
    onChange: (value: T) => void;
    /** Grow to share the row's width evenly with sibling `grow` fields. */
    grow?: boolean;
}) {
    return (
        <Field.Root flex={grow ? "1" : undefined}>
            <Field.Label color="fg.muted" textStyle="xs" gap="1">
                {label}
            </Field.Label>
            <OcSelect
                value={value}
                items={items}
                size="md"
                variant="outline"
                controlProps={{ bg: "transparent" }}
                onValueChange={(v) => onChange(v as T)}
            />
        </Field.Root>
    );
}
