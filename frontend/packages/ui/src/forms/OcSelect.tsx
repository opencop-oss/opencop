// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { use, useMemo, type ReactNode, type RefObject } from "react";
import {
    Box,
    Flex,
    HStack,
    Portal,
    Select,
    Text,
    createListCollection,
    type SelectContentProps,
    type SelectControlProps,
    type SelectItemTextProps,
    type SelectTriggerProps,
    type SelectValueTextProps,
} from "@chakra-ui/react";
import { groupLabelStyleProps } from "../surfaces/groupLabel.ts";
import { DialogContainerContext } from "../overlays/BaseDialog.tsx";

export interface SelectItem {
    label: string;
    value: string;
    color?: string;
    icon?: ReactNode;
    disabled?: boolean;
}

/** A labeled section of items rendered as a `Select.ItemGroup`. */
export interface SelectGroup<T extends SelectItem = SelectItem> {
    label: string;
    items: T[];
}

/** Props shared by both the single- and multi-select shapes. */
interface OcSelectBaseProps<T extends SelectItem = SelectItem> {
    items?: T[];
    /**
     * Optional labeled sections. When provided, items render under group headers
     * (`Select.ItemGroupLabel`) and `items` is ignored. The flattened items still
     * form the value collection, so selection/value resolution is unchanged.
     */
    groups?: SelectGroup<T>[];
    placeholder?: string;
    disabled?: boolean;
    size?: "xs" | "sm" | "md" | "lg";
    renderItem?: (item: T) => ReactNode;
    portal?: boolean;
    /** Pass dialog content ref for proper stacking in modals. Auto-detected when inside BaseDialog. */
    containerRef?: RefObject<HTMLElement | null>;
    /** Message to display when items array is empty */
    emptyMessage?: string;
    /** Visual variant. "plain" renders as minimal text with no border or background. */
    variant?: "outline" | "subtle" | "plain";
    controlProps?: SelectControlProps;
    /** Props for the dropdown content (the open menu). Use to widen it past the trigger. */
    contentProps?: SelectContentProps;
    triggerProps?: SelectTriggerProps;
    itemTextProps?: SelectItemTextProps;
    valueTextProps?: SelectValueTextProps;
}

/** Single-select shape (default). `values`/multi props are disallowed. */
interface OcSelectSingleProps {
    multiple?: false;
    value?: string;
    defaultValue?: string;
    onValueChange?: (value: string) => void;
    values?: never;
    onValuesChange?: never;
    renderValueText?: never;
}

/**
 * Multi-select shape. Selected items show a checkmark and the dropdown stays
 * open while picking. Single-select props (`value`/`onValueChange`) are
 * disallowed; use `values`/`onValuesChange` and `renderValueText` to collapse
 * the trigger label (e.g. to "Multiple").
 */
interface OcSelectMultiProps<T extends SelectItem = SelectItem> {
    multiple: true;
    /** Selected values in multi-select mode. */
    values?: string[];
    /** Change handler in multi-select mode — receives the full new selection. */
    onValuesChange?: (values: string[]) => void;
    /**
     * Custom trigger content for the current selection (e.g. collapse to
     * "Multiple"). Receives the selected items; return `null` to fall back to
     * the placeholder.
     */
    renderValueText?: (selected: T[]) => ReactNode;
    value?: never;
    defaultValue?: never;
    onValueChange?: never;
}

/**
 * Discriminated on `multiple`: single-select callers pass `value`/`onValueChange`;
 * multi-select callers pass `multiple` + `values`/`onValuesChange`. Mixing the
 * two shapes is a compile error.
 */
export type OcSelectProps<T extends SelectItem = SelectItem> = OcSelectBaseProps<T> &
    (OcSelectSingleProps | OcSelectMultiProps<T>);

