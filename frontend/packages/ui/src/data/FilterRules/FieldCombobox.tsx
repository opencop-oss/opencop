// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Combobox,
    HStack,
    IconButton,
    Portal,
    Text,
    type ComboboxRootProviderProps,
} from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { LuX } from "react-icons/lu";
import { useCreatableCombobox } from "../../forms/use-creatable-combobox.hook.ts";
import type { PredicateDraft, PredicateError } from "./predicate.model";
import { fieldLabel, type FilterField } from "./filter-field.model";
import { useFilterFields } from "./FilterFieldsContext";
import { PredicateField } from "./PredicateField";

export type FieldComboboxProps = {
    predicate: PredicateDraft;
    setField: (field?: string) => void;
    /** The fields to offer, in order. */
    fields: readonly FilterField[];
    /** Offers creating a custom field from the typed text, when the provider supports it. */
    allowCreate: boolean;
    openOnClick?: boolean;
    error?: PredicateError["field"];
} & Omit<ComboboxRootProviderProps, "value">;

export function FieldCombobox({
    predicate,
    setField,
    fields,
    allowCreate,
    openOnClick,
    error,
    ...rest
}: FieldComboboxProps) {
    const { t } = useTranslation();
    const { createField, removeField, isValidFieldName } = useFilterFields();
    const canCreate = allowCreate && createField !== undefined;

    const { combobox, isTentative } = useCreatableCombobox({
        inputBehavior: "autohighlight",
        items: [...fields],
        value: predicate.field ? [predicate.field] : [],
        validate: (input) => canCreate && (isValidFieldName?.(input) ?? true),
        onValueChange: (value) => setField(value[0]),
        onCreate: (name) => createField?.(name),
        itemToString: (field) => fieldLabel(field, t),
        itemToValue: (field) => field.id,
        openOnClick,
    });

    return (
        <PredicateField slot="field" error={error}>
            <Combobox.RootProvider {...rest} value={combobox}>
                <Combobox.Control>
                    <Combobox.Input placeholder={t("filter.fieldPlaceholder")} boxShadow="none" />
                    <Combobox.IndicatorGroup>
                        <Combobox.ClearTrigger />
                        <Combobox.Trigger />
                    </Combobox.IndicatorGroup>
                </Combobox.Control>
                <Portal>
                    <Combobox.Positioner data-oc-combobox-portal>
                        <Combobox.Content>
                            <Combobox.Empty>{t("filter.noFieldsFound")}</Combobox.Empty>
                            {combobox.collection.items.map((field) => (
                                <Combobox.Item
                                    item={field}
                                    key={isTentative(field) ? `create:${field.label}` : field.id}
                                >
                                    {isTentative(field) ? (
                                        <Text>
                                            {t("filter.createAttribute", { name: field.label })}
                                        </Text>
                                    ) : (
                                        fieldLabel(field, t)
                                    )}

                                    <HStack>
                                        <Combobox.ItemIndicator />
                                        {!isTentative(field) && field.removable && removeField && (
                                            <IconButton
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    removeField(field.id);
                                                    setField(undefined);
                                                }}
                                                aria-label={t("filter.removeAttribute", {
                                                    name: fieldLabel(field, t),
                                                })}
                                                size="2xs"
                                            >
                                                <LuX />
                                            </IconButton>
                                        )}
                                    </HStack>
                                </Combobox.Item>
                            ))}
                        </Combobox.Content>
                    </Combobox.Positioner>
                </Portal>
            </Combobox.RootProvider>
        </PredicateField>
    );
}
