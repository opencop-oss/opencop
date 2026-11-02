// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo } from "react";
import {
    Accordion,
    Button,
    HStack,
    VStack,
    Text,
    NumberInput,
    Heading,
    Menu,
    Portal,
} from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { OcInputGroup, OcSlider, OcSwatch, OcSwitch, ScrollArea } from "@opencop/ui";
import { useMapSettingsStore, type AngleUnit } from "../map/map-settings.store";
import { useTextSettings } from "~/features/contributions/element-mapper-registry";
import { MetricToggles } from "./Metrics";
import { TextZoomThresholdControl } from "./TextZoomThresholdControl";
import type { AngleOrientation } from "@opencop/layers";

const ANGLE_UNITS: AngleUnit[] = ["degrees", "mils", "both"];
const ANGLE_ORIENTATIONS: AngleOrientation[] = ["ccw", "cw"];

export function MapSettings() {
    const { t } = useTranslation();
    const settings = useMapSettingsStore((s) => s);
    const textSettings = useTextSettings();
    const textTypes = useMemo(() => textSettings.map(({ type }) => type), [textSettings]);
    const setAccordion = useMapSettingsStore.getState().setAccordion;
    const angleUnitLabels = useMemo<Record<AngleUnit, string>>(
        () => ({
            degrees: t("mapSettings.angleUnits.degrees"),
            mils: t("mapSettings.angleUnits.mils"),
            both: t("mapSettings.angleUnits.both"),
        }),
        [t],
    );

    const angleOrientationLabels = useMemo<Record<AngleOrientation, string>>(
        () => ({
            ccw: t("mapSettings.angleOrientations.ccw"),
            cw: t("mapSettings.angleOrientations.cw"),
        }),
        [t],
    );

    return (
        <ScrollArea>
            <Accordion.Root
                multiple
                value={settings.accordion}
                onValueChange={(e) => setAccordion(() => e.value)}
            >
                <Accordion.Item value="general">
                    <Accordion.ItemTrigger>
                        <Text>{t("common.general")}</Text>
                        <Accordion.ItemIndicator />
                    </Accordion.ItemTrigger>
                    <Accordion.ItemContent>
                        <Accordion.ItemBody>
                            <VStack w="full" gap="4" align="start">
                                <OcInputGroup
                                    label={t("mapSettings.outlineColor")}
                                    groupProps={{ bg: "none", border: "none" }}
                                >
                                    <OcSwatch
                                        alpha={false}
                                        label={t("mapSettings.outlineColor")}
                                        value={settings.outlineColor}
                                        onChange={(v) =>
                                            useMapSettingsStore.getState().setOutlineColor(v)
                                        }
                                    />
                                </OcInputGroup>
                                <OcSlider
                                    label={t("mapSettings.symbolSize")}
                                    min={5}
                                    max={50}
                                    defaultValue={settings.symbolSize}
                                    step={0.1}
                                    onChange={(v) =>
                                        useMapSettingsStore.getState().setSymbolSize(v)
                                    }
                                    units="px"
                                />
                            </VStack>
                        </Accordion.ItemBody>
                    </Accordion.ItemContent>
                </Accordion.Item>
                <Accordion.Item value="text">
                    <Accordion.ItemTrigger>
                        <Text>{t("mapSettings.text")}</Text>
                        <Accordion.ItemIndicator />
                    </Accordion.ItemTrigger>
                    <Accordion.ItemContent>
                        <Accordion.ItemBody>
                            <VStack w="full" gap="4">
                                <VStack w="full" align="start">
                                    <Heading size="md">{t("mapSettings.sizing")}</Heading>
                                    <HStack w="full" justify="space-between">
                                        <Text
                                            textStyle="sm"
                                            fontWeight={500}
                                            color="slate.text.muted"
                                        >
                                            {t("mapSettings.size")}
                                        </Text>
                                        <NumberInput.Root
                                            size="xs"
                                            defaultValue={settings.textSize.toString()}
                                            min={5}
                                            max={50}
                                            onValueChange={(e) => {
                                                if (
                                                    !isNaN(e.valueAsNumber) &&
                                                    e.valueAsNumber >= 5 &&
                                                    e.valueAsNumber <= 50
                                                )
                                                    useMapSettingsStore
                                                        .getState()
                                                        .setTextSize(e.valueAsNumber);
                                            }}
                                        >
                                            <NumberInput.Control />
                                            <NumberInput.Input />
                                        </NumberInput.Root>
                                    </HStack>
                                    <HStack w="full" justify="space-between">
                                        <Text
                                            textStyle="sm"
                                            fontWeight={500}
                                            color="slate.text.muted"
                                        >
                                            {t("mapSettings.angleUnit")}
                                        </Text>
                                        <Menu.Root positioning={{ placement: "bottom-end" }}>
                                            <Menu.Trigger asChild>
                                                <Button
                                                    size="xs"
                                                    variant="outline"
                                                    minW="11rem"
                                                    justifyContent="space-between"
                                                >
                                                    {angleUnitLabels[settings.angleUnit]}
                                                </Button>
                                            </Menu.Trigger>
                                            <Portal>
                                                <Menu.Positioner>
                                                    <Menu.Content minW="11rem">
                                                        {ANGLE_UNITS.map((unit) => (
                                                            <Menu.Item
                                                                key={unit}
                                                                value={unit}
                                                                onClick={() =>
                                                                    useMapSettingsStore
                                                                        .getState()
                                                                        .setAngleUnit(unit)
                                                                }
                                                                fontWeight={
                                                                    unit === settings.angleUnit
                                                                        ? "semibold"
                                                                        : "normal"
                                                                }
                                                                color={
                                                                    unit === settings.angleUnit
                                                                        ? "active"
                                                                        : "fg"
                                                                }
                                                            >
                                                                {angleUnitLabels[unit]}
                                                            </Menu.Item>
                                                        ))}
                                                    </Menu.Content>
                                                </Menu.Positioner>
                                            </Portal>
                                        </Menu.Root>
                                    </HStack>
                                    <HStack w="full" justify="space-between">
                                        <Text
                                            textStyle="sm"
                                            fontWeight={500}
                                            color="slate.text.muted"
                                        >
                                            {t("mapSettings.angleOrientation")}
                                        </Text>
                                        <Menu.Root positioning={{ placement: "bottom-end" }}>
                                            <Menu.Trigger asChild>
                                                <Button
                                                    size="xs"
                                                    variant="outline"
                                                    minW="11rem"
                                                    justifyContent="space-between"
                                                >
                                                    {
                                                        angleOrientationLabels[
                                                            settings.angleOrientation
                                                        ]
                                                    }
                                                </Button>
                                            </Menu.Trigger>
                                            <Portal>
                                                <Menu.Positioner>
                                                    <Menu.Content minW="11rem">
                                                        {ANGLE_ORIENTATIONS.map((orientation) => (
                                                            <Menu.Item
                                                                key={orientation}
                                                                value={orientation}
                                                                onClick={() =>
                                                                    useMapSettingsStore
                                                                        .getState()
                                                                        .setAngleOrientation(
                                                                            orientation,
                                                                        )
                                                                }
                                                                fontWeight={
                                                                    orientation ===
                                                                    settings.angleOrientation
                                                                        ? "semibold"
                                                                        : "normal"
                                                                }
                                                                color={
                                                                    orientation ===
                                                                    settings.angleOrientation
                                                                        ? "active"
                                                                        : "fg"
                                                                }
                                                            >
                                                                {
                                                                    angleOrientationLabels[
                                                                        orientation
                                                                    ]
                                                                }
                                                            </Menu.Item>
                                                        ))}
                                                    </Menu.Content>
                                                </Menu.Positioner>
                                            </Portal>
                                        </Menu.Root>
                                    </HStack>
                                </VStack>

                                <VStack w="full" align="start">
                                    <TextZoomThresholdControl />
                                </VStack>

                                <VStack w="full" align="start">
                                    <HStack w="full" justify="space-between">
                                        <Heading size="md">{t("mapSettings.visibility")}</Heading>
                                        <HStack w="full" justify="end">
                                            <Button
                                                size="2xs"
                                                variant="outline"
                                                onClick={() =>
                                                    useMapSettingsStore
                                                        .getState()
                                                        .setAllTextEnabled(true, textTypes)
                                                }
                                            >
                                                {t("mapSettings.showAll")}
                                            </Button>
                                            <Button
                                                size="2xs"
                                                variant="outline"
                                                onClick={() =>
                                                    useMapSettingsStore
                                                        .getState()
                                                        .setAllTextEnabled(false, textTypes)
                                                }
                                            >
                                                {t("mapSettings.hideAll")}
                                            </Button>
                                        </HStack>
                                    </HStack>
                                    {textSettings.map(({ type, labelKey }) => (
                                        <OcSwitch
                                            key={type}
                                            label={t(labelKey as never)}
                                            checked={settings.textEnabled[type] ?? true}
                                            onCheckedChange={(v) =>
                                                useMapSettingsStore
                                                    .getState()
                                                    .setTextEnabled(v, type)
                                            }
                                        />
                                    ))}
                                </VStack>
                            </VStack>
                        </Accordion.ItemBody>
                    </Accordion.ItemContent>
                </Accordion.Item>
                <Accordion.Item value="metrics">
                    <Accordion.ItemTrigger>
                        <Text>{t("common.metrics")}</Text>
                        <Accordion.ItemIndicator />
                    </Accordion.ItemTrigger>
                    <Accordion.ItemContent>
                        <Accordion.ItemBody>
                            <MetricToggles />
                        </Accordion.ItemBody>
                    </Accordion.ItemContent>
                </Accordion.Item>
            </Accordion.Root>
        </ScrollArea>
    );
}
