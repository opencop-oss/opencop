// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field } from "@chakra-ui/react";
import type { ReactNode } from "react";

interface LabeledInputProps {
    children?: ReactNode;
    label?: string;
    invalid?: boolean;
    errorText?: ReactNode;
}

export function LabeledInput({ children, label, invalid, errorText }: LabeledInputProps) {
    return (
        <Field.Root w="full" minW="0" invalid={invalid}>
            <Field.Label color="fg.muted" textStyle="xs" lineHeight="short" whiteSpace="normal">
                {label}
            </Field.Label>
            {children}
            {errorText && <Field.ErrorText textStyle="xs">{errorText}</Field.ErrorText>}
        </Field.Root>
    );
}
