// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    HStack,
    TagsInput as ChakraTagsInput,
    NumberInput,
    Input,
    Text,
    type TagsInputRootProps,
    Box,
} from "@chakra-ui/react";
import assertNever from "assert-never";
import { useTranslation } from "react-i18next";
import {
    isEmptyPredicate,
    isRawPredicate,
    type InPredicateDraft,
    type InValueError,
    type SubstringInPredicateDraft,
    type SubstringInValueError,
    type PredicateDraft,
    type PredicateValueError,
} from "./predicate.model";
import type { FilterField } from "./filter-field.model";
import { PredicateField } from "./PredicateField";

export type ValueEditorProps = {
    predicate: PredicateDraft;
    setPredicate: (predicate: PredicateDraft) => void;
    error?: PredicateValueError;
    /** The row's field; its own `ValueEditor`, when it has one, replaces the generic one. */
    field?: FilterField;
};

export function ValueEditor({ predicate, setPredicate, error, field }: ValueEditorProps) {
    const { t } = useTranslation();
    if (isRawPredicate(predicate) || isEmptyPredicate(predicate)) return <Box />;
    if (field?.ValueEditor) {
        const FieldValueEditor = field.ValueEditor;
        return (
            <FieldValueEditor
                // A field's editor may seed its state on mount; a per-field key keeps
                // one field's state from carrying over to the next.
                key={field.id}
                predicate={predicate}
                setPredicate={setPredicate}
                error={error}
            />
        );
    }
    switch (predicate.operator) {
        case "SUBSTRING_IN":
            return (
                <SubstringInValueEditor
                    predicate={predicate}
                    setPredicate={setPredicate}
                    error={typeof error === "object" ? error : undefined}
                />
            );
        case "IN":
            return (
                <InValueEditor
                    predicate={predicate}
                    setPredicate={setPredicate}
                    error={error === "empty" ? error : undefined}
                />
            );
        case "EXISTS":
            return <Box />;
        case "EQUALS":
            return (
                <PredicateField slot="value" error={error === "required" ? error : undefined}>
                    <Input
                        size="xs"
                        variant="flushed"
                        placeholder={t("filter.valuePlaceholder")}
                        value={predicate.value}
                        boxShadow="none"
                        onChange={(e) => setPredicate({ ...predicate, value: e.target.value })}
                    />
                </PredicateField>
            );
        default:
            return assertNever(predicate);
    }
}

type TagsInputsProps = {
    value: string[];
    onChange: (value: string[]) => void;
} & Omit<Partial<TagsInputRootProps>, "onChange">;

function TagsInput({ value, onChange, ...rest }: TagsInputsProps) {
    const { t } = useTranslation();
    return (
        <ChakraTagsInput.Root
            {...rest}
            size="xs"
            max={50}
            variant="flushed"
            // Text typed but not committed lives in the input, not in form
            // state, so submitting reported "a value is required" over a field
            // that looked filled. Blur commits it.
            blurBehavior="add"
            value={value}
            onValueChange={(details) => onChange(details.value)}
        >
            <ChakraTagsInput.Control>
                <ChakraTagsInput.Items />
                <ChakraTagsInput.Input placeholder={t("filter.valuesPlaceholder")} />
                <ChakraTagsInput.ClearTrigger />
            </ChakraTagsInput.Control>
        </ChakraTagsInput.Root>
    );
}

type SubstringInValueEditorProps = {
    predicate: SubstringInPredicateDraft;
    setPredicate: (predicate: PredicateDraft) => void;
    error?: SubstringInValueError;
};

function SubstringInValueEditor({ predicate, setPredicate, error }: SubstringInValueEditorProps) {
    const { t } = useTranslation();
    const { value } = predicate;

    return (
        <HStack w="full" align="start">
            <PredicateField slot="value" error={error?.value}>
                <TagsInput
                    w="full"
                    size="xs"
                    value={value.value}
                    onChange={(v) => setPredicate({ ...predicate, value: { ...value, value: v } })}
                />
            </PredicateField>
            <Text>{t("filter.from")}</Text>
            <HStack w="full" align="start">
                <PredicateField slot="start" error={error?.start}>
                    <NumberInput.Root
                        value={value.start}
                        onValueChange={(details) =>
                            setPredicate({
                                ...predicate,
                                value: { ...value, start: details.value },
                            })
                        }
                        w="full"
                        variant="flushed"
                        size="xs"
                        formatOptions={{ maximumFractionDigits: 0 }}
                    >
                        <NumberInput.Input focusRing="none" boxShadow="none" placeholder="--" />
                    </NumberInput.Root>
                </PredicateField>
                <Text>{t("filter.to")}</Text>
                <PredicateField slot="end" error={error?.end}>
                    <NumberInput.Root
                        value={value.end}
                        onValueChange={(details) =>
                            setPredicate({ ...predicate, value: { ...value, end: details.value } })
                        }
                        w="full"
                        variant="flushed"
                        size="xs"
                        formatOptions={{ maximumFractionDigits: 0 }}
                    >
                        <NumberInput.Input focusRing="none" boxShadow="none" placeholder="--" />
                    </NumberInput.Root>
                </PredicateField>
            </HStack>
        </HStack>
    );
}

type InValueEditorProps = {
    predicate: InPredicateDraft;
    setPredicate: (predicate: PredicateDraft) => void;
    error?: InValueError;
};

function InValueEditor({ predicate, setPredicate, error }: InValueEditorProps) {
    const { value } = predicate;
    return (
        <PredicateField slot="value" error={error}>
            <TagsInput value={value} onChange={(value) => setPredicate({ ...predicate, value })} />
        </PredicateField>
    );
}
