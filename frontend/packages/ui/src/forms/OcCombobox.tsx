// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { use, useMemo, useState, type ReactNode, type RefObject } from "react";
import { Combobox, Portal, Wrap, createListCollection } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { DialogContainerContext } from "../overlays/BaseDialog.tsx";
import { OcTag } from "./OcTag.tsx";

export interface ComboboxItem {
    label: string;
    value: string;
}

export interface OcComboboxProps<T extends ComboboxItem = ComboboxItem> {
    items: T[];
    value: string | string[];
    onValueChange: (value: string | string[]) => void;
    multiple?: boolean;
    placeholder?: string;
    disabled?: boolean;
    /** Shows selected items as removable tags above input. Defaults to true when multiple. */
    showTags?: boolean;
    /**
     * Drops already-selected options from the list, leaving only what can still
     * be added. Safe only where selections stay visible and removable
     * elsewhere, which `showTags` provides.
     */
    hideSelectedOptions?: boolean;
    emptyMessage?: string;
    renderItem?: (item: T) => ReactNode;
    /** Replaces the default flat item list inside Combobox.Content. Receives filtered items. */
    renderContent?: (filteredItems: T[]) => ReactNode;
    portal?: boolean;
    openOnClick?: boolean;
    /** Pass dialog content ref for proper stacking in modals. Auto-detected when inside BaseDialog. */
    containerRef?: RefObject<HTMLElement | null>;
    /** Fires when the input loses focus — wire to a form's blur-triggered validation. */
    onBlur?: () => void;
}

/** Searchable select with optional multi-select and tag display. */
export function OcCombobox<T extends ComboboxItem = ComboboxItem>({
    items,
    value,
    onValueChange,
    multiple = false,
    placeholder,
    disabled,
    showTags,
    hideSelectedOptions = false,
    emptyMessage,
    renderItem,
    renderContent,
    portal = true,
    openOnClick = true,
    containerRef,
    onBlur,
}: OcComboboxProps<T>) {
    const { t } = useTranslation();
    // Use provided containerRef, or fall back to dialog context (if inside a dialog)
    const dialogContainer = use(DialogContainerContext);
    const portalContainer = containerRef ?? dialogContainer;

    const [inputValue, setInputValue] = useState("");

    // Normalize value to array for internal use
    const valueArray = useMemo(
        () => (Array.isArray(value) ? value : value ? [value] : []),
        [value],
    );

    // NOTE: We use createListCollection + useMemo instead of useListCollection.
    // useListCollection's initialItems is not reactive to prop changes, requiring
    // a useEffect to sync - an antipattern. This approach derives state directly
    // from props, making the collection reactive without effects.
    const filteredItems = useMemo(() => {
        // Selection filtering happens before search so the two compose.
        const base = hideSelectedOptions
            ? items.filter((item) => !valueArray.includes(item.value))
            : items;
        if (!inputValue) return base;
        const search = inputValue.toLowerCase();
        return base.filter((item) => item.label.toLowerCase().includes(search));
    }, [items, inputValue, hideSelectedOptions, valueArray]);

    const collection = useMemo(
        () =>
            createListCollection({
                items: filteredItems,
                itemToString: (item) => item.label,
                itemToValue: (item) => item.value,
            }),
        [filteredItems],
    );

    const handleValueChange = (details: { value: string[] }) => {
        if (multiple) {
            onValueChange(details.value);
        } else {
            onValueChange(details.value[0] ?? "");
        }
    };

    const handleRemoveTag = (val: string) => {
        if (multiple) {
            onValueChange(valueArray.filter((v) => v !== val));
        }
    };

    const shouldShowTags = showTags ?? multiple;

    const positionerContent = (
        <Combobox.Positioner data-oc-combobox-portal>
            <Combobox.Content>
                <Combobox.Empty>{emptyMessage ?? t("common.noResults")}</Combobox.Empty>
                {renderContent
                    ? renderContent(filteredItems)
                    : collection.items.map((item) => (
                          <Combobox.Item key={item.value} item={item}>
                              {renderItem ? (
                                  renderItem(item as T)
                              ) : (
                                  <Combobox.ItemText>{item.label}</Combobox.ItemText>
                              )}
                              <Combobox.ItemIndicator />
                          </Combobox.Item>
                      ))}
            </Combobox.Content>
        </Combobox.Positioner>
    );

    return (
        <Combobox.Root
            collection={collection}
            multiple={multiple}
            value={valueArray}
            onValueChange={handleValueChange}
            onInputValueChange={(d) => setInputValue(d.inputValue)}
            openOnClick={openOnClick}
            disabled={disabled}
        >
            {shouldShowTags && valueArray.length > 0 && (
                <Wrap gap="2" mb="2">
                    {valueArray.map((val) => {
                        const item = items.find((o) => o.value === val);
                        const label = item?.label ?? val;
                        return (
                            <OcTag
                                key={val}
                                closable
                                closeLabel={t("common.removeItem", { item: label })}
                                onClose={() => handleRemoveTag(val)}
                            >
                                {label}
                            </OcTag>
                        );
                    })}
                </Wrap>
            )}
            <Combobox.Control flexWrap="wrap">
                <Combobox.Input placeholder={placeholder} onBlur={onBlur} />
                <Combobox.IndicatorGroup>
                    <Combobox.ClearTrigger />
                    <Combobox.Trigger />
                </Combobox.IndicatorGroup>
            </Combobox.Control>
            {portal ? (
                <Portal container={portalContainer ?? undefined}>{positionerContent}</Portal>
            ) : (
                positionerContent
            )}
        </Combobox.Root>
    );
}
