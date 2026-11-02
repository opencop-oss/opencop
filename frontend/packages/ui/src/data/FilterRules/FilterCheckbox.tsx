// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { CheckboxRootProps } from "@ark-ui/react";
import { Checkbox } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";

export type FilterCheckboxProps = {
    value: boolean;
    onChange?: (value: boolean) => void;
    labelKey: string;
} & Omit<CheckboxRootProps, "value" | "onChange">;

export function FilterCheckbox({ value, onChange, labelKey, ...rest }: FilterCheckboxProps) {
    const { t } = useTranslation();
    return (
        <Checkbox.Root
            {...rest}
            size="xs"
            checked={value}
            onCheckedChange={onChange ? (d) => onChange(!!d.checked) : undefined}
        >
            <Checkbox.HiddenInput />
            <Checkbox.Control />
            <Checkbox.Label color="slate.text.muted">{t(labelKey as never)}</Checkbox.Label>
        </Checkbox.Root>
    );
}