/** Select wrapper supporting single- and multi-select, discriminated on `multiple`. */
export function OcSelect<T extends SelectItem = SelectItem>({
    items,
    groups,
    value,
    defaultValue,
    onValueChange,
    multiple = false,
    values,
    onValuesChange,
    renderValueText,
    placeholder,
    disabled,
    size = "md",
    renderItem,
    portal = true,
    containerRef,
    emptyMessage,
    variant = "outline",
    ...rest
}: OcSelectProps<T>) {
    // Use provided containerRef, or fall back to dialog context (if inside a dialog)
    const dialogContainer = use(DialogContainerContext);
    const portalContainer = containerRef ?? dialogContainer;

    // Grouped and flat modes share one collection; the flattened group items
    // back value resolution so selection works the same either way.
    const flatItems = useMemo(
        () => (groups ? groups.flatMap((group) => group.items) : (items ?? [])),
        [groups, items],
    );

    const collection = useMemo(
        () =>
            createListCollection({
                items: flatItems,
                itemToString: (item) => item.label,
                itemToValue: (item) => item.value,
            }),
        [flatItems],
    );

    const renderSelectItem = (item: T, indented = false) => (
        <Select.Item key={item.value} item={item} ps={indented ? "6" : undefined}>
            {renderItem ? (
                renderItem(item)
            ) : (
                <Select.ItemText
                    {...rest.itemTextProps}
                    color={item.color ?? rest.itemTextProps?.color}
                    flexDir="row"
                    display="flex"
                    alignItems="center"
                    gap="1"
                >
                    {item.icon && (
                        <Box boxSize="4" flexShrink={0}>
                            {item.icon}
                        </Box>
                    )}
                    {item.label}
                </Select.ItemText>
            )}
            <Select.ItemIndicator />
        </Select.Item>
    );

    const positionerContent = (
        <Select.Positioner>
            <Select.Content {...rest.contentProps}>
                {collection.items.length === 0 && emptyMessage ? (
                    <Text px="3" py="2" fontSize="sm" color="fg.muted">
                        {emptyMessage}
                    </Text>
                ) : groups ? (
                    groups
                        .filter((group) => group.items.length > 0)
                        .map((group, groupIndex) => (
                            <Select.ItemGroup key={group.label}>
                                <Select.ItemGroupLabel {...groupLabelStyleProps(groupIndex)}>
                                    {group.label}
                                </Select.ItemGroupLabel>
                                {group.items.map((item) => renderSelectItem(item, true))}
                            </Select.ItemGroup>
                        ))
                ) : (
                    (flatItems as T[]).map((item) => renderSelectItem(item))
                )}
            </Select.Content>
        </Select.Positioner>
    );

    const isPlain = variant === "plain";
    const item = collection.items.find((v) => v.value === value);
    const selectedItems = multiple
        ? (collection.items.filter((v) => values?.includes(v.value)) as T[])
        : [];

    return (
        <Select.Root
            collection={collection}
            multiple={multiple}
            // Multi-select stays open while the user checks several options.
            closeOnSelect={!multiple}
            value={
                multiple ? (values ?? []) : value !== undefined ? (value ? [value] : []) : undefined
            }
            defaultValue={
                defaultValue !== undefined ? (defaultValue ? [defaultValue] : []) : undefined
            }
            onValueChange={(d) =>
                multiple ? onValuesChange?.(d.value) : onValueChange?.(d.value[0])
            }
            disabled={disabled}
            size={size}
            variant={isPlain ? undefined : variant}
        >
            <Select.HiddenSelect />
            <Select.Control bg="bg" minW="0" {...rest.controlProps}>
                <Select.Trigger
                    minW="0"
                    overflow="hidden"
                    {...rest.triggerProps}
                    borderColor={isPlain ? "transparent" : undefined}
                    bg={isPlain ? "transparent" : undefined}
                    px={isPlain ? "1" : undefined}
                    minH={isPlain ? "auto" : undefined}
                    _hover={isPlain ? { color: "fg" } : undefined}
                >
                    <Select.ValueText
                        placeholder={placeholder}
                        minW="0"
                        {...rest.valueTextProps}
                        fontSize={isPlain ? "xs" : undefined}
                        color={isPlain ? "fg" : undefined}
                    >
                        {multiple
                            ? renderValueText
                                ? renderValueText(selectedItems)
                                : selectedItems.length > 0
                                  ? selectedItems.map((i) => i.label).join(", ")
                                  : null
                            : item && (
                                  <HStack gap="1" minW="0" overflow="hidden">
                                      {item.icon && (
                                          <Flex boxSize="4" flexShrink={0} color={item.color}>
                                              {item.icon}
                                          </Flex>
                                      )}
                                      <Text
                                          {...rest.valueTextProps}
                                          color={item.color ?? rest.valueTextProps?.color}
                                          truncate
                                      >
                                          {item.label}
                                      </Text>
                                  </HStack>
                              )}
                    </Select.ValueText>
                </Select.Trigger>
                <Select.IndicatorGroup>
                    <Select.Indicator
                        color={isPlain ? "fg" : undefined}
                        boxSize={isPlain ? "3" : undefined}
                    />
                </Select.IndicatorGroup>
            </Select.Control>
            {portal ? (
                <Portal container={portalContainer ?? undefined}>{positionerContent}</Portal>
            ) : (
                positionerContent
            )}
        </Select.Root>
    );
}
