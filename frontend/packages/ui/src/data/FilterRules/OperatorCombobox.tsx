// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Combobox,
    Portal,
    useFilter,
    useListCollection,
    type ComboboxRootProps,
} from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import {
    isNonEmptyPredicate,
    operators,
    type PredicateOperator,
    type PredicateDraft,
    type PredicateError,
} from "./predicate.model";
import { PredicateField } from "./PredicateField";

export type OperatorComboboxProps = {
    predicate: PredicateDraft;
    setOperator: (operator: PredicateOperator | undefined) => void;
    /** The operators to offer; all when absent. */
    operators?: readonly PredicateOperator[];
    error?: PredicateError["operator"];
} & Partial<ComboboxRootProps>;

export function OperatorCombobox({
    predicate,
    setOperator,
    operators: offered,
    error,
    ...rest
}: OperatorComboboxProps) {
    const { t } = useTranslation();
    const { contains } = useFilter({ sensitivity: "base" });
    const { collection, filter } = useListCollection({
        initialItems: Object.values(operators).filter(
            (def) => offered === undefined || offered.includes(def.operator),
        ),
        itemToValue: (def) => def.operator,
        itemToString: (def) => t(def.label),
        filter: contains,
    });

    return (
        <PredicateField slot="operator" error={error}>
            <Combobox.Root
                {...rest}
                size="xs"
                value={operatorValue(predicate)}
                collection={collection}
                onValueChange={(details) =>
                    setOperator(details.value[0] as PredicateOperator | undefined)
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
                        placeholder={t("filter.operationPlaceholder")}
                        boxShadow="none"
                    />
                    <Combobox.IndicatorGroup>
                        <Combobox.ClearTrigger />
                        <Combobox.Trigger />
                    </Combobox.IndicatorGroup>
                </Combobox.Control>
                <Portal>
                    <Combobox.Positioner data-oc-combobox-portal>
                        <Combobox.Content>
                            <Combobox.Empty>{t("filter.noOperationsFound")}</Combobox.Empty>
                            {collection.items.map((def) => (
                                <Combobox.Item item={def.operator} key={def.operator}>
                                    {t(def.label)}
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

function operatorValue(predicate: PredicateDraft): string[] {
    return isNonEmptyPredicate(predicate) ? [predicate.operator] : [];
}
