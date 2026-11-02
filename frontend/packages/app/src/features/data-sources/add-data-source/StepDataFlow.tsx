// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Badge, Field, Flex, RadioGroup, VStack } from "@chakra-ui/react";
import type { TransportType } from "@opencop/api";
import { useTranslation } from "react-i18next";
import { TRANSPORT_LABEL_KEYS } from "../data-sources.util";

const TRANSPORT_TYPES = ["TCP", "UDP", "KAFKA", "RTSP"] as const satisfies readonly TransportType[];

interface StepDataFlowProps {
    transportType: TransportType;
    onChange: (transportType: TransportType) => void;
    rtspEnabled: boolean;
}

interface RadioOption {
    label: string;
    value: TransportType;
    disabled?: boolean;
}

function isTransportType(value: string): value is TransportType {
    return (TRANSPORT_TYPES as readonly string[]).includes(value);
}

/** Picks the transport; access is asked later, from what the chosen integration supports. */
export function StepDataFlow({ transportType, onChange, rtspEnabled }: StepDataFlowProps) {
    const { t } = useTranslation();

    const radioOptions: RadioOption[] = [
        { label: t(TRANSPORT_LABEL_KEYS.TCP), value: "TCP" },
        { label: t(TRANSPORT_LABEL_KEYS.UDP), value: "UDP" },
        { label: t(TRANSPORT_LABEL_KEYS.KAFKA), value: "KAFKA" },
        { label: t(TRANSPORT_LABEL_KEYS.RTSP), value: "RTSP", disabled: !rtspEnabled },
    ];

    return (
        <Field.Root p={2}>
            <Field.Label>{t("admin.transportType")}</Field.Label>
            <RadioGroup.Root
                aria-label={t("admin.transportType")}
                colorPalette="brand"
                variant="outline"
                value={transportType}
                onValueChange={(details) => {
                    const val = typeof details === "string" ? details : details.value;
                    if (!val || !isTransportType(val)) return;
                    const option = radioOptions.find((item) => item.value === val);
                    if (option && !option.disabled) onChange(val);
                }}
            >
                <VStack align="start" gap={4} w="full">
                    {radioOptions.map((item) => (
                        <RadioGroup.Item
                            key={item.value}
                            value={item.value}
                            disabled={item.disabled}
                            opacity={item.disabled ? 0.5 : 1}
                            cursor={item.disabled ? "not-allowed" : "pointer"}
                        >
                            <RadioGroup.ItemHiddenInput />
                            <RadioGroup.ItemIndicator />
                            <Flex align="center" gap={2}>
                                <RadioGroup.ItemText>{item.label}</RadioGroup.ItemText>
                                {item.disabled && (
                                    <Badge size="sm" colorPalette="gray" variant="subtle">
                                        {t("admin.unavailable")}
                                    </Badge>
                                )}
                            </Flex>
                        </RadioGroup.Item>
                    ))}
                </VStack>
            </RadioGroup.Root>
        </Field.Root>
    );
}
