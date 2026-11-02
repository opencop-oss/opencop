// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field, Input, type InputProps } from "@chakra-ui/react";

interface PreferenceFieldProps extends Omit<InputProps, "value"> {
    label: string;
    value: string;
    disabled?: boolean;
}

export function PreferenceField({ label, value, disabled, ...inputProps }: PreferenceFieldProps) {
    return (
        <Field.Root>
            <Field.Label>{label}</Field.Label>
            <Input
                value={value}
                disabled={disabled}
                bg={disabled ? "bg.muted" : undefined}
                cursor={disabled ? "not-allowed" : undefined}
                {...inputProps}
            />
        </Field.Root>
    );
}
