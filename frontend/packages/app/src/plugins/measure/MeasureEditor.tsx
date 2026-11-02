// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { HStack, IconButton, Separator, Text, VStack } from "@chakra-ui/react";
import { useToolSessionRevision } from "@opencop/tools";
import { CopyButton, Panel, Tooltip, useDisplayPreferences } from "@opencop/ui";
import { useMemo, type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import type { ParseKeys } from "i18next";
import type { IconType } from "react-icons";
import { PiCircle, PiLineSegments, PiPolygon } from "react-icons/pi";
import {
    useMeasureShape,
    useMeasurement,
    useToggleMeasureShape,
    type MeasureShape,
} from "./measure-session";
import { summarizeMeasurement } from "./measure.util";

interface ShapeDescriptor {
    icon: IconType;
    labelKey: ParseKeys;
    hintKey: ParseKeys;
}

const MEASURE_SHAPES: Record<MeasureShape, ShapeDescriptor> = {
    circle: {
        icon: PiCircle,
        labelKey: "toolbar.measureCircle",
        hintKey: "toolbar.measureCircleHint",
    },
    line: {
        icon: PiLineSegments,
        labelKey: "toolbar.measureLine",
        hintKey: "toolbar.measureLineHint",
    },
    polygon: {
        icon: PiPolygon,
        labelKey: "toolbar.measurePolygon",
        hintKey: "toolbar.measurePolygonHint",
    },
} satisfies Record<MeasureShape, ShapeDescriptor>;

const SHAPE_ORDER = ["circle", "line", "polygon"] as const satisfies readonly MeasureShape[];

function ShapeToggle({ shape }: { shape: MeasureShape }): ReactElement {
    const { t } = useTranslation();
    const { icon: Icon, labelKey, hintKey } = MEASURE_SHAPES[shape];
    const active = useMeasureShape() === shape;
    const toggle = useToggleMeasureShape();

    return (
        <Tooltip content={t(hintKey)} uppercase>
            <IconButton
                size="xs"
                border="none"
                active="iconToggle"
                data-active={active ? "" : undefined}
                aria-pressed={active}
                aria-label={t(labelKey)}
                onClick={() => toggle(shape)}
                flex="1"
            >
                <Icon />
            </IconButton>
        </Tooltip>
    );
}

function MeasureSummary(): ReactElement | null {
    const { t } = useTranslation();
    const measurement = useMeasurement();
    const revision = useToolSessionRevision();
    const { measurementSystem } = useDisplayPreferences();

    const summary = useMemo((): string[] | null => {
        void revision; // geometry is mutable; a revision bump forces a recompute on vertex moves
        if (!measurement) return null;
        return summarizeMeasurement(measurement, measurementSystem);
    }, [measurement, measurementSystem, revision]);

    if (!summary) return null;

    return (
        <>
            <Separator mx="-1" />
            <VStack align="stretch" gap="0">
                {summary.map((line) => (
                    <HStack key={line} justify="space-between" gap="0">
                        <Text fontSize="xs" color="fg.subtle">
                            {line}
                        </Text>
                        <CopyButton
                            value={line}
                            label={t("common.copyToClipboardValue", { value: line })}
                        />
                    </HStack>
                ))}
            </VStack>
        </>
    );
}

export function MeasureEditor(): ReactElement {
    const { t } = useTranslation();
    return (
        <Panel pointerEvents="auto" alignSelf="start">
            <VStack align="stretch" p="1" gap="1">
                {/* Named group: the shape toggles share their labels ("Line", "Circle",
                    "Polygon") with the drawing tools, and both sets are pressable
                    buttons, so assistive tech and tests need the container to say
                    which set this is. */}
                <HStack gap="0" role="group" aria-label={t("toolbar.measure")}>
                    {SHAPE_ORDER.map((shape) => (
                        <ShapeToggle key={shape} shape={shape} />
                    ))}
                </HStack>
                <MeasureSummary />
            </VStack>
        </Panel>
    );
}
