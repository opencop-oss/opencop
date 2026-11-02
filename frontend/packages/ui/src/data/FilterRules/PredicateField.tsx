// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field } from "@chakra-ui/react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type {
    EqualsValueError,
    InValueError,
    SubstringInValueError,
    PredicateError,
} from "./predicate.model";

type FieldSlotError = NonNullable<PredicateError["field"]>;
type OperatorSlotError = NonNullable<PredicateError["operator"]>;
type ValueSlotError = EqualsValueError | InValueError | NonNullable<SubstringInValueError["value"]>;
type StartSlotError = NonNullable<SubstringInValueError["start"]>;
type EndSlotError = NonNullable<SubstringInValueError["end"]>;

const messageKey = {
    field: {
        required: "filter.errors.field-required",
        unavailable: "filter.errors.field-unavailable",
    },
    operator: { required: "filter.errors.operator-required" },
    value: { required: "filter.errors.value-required", empty: "filter.errors.value-required" },
    start: {
        required: "filter.errors.start-invalid",
        "invalid-range": "filter.errors.range-invalid",
    },
    end: {
        required: "filter.errors.end-invalid",
        "invalid-range": "filter.errors.range-invalid",
    },
} as const satisfies {
    field: Record<FieldSlotError, string>;
    operator: Record<OperatorSlotError, string>;
    value: Record<ValueSlotError, string>;
    start: Record<StartSlotError, string>;
    end: Record<EndSlotError, string>;
};

export type PredicateFieldProps = { children: ReactNode } & (
    | { slot: "field"; error?: FieldSlotError }
    | { slot: "operator"; error?: OperatorSlotError }
    | { slot: "value"; error?: ValueSlotError }
    | { slot: "start"; error?: StartSlotError }
    | { slot: "end"; error?: EndSlotError }
);

function errorKey(props: PredicateFieldProps) {
    if (props.error === undefined) return undefined;
    switch (props.slot) {
        case "field":
            return messageKey.field[props.error];
        case "operator":
            return messageKey.operator[props.error];
        case "value":
            return messageKey.value[props.error];
        case "start":
            return messageKey.start[props.error];
        case "end":
            return messageKey.end[props.error];
    }
}

export function PredicateField(props: PredicateFieldProps) {
    const { t } = useTranslation();
    const key = errorKey(props);
    return (
        <Field.Root invalid={key !== undefined}>
            {props.children}
            {key !== undefined && <Field.ErrorText>{t(key)}</Field.ErrorText>}
        </Field.Root>
    );
}
