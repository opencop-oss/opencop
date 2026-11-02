// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Combobox, HStack, Portal, Text, useFilter, useListCollection } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { PredicateField, type FilterValueEditorProps } from "@opencop/ui";
import type { SymbolOption, SymbolSlice } from "./symbol-fields.util";
import { IdentityFrameIcon } from "./IdentityFrameIcon";

/**
 * The value editor for one SIDC slice: picks one value from its closed list. Writes only
 * `value.value` — the slice bounds belong to the field, unlike the generic substring editor.
 */
export function SymbolFieldValueEditor({
    predicate,
    setPredicate,
    error,
    slice,
}: FilterValueEditorProps & { slice: SymbolSlice }) {
    const { t } = useTranslation(["translation", "symbology"]);
    const { contains } = useFilter({ sensitivity: "base" });
    const label = (option: SymbolOption) =>
        t(option.localeKey as never, { defaultValue: option.label });

    const { collection, filter } = useListCollection({
        initialItems: [...slice.options],
        itemToValue: (option) => option.code,
        itemToString: label,
        filter: contains,
    });

    if (predicate.operator !== "SUBSTRING_IN") return null;
    const selected = predicate.value.value;
    const isEmpty = typeof error === "object" && error.value === "empty";

    return (
        <PredicateField slot="value" error={isEmpty ? "empty" : undefined}>
            <Combobox.Root
                size="xs"
                variant="flushed"
                // The list is the only way to fill this in, so clicking opens it.
                openOnClick
                value={selected}
                collection={collection}
                onValueChange={(details) =>
                    setPredicate({
                        ...predicate,
                        // A list on the wire because SUBSTRING_IN is, but one code here.
                        value: { ...predicate.value, value: details.value.slice(0, 1) },
                    })
                }
                onInputValueChange={(details) => {
                    if (details.reason === "input-change") filter(details.inputValue);
                }}
                onOpenChange={(details) => {
                    if (!details.open) filter("");
                }}
            >
                <Combobox.Control>
                    <Combobox.Input
                        placeholder={t("symbology:filter.symbolValuePlaceholder")}
                        boxShadow="none"
                    />
                    <Combobox.IndicatorGroup>
                        <Combobox.ClearTrigger />
                        <Combobox.Trigger />
                    </Combobox.IndicatorGroup>
                </Combobox.Control>
                <Portal>
                    <Combobox.Positioner data-oc-combobox-portal>
                        <Combobox.Content maxH="min(384px, calc(100vh - 8rem))" overflowY="auto">
                            <Combobox.Empty>{t("symbology:filter.noValuesFound")}</Combobox.Empty>
                            {collection.items.map((option) => (
                                <Combobox.Item item={option.code} key={option.code}>
                                    <HStack gap="2" minW={0}>
                                        {option.frame && option.color && (
                                            <IdentityFrameIcon
                                                frame={option.frame}
                                                color={option.color}
                                            />
                                        )}
                                        <Text truncate>{label(option)}</Text>
                                    </HStack>
                                    <Combobox.ItemIndicator />
                                </Combobox.Item>
                            ))}
                        </Combobox.Content>
                    </Combobox.Positioner>
                </Portal>
            </Combobox.Root>
        </PredicateField>
    );
}
