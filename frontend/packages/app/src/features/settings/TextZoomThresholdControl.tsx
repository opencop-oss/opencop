// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { HStack, NumberInput, Text, VStack } from "@chakra-ui/react";
import { clamp } from "lodash";
import { useTranslation } from "react-i18next";
import { OcSlider } from "@opencop/ui";
import { ZOOM_MAX, ZOOM_MIN } from "~/features/map/core/slices/view/view-url.util";
import {
    DEFAULT_TEXT_ZOOM_THRESHOLD,
    useMapSettingsStore,
} from "~/features/map/map-settings.store";

const STEP = 0.1;

/** Below this, culling more text onto the screen at once risks stuttery map movement. */
const PERFORMANCE_WARNING_THRESHOLD = DEFAULT_TEXT_ZOOM_THRESHOLD;

export function TextZoomThresholdControl() {
    const { t } = useTranslation();
    const value = useMapSettingsStore((s) => s.textZoomThreshold);

    // A rebuild here costs the same as the symbolSize slider next door — both
    // hit the same unconditional live rebuild in needsRebuild() when nothing is
    // being edited — so, like that slider, this commits on every tick.
    const commit = (next: number) => useMapSettingsStore.getState().setTextZoomThreshold(next);

    const handleNumberInputChange = (details: { valueAsNumber: number }) => {
        if (!Number.isFinite(details.valueAsNumber)) return;
        const snapped = Math.round(details.valueAsNumber / STEP) * STEP;
        commit(clamp(Number(snapped.toFixed(1)), ZOOM_MIN, ZOOM_MAX));
    };

    return (
        <VStack w="full" align="start" gap="1">
            <Text textStyle="sm" fontWeight={500} color="slate.text.muted">
                {t("mapSettings.textZoomThreshold")}
            </Text>
            <Text
                id="text-zoom-threshold-warning"
                textStyle="xs"
                color="fg.warning"
                aria-live="polite"
                visibility={value < PERFORMANCE_WARNING_THRESHOLD ? "visible" : "hidden"}
            >
                {t("mapSettings.textZoomThresholdWarning", {
                    threshold: PERFORMANCE_WARNING_THRESHOLD,
                })}
            </Text>
            <HStack w="full" gap="3">
                <OcSlider
                    ariaLabel={t("mapSettings.textZoomThreshold")}
                    min={ZOOM_MIN}
                    max={ZOOM_MAX}
                    step={STEP}
                    value={value}
                    onChange={commit}
                    thumbProps={{ index: 0, "aria-describedby": "text-zoom-threshold-warning" }}
                    rootProps={{ flex: "1" }}
                />
                {/* A separate always-visible field rather than OcSlider's own
                `editable` (click-to-reveal): this value has a warning threshold
                attached, so it needs to stay legible without a click. */}
                <NumberInput.Root
                    size="xs"
                    value={String(value)}
                    min={ZOOM_MIN}
                    max={ZOOM_MAX}
                    step={STEP}
                    w="20"
                    onValueChange={handleNumberInputChange}
                >
                    <NumberInput.Control />
                    <NumberInput.Input
                        aria-label={t("mapSettings.textZoomThresholdExact")}
                        aria-describedby="text-zoom-threshold-warning"
                    />
                </NumberInput.Root>
            </HStack>
        </VStack>
    );
}
