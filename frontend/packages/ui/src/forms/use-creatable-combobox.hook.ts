// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    createListCollection,
    useCombobox,
    useFilter,
    type UseComboboxProps,
} from "@chakra-ui/react";
import { useMemo, useState } from "react";

const TENTATIVE_VALUE = "[[tentative]]";
const TENTATIVE = Symbol("tentative");
type Tentative = { [TENTATIVE]: true; label: string };
const isTentative = (item: unknown): item is Tentative =>
    typeof item === "object" && item !== null && TENTATIVE in item;

export type UseCreatableComboboxProps<T = unknown> = {
    items: T[];
    value: string[];
    onValueChange: (value: string[]) => void;
    /** Creates the typed value; return the created item's value when it differs from the text. */
    onCreate: (value: string) => string | void;
    itemToString: (item: T) => string;
    itemToValue: (item: T) => string;

    /** Gate for adding a new value */
    validate?: (input: string) => boolean;
} & Partial<
    Omit<
        UseComboboxProps<unknown>,
        "value" | "onValueChange" | "collection" | "onInputValueChange" | "onOpenChange"
    >
>;

export function useCreatableCombobox<T = unknown>(props: UseCreatableComboboxProps<T>) {
    const { items, value, validate, onValueChange, onCreate, itemToString, itemToValue, ...rest } =
        props;
    const { contains } = useFilter({ sensitivity: "base" });
    const [query, setQuery] = useState("");

    const collection = useMemo(() => {
        const q = query.trim();
        const filtered = q ? items.filter((item) => contains(itemToString(item), q)) : items;
        const exists =
            q && items.some((item) => itemToString(item).toLowerCase() === q.toLowerCase());
        const display: (T | Tentative)[] =
            q && !exists && validate?.(q)
                ? [...filtered, { [TENTATIVE]: true, label: q }]
                : filtered;

        return createListCollection<T | Tentative>({
            items: display,
            itemToString: (item) => (isTentative(item) ? item.label : itemToString(item)),
            itemToValue: (item) => (isTentative(item) ? TENTATIVE_VALUE : itemToValue(item)),
        });
    }, [items, query, validate, contains, itemToString, itemToValue]);

    const combobox = useCombobox({
        ...rest,
        collection,
        value: value ?? [],
        onValueChange(details) {
            const selected = details.value.map((value) => {
                if (value !== TENTATIVE_VALUE) return value;
                const input = combobox.inputValue;
                return onCreate(input) ?? input;
            });

            onValueChange(selected);
        },
        onInputValueChange(details) {
            if (details.reason === "input-change") setQuery(details.inputValue);
        },
        onOpenChange(details) {
            if (!details.open) setQuery("");
        },
    });

    return {
        combobox,
        isTentative,
    };
}
