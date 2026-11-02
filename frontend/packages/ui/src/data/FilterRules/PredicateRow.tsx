// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { HStack, IconButton, Text, VStack } from "@chakra-ui/react";
import { FieldCombobox } from "./FieldCombobox";
import { OperatorCombobox } from "./OperatorCombobox";
import { ValueEditor } from "./ValueEditor";
import {
    defaultPredicateDraft,
    isRawPredicate,
    type PredicateDraft,
    type PredicateError,
} from "./predicate.model";
import { findField, retargetDraftToField, type FilterField } from "./filter-field.model";
import { useTranslation } from "react-i18next";
import { FaTrash } from "react-icons/fa";
import { FilterCheckbox } from "./FilterCheckbox";
import { summarizePredicate, summarizeUnavailable } from "./predicate-summary.util";
import { PredicateSummary } from "./PredicateSummary";

export type PredicateRowProps = {
    predicate: PredicateDraft;
    setPredicate: (predicate: PredicateDraft) => void;
    remove: () => void;
    errors?: PredicateError;
    expanded: boolean;
    onExpand: () => void;
    /** The fields the row may pick from, in order. */
    fields: readonly FilterField[];
    /** Offers creating a custom field from the field picker. */
    allowCreate: boolean;
};

export function PredicateRow({
    predicate,
    setPredicate,
    remove,
    errors,
    expanded,
    onExpand,
    fields,
    allowCreate,
}: PredicateRowProps) {
    const { t } = useTranslation();
    const field = findField(fields, predicate.field);
    const summary = summarizePredicate(predicate, field, t);
    const setField = (id?: string) => setPredicate(retargetDraftToField(predicate, id, fields));
    const summaryId = `filter-predicate-summary-${predicate.id}`;
    // A field that pins its operator has nothing to choose; one that withholds a
    // modifier does so because it has no meaning for its values.
    const showOperator = field?.operators?.length !== 1;
    const showNegate = field?.toggles?.negate !== false;
    const showCaseInsensitive = field?.toggles?.caseInsensitive !== false;
    // A fallback row stays collapsed: it can be deleted but not edited. Any other row
    // stays open while it can't yet be summarized (still being filled in) or has errors
    // to fix — regardless of expanded/collapsed state.
    if (isRawPredicate(predicate))
        return (
            <HStack
                bg="slate.elevated"
                p="3"
                borderWidth="1px"
                w="full"
                justify="space-between"
                gap="2"
            >
                <Text fontSize="sm" truncate minW={0}>
                    <PredicateSummary summary={summarizeUnavailable(predicate.raw)} />
                </Text>
                <IconButton
                    size="2xs"
                    colorPalette="red"
                    variant="ghost"
                    aria-label={t("filter.removeCondition")}
                    onClick={remove}
                >
                    <FaTrash />
                </IconButton>
            </HStack>
        );
    const isOpen = expanded || !!errors || summary === undefined;

    if (!isOpen) {
        return (
            <HStack
                className="group"
                bg="slate.elevated"
                p="3"
                borderWidth="1px"
                w="full"
                justify="space-between"
                align="center"
                gap="2"
                cursor="pointer"
                transition="box-shadow 0.15s ease"
                _hover={{
                    boxShadow: "0 0 6px 1px var(--shadow-color)",
                    shadowColor: "brand.focusRing",
                }}
            >
                {/* A sibling of the trash IconButton, not its ancestor — a
                    focusable custom-role control can't nest another
                    interactive element without producing unpredictable
                    screen-reader/tab-order behavior (role="button" must be a leaf). */}
                <HStack
                    role="button"
                    aria-label={t("filter.expandCondition")}
                    aria-describedby={summaryId}
                    tabIndex={0}
                    flex="1"
                    minW={0}
                    justify="space-between"
                    align="center"
                    gap="2"
                    _focusVisible={{
                        outline: "2px solid",
                        outlineColor: "brand.focusRing",
                        outlineOffset: "2px",
                    }}
                    // pointerdown, not click: switching from another open row
                    // relies on the outside-pointerdown listener in
                    // FilterRulesBuilder collapsing it in the same event pass
                    // as this row expanding — a later click could be invalidated
                    // by the layout shift the other row's collapse causes.
                    onPointerDown={onExpand}
                    onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                            e.preventDefault();
                            onExpand();
                        }
                    }}
                >
                    <Text id={summaryId} fontSize="sm" truncate flexShrink={1} minW={0}>
                        <PredicateSummary summary={summary} />
                    </Text>
                    <Text
                        fontSize="xs"
                        color="fg.muted"
                        opacity="0"
                        _groupHover={{ opacity: 1 }}
                        transition="opacity 0.15s ease"
                        pointerEvents="none"
                        flex="1"
                        textAlign="center"
                        truncate
                    >
                        {t("filter.clickToEdit")}
                    </Text>
                </HStack>
                <IconButton
                    size="2xs"
                    colorPalette="red"
                    variant="ghost"
                    aria-label={t("filter.removeCondition")}
                    onClick={remove}
                >
                    <FaTrash />
                </IconButton>
            </HStack>
        );
    }

    return (
        <VStack bg="slate.elevated" p="3" gap="2" borderWidth="1px" w="full" align="start">
            <HStack w="full" align="start">
                <FieldCombobox
                    size="xs"
                    variant="flushed"
                    openOnClick
                    predicate={predicate}
                    fields={fields}
                    allowCreate={allowCreate}
                    error={errors?.field}
                    setField={setField}
                />
                {showOperator && (
                    <OperatorCombobox
                        // The offered operators seed on mount; a new field gets its own list.
                        key={field?.id}
                        size="xs"
                        variant="flushed"
                        predicate={predicate}
                        operators={field?.operators}
                        openOnClick
                        inputBehavior="autohighlight"
                        error={errors?.operator}
                        setOperator={(operator) => {
                            if (!operator)
                                return setPredicate({
                                    ...predicate,
                                    operator: undefined,
                                    value: undefined,
                                });
                            setPredicate(
                                defaultPredicateDraft({
                                    id: predicate.id,
                                    field: predicate.field,
                                    operator,
                                    // Only the value shape is operator-specific; the
                                    // modifiers survive an operator change.
                                    negate: predicate.negate,
                                    caseInsensitive: predicate.caseInsensitive,
                                }),
                            );
                        }}
                    />
                )}
            </HStack>

            <ValueEditor
                predicate={predicate}
                setPredicate={setPredicate}
                error={errors?.value}
                field={field}
            />

            <HStack w="full" justify="space-between">
                <HStack gap="3">
                    {showNegate && (
                        <FilterCheckbox
                            value={!!predicate.negate}
                            onChange={(negate) => setPredicate({ ...predicate, negate })}
                            labelKey="filter.negate"
                        />
                    )}
                    {/* Nothing to compare case on: EXISTS has no value. */}
                    {predicate.operator !== undefined &&
                        predicate.operator !== "EXISTS" &&
                        showCaseInsensitive && (
                            <FilterCheckbox
                                value={!!predicate.caseInsensitive}
                                onChange={(caseInsensitive) =>
                                    setPredicate({ ...predicate, caseInsensitive })
                                }
                                labelKey="filter.caseInsensitive"
                            />
                        )}
                </HStack>
                <IconButton
                    size="2xs"
                    colorPalette="red"
                    variant="ghost"
                    aria-label={t("filter.removeCondition")}
                    onClick={remove}
                >
                    <FaTrash />
                </IconButton>
            </HStack>
        </VStack>
    );
}
