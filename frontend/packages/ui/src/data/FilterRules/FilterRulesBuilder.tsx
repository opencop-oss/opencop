// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Center, HStack, Text, VStack } from "@chakra-ui/react";
import {
    useCallback,
    useEffect,
    useId,
    useImperativeHandle,
    useMemo,
    useRef,
    useState,
    type Ref,
} from "react";
import {
    Controller,
    useController,
    useFieldArray,
    useForm,
    useFormState,
    useWatch,
    type Control,
} from "react-hook-form";
import { FaPlus } from "react-icons/fa";
import { FaTriangleExclamation } from "react-icons/fa6";
import { useTranslation } from "react-i18next";
import {
    defaultPredicateDraft,
    toPredicateError,
    type FilterDraft,
    type FilterError,
} from "./predicate.model";
import {
    activeNotices,
    findField,
    hasFallbackRow,
    isInertUnderAll,
    parseFilter,
    type FilterField,
    type ParseFilterResult,
} from "./filter-field.model";
import { useFilterFields } from "./FilterFieldsContext";
import { summarizePredicate } from "./predicate-summary.util";
import { PredicateRow } from "./PredicateRow";
import { CombinatorSelect } from "./CombinatorSelect";

/** The part of a form the builder reads and writes: its draft lives under `filter`. */
export type FilterRulesFormValues = { filter: FilterDraft };

const EMPTY_FILTER: FilterDraft = { combinator: "ALL", predicates: [] };

/**
 * True only on the edge where a row is retargeted *onto* a field that matches only under
 * ANY while the filter is still ALL. Deliberately not true on later value edits, so a
 * user who switches back to ALL (accepting the warning) is not overridden.
 */
function retargetedOntoAnyOnlyField(
    prev: string | undefined,
    next: string | undefined,
    combinator: FilterDraft["combinator"],
    fields: readonly FilterField[],
): boolean {
    const wasAnyOnly = findField(fields, prev)?.companions !== undefined;
    const isAnyOnly = findField(fields, next)?.companions !== undefined;
    return !wasAnyOnly && isAnyOnly && combinator === "ALL";
}

export type FilterRulesBuilderHandle = {
    /** Validate the draft, show row errors, and return the parsed filter. */
    attempt: () => ParseFilterResult;
};

export type FilterRulesBuilderProps<T extends FilterRulesFormValues> = {
    /**
     * The caller's form, when the filter is one field of a larger form; its draft lives
     * under `filter`. Without it the builder owns a form of its own.
     */
    control?: Control<T>;
    defaultValue?: FilterDraft;
    ref?: Ref<FilterRulesBuilderHandle>;
    /** Which of the provided fields to offer; all when absent. */
    offerField?: (field: FilterField) => boolean;
    /** Offers creating a custom field from the field picker (default true). */
    allowCustomFields?: boolean;
    /** Fires whenever the draft becomes/stops being unsavable (a condition that needs Any
     *  sitting under All), so a caller with its own Save button can disable it. */
    onValidityChange?: (isInvalid: boolean) => void;
};

