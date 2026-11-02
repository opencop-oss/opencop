// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useState, type ReactElement } from "react";
import type { NumberStyleSpec } from "../specs";
import { capitalize } from "lodash";
import { HStack, NumberInput, Text } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { useStyleValue, createStyleChangeHandler } from "../tool.hooks";

export function ToolNumber({
    spec,
    toolKey,
}: {
    spec: NumberStyleSpec;
    toolKey: string;
}): ReactElement {
    const { t } = useTranslation();
    const value = useStyleValue(toolKey, spec.label, spec.input.defaultValue);
    const onChange = createStyleChangeHandler(spec.input, toolKey, spec.label);
    const displayLabel = spec.labelKey
        ? t(spec.labelKey as never, { defaultValue: spec.label })
        : spec.label;
    const resolvedValue = Number.isFinite(value) ? value : spec.input.defaultValue;
    const [draftValue, setDraftValue] = useState(String(resolvedValue));

    useEffect(() => {
        // eslint-disable-next-line @eslint-react/hooks-extra/no-direct-set-state-in-use-effect
        setDraftValue(String(resolvedValue));
    }, [resolvedValue]);

    return (
        <HStack px="2" gap="1.5" h="full" alignItems="center">
            <Text fontSize="xs" color="fg.muted" whiteSpace="nowrap">
                {capitalize(displayLabel)}
            </Text>
            <NumberInput.Root
                size="xs"
                value={draftValue}
                min={spec.input.min}
                max={spec.input.max}
                step={spec.input.step}
                onValueChange={(e) => {
                    setDraftValue(e.value);
                    if (Number.isFinite(e.valueAsNumber)) {
                        onChange(e.valueAsNumber);
                    }
                }}
                onBlur={() => setDraftValue(String(resolvedValue))}
                w="14"
            >
                <NumberInput.Control border="none">
                    <NumberInput.IncrementTrigger border="none" />
                    <NumberInput.DecrementTrigger border="none" />
                </NumberInput.Control>
                <NumberInput.Input h="7" textAlign="center" fontSize="xs" />
            </NumberInput.Root>
            {spec.input.suffix && (
                <Text fontSize="xs" color="fg.muted">
                    {spec.input.suffix}
                </Text>
            )}
        </HStack>
    );
}
