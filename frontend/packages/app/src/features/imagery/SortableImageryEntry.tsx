// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { useId } from "react";
import { DateTime } from "luxon";
import { SortableLayerEntry } from "@opencop/ui";
import { useTranslation } from "react-i18next";
import { ImageryEntryRow } from "./ImageryEntryRow";
import { ImageryEntryDetails } from "./ImageryEntryDetails";

interface SortableImageryEntryProps {
    id: string;
    name: string;
    kindLabel: string;
    nameTooltip?: ReactNode;
    opacity: number;
    visible: boolean;
    canMoveUp: boolean;
    canMoveDown: boolean;
    onMoveUp: () => void;
    onMoveDown: () => void;
    onOpacityChange: (value: number) => void;
    onVisibleChange: (visible: boolean) => void;
    hasTimeControls: boolean;
    time: string | null;
    timeMin: DateTime | null;
    timeMax: DateTime | null;
    step: string | null;
    onTimeChange: (time: string | null) => void;
    detailsOpen: boolean;
    onDetailsOpenChange: (v: boolean) => void;
}

export function SortableImageryEntry({
    id,
    name,
    kindLabel,
    nameTooltip,
    opacity,
    visible,
    canMoveUp,
    canMoveDown,
    onMoveUp,
    onMoveDown,
    onOpacityChange,
    onVisibleChange,
    hasTimeControls,
    time,
    timeMin,
    timeMax,
    step,
    onTimeChange,
    detailsOpen,
    onDetailsOpenChange,
}: SortableImageryEntryProps) {
    const { t } = useTranslation();
    const detailsId = useId();

    return (
        <SortableLayerEntry
            id={id}
            canMoveUp={canMoveUp}
            canMoveDown={canMoveDown}
            onMoveUp={onMoveUp}
            onMoveDown={onMoveDown}
            dragAriaLabel={t("imagery.overlay.dragToReorder")}
            moveUpAriaLabel={t("imagery.overlay.moveUp")}
            moveDownAriaLabel={t("imagery.overlay.moveDown")}
        >
            <ImageryEntryRow
                name={name}
                nameTooltip={nameTooltip}
                kindLabel={kindLabel}
                visible={visible}
                onVisibleChange={onVisibleChange}
                detailsOpen={detailsOpen}
                detailsId={detailsId}
                onDetailsOpenChange={onDetailsOpenChange}
            />
            <ImageryEntryDetails
                id={detailsId}
                open={detailsOpen}
                name={name}
                opacity={opacity}
                onOpacityChange={onOpacityChange}
                hasTimeControls={hasTimeControls}
                time={time}
                timeMin={timeMin}
                timeMax={timeMax}
                step={step}
                onTimeChange={onTimeChange}
            />
        </SortableLayerEntry>
    );
}