export function FilterRulesBuilder<T extends FilterRulesFormValues>({
    control,
    defaultValue,
    ref,
    offerField,
    allowCustomFields = true,
    onValidityChange,
}: FilterRulesBuilderProps<T>) {
    const { t, i18n } = useTranslation();
    const { fields: provided, defaultFieldId } = useFilterFields();
    const fieldDefinitions = useMemo(
        () => (offerField ? provided.filter(offerField) : provided),
        [provided, offerField],
    );
    const standaloneForm = useForm<FilterRulesFormValues>({
        defaultValues: { filter: defaultValue ?? EMPTY_FILTER },
    });
    // RHF's `Control` is invariant in its form values, so a caller's wider form is not
    // assignable to `Control<FilterRulesFormValues>`; `T extends FilterRulesFormValues` guarantees the
    // `filter` paths below exist on it.
    const activeControl =
        (control as Control<FilterRulesFormValues> | undefined) ?? standaloneForm.control;
    const draft = useWatch({ control: activeControl, name: "filter" });
    const [attemptErrors, setAttemptErrors] = useState<FilterError>({});
    const { fields, append, remove } = useFieldArray({
        control: activeControl,
        name: "filter.predicates",
        // Predicate drafts already carry their own meaningful `id` (used below
        // for expandedId/row refs/summarization) — a custom keyName keeps RHF's
        // own generated key from shadowing it in the `fields` projection.
        keyName: "_rhfId",
    });
    const { errors } = useFormState({ control: activeControl, name: "filter.predicates" });

    // A field with companions saves an OR, which only ANY can satisfy — under All it
    // matches nothing, so this blocks saving rather than just warning.
    const isUnsavable = isInertUnderAll(draft, fieldDefinitions);

    // Independent of the combinator — standing caveats of the fields in use, not errors.
    const notices = activeNotices(draft, fieldDefinitions);

    // A row whose field is unavailable may need the combinator it was saved with.
    const combinatorLocked = hasFallbackRow(draft);
    const combinatorLockedId = useId();

    // Pushed from an effect rather than read by the parent — normally the "Bad" pattern
    // in docs/best-practices/react/avoiding-unnecessary-effects.md §6, but safe here: a
    // caller that wants it has its own Save button, shares no control with this component
    // and mounts it from a fresh draft, so there's no stale-parent-state render to land on.
    useEffect(() => {
        onValidityChange?.(isUnsavable);
    }, [isUnsavable, onValidityChange]);

    // Owns the combinator field directly (not a second <Controller>), so a
    // predicate retarget elsewhere in this component can flip it too.
    const combinator = useController({ control: activeControl, name: "filter.combinator" });

    const attempt = useCallback(() => {
        const result = parseFilter(draft, fieldDefinitions);
        setAttemptErrors(result.ok ? {} : result.errors);
        return result;
    }, [draft, fieldDefinitions]);

    useImperativeHandle(ref, () => ({ attempt }), [attempt]);

    // The one row the user has expanded (Google-Forms-style: a complete row
    // shows as a single summary line until reopened, and only one can be open
    // at a time). A brand-new, still-incomplete first row starts expanded here
    // — if it instead relied only on PredicateRow's "incomplete rows force
    // open" fallback, typing its first non-empty character would make it
    // summarizable while `expanded` was still false, collapsing the row out
    // from under the input mid-keystroke. A first row that's already complete
    // (e.g. editing a saved filter) starts collapsed like every other row.
    const [expandedId, setExpandedId] = useState<string | null>(() => {
        const first = fields[0];
        if (!first) return null;
        return summarizePredicate(first, findField(fieldDefinitions, first.field), t) === undefined
            ? first.id
            : null;
    });
    const expand = (id: string) => setExpandedId(id);
    const collapse = (id: string) => setExpandedId((prev) => (prev === id ? null : prev));

    // Scrolls a freshly added row into view — it may render below the fold of
    // the scrollable rows list once there are enough conditions to fill it.
    // Handled from the row's ref callback (fired during commit, before paint)
    // rather than an effect, so adding a row doesn't need a second render.
    const rowElementsRef = useRef(new Map<string, HTMLElement>());
    const pendingScrollIdRef = useRef<string | null>(null);

    // Collapse the open row on a pointerdown anywhere outside it. Combobox
    // dropdowns are portaled outside the row and carry a marker so choosing an
    // option does not collapse and unmount its editor during the same event.
    useEffect(() => {
        if (!expandedId) return;
        const handlePointerDown = (e: PointerEvent) => {
            const target = e.target as Node;
            const rowEl = rowElementsRef.current.get(expandedId);
            if (!rowEl || rowEl.contains(target)) return;
            if (target instanceof Element && target.closest("[data-oc-combobox-portal]")) return;
            // The add-condition button re-points expandedId itself; collapsing here
            // reflows the scroller mid-click and can drop the click entirely.
            if (target instanceof Element && target.closest("[data-filter-add-condition]")) return;
            collapse(expandedId);
        };
        document.addEventListener("pointerdown", handlePointerDown, true);
        return () => document.removeEventListener("pointerdown", handlePointerDown, true);
    }, [expandedId]);

    return (
        <VStack align="left" pointerEvents="auto" w="full" h="full" margin="0">
            <HStack w="full" justify="space-between" pt="2" pb="2" px="2">
                <CombinatorSelect
                    value={combinator.field.value}
                    onChange={combinator.field.onChange}
                    disabled={combinatorLocked}
                    describedBy={combinatorLocked ? combinatorLockedId : undefined}
                />
                <Button
                    size="xs"
                    variant="solid"
                    colorPalette="brand"
                    data-filter-add-condition
                    disabled={fields.length === 20}
                    onClick={() => {
                        const predicate = defaultPredicateDraft({
                            field: defaultFieldId,
                            operator: "EQUALS",
                        });
                        append(predicate);
                        expand(predicate.id);
                        pendingScrollIdRef.current = predicate.id;
                    }}
                >
                    <FaPlus />
                    {t("filter.addCondition")}
                </Button>
            </HStack>
            {/* Locked and needing Any at once gets one message: "switch to Any" alone would
                ask for what the locked control refuses. */}
            {isUnsavable && (
                <HStack px="2" pb="1" gap="2" align="start">
                    <Box color="fg.error" flexShrink={0} mt="0.5">
                        <FaTriangleExclamation />
                    </Box>
                    <Text
                        id={combinatorLocked ? combinatorLockedId : undefined}
                        role="alert"
                        fontSize="xs"
                        color="fg.error"
                    >
                        {combinatorLocked
                            ? t("filter.conditionNeedsAnyLocked")
                            : t("filter.conditionNeedsAny")}
                    </Text>
                </HStack>
            )}
            {combinatorLocked && !isUnsavable && (
                <HStack px="2" pb="1" gap="2" align="start">
                    <Text id={combinatorLockedId} fontSize="xs" color="fg.muted">
                        {t("filter.combinatorLocked")}
                    </Text>
                </HStack>
            )}
            {notices.map((key) => (
                <HStack key={key} px="2" pb="1" gap="2" align="start">
                    <Text fontSize="xs" color="fg.muted">
                        {t(key as never)}
                    </Text>
                </HStack>
            ))}
            {/* p (not just gap) so a row's hover glow has room to render before
                this scroll container's own edge clips it — otherwise only the
                gap between rows shows the glow; the top/bottom/side edges of
                the list don't. */}
            <VStack overflowY="auto" w="full" h="full" gap="2" p="2">
                {fields.map((field, index) => (
                    <Box
                        // Combobox's fail to update value text on localization change.
                        key={field.id + ":" + i18n.language}
                        w="full"
                        ref={(el: HTMLDivElement | null) => {
                            if (el) {
                                rowElementsRef.current.set(field.id, el);
                                if (pendingScrollIdRef.current === field.id) {
                                    el.scrollIntoView({ block: "nearest" });
                                    pendingScrollIdRef.current = null;
                                }
                            } else {
                                rowElementsRef.current.delete(field.id);
                            }
                        }}
                    >
                        <Controller
                            control={activeControl}
                            name={`filter.predicates.${index}`}
                            render={({ field: predicateField }) => (
                                <PredicateRow
                                    predicate={predicateField.value}
                                    setPredicate={(next) => {
                                        // A forced-open incomplete row must reclaim expandedId
                                        // before the edit makes it summarizable.
                                        expand(field.id);
                                        if (
                                            !combinatorLocked &&
                                            retargetedOntoAnyOnlyField(
                                                predicateField.value.field,
                                                next.field,
                                                draft.combinator,
                                                fieldDefinitions,
                                            )
                                        ) {
                                            combinator.field.onChange("ANY");
                                        }
                                        predicateField.onChange(next);
                                    }}
                                    remove={() => {
                                        remove(index);
                                        collapse(field.id);
                                    }}
                                    errors={
                                        toPredicateError(errors.filter?.predicates?.[index]) ??
                                        attemptErrors[field.id]
                                    }
                                    expanded={expandedId === field.id}
                                    onExpand={() => expand(field.id)}
                                    fields={fieldDefinitions}
                                    allowCreate={allowCustomFields}
                                />
                            )}
                        />
                    </Box>
                ))}
                {fields.length === 0 && (
                    <Center w="full" h="full">
                        <Text fontSize="xs" color="fg.muted">
                            {t("filter.noConditions")}
                        </Text>
                    </Center>
                )}
            </VStack>
        </VStack>
    );
}
