// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo, type ReactNode, type RefObject } from "react";
import { Combobox } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { groupLabelStyleProps, SR_ONLY } from "../surfaces/groupLabel.ts";
import { OcCombobox } from "./OcCombobox.tsx";

// Grouping is flat (each item carries `group`) rather than nested like OcSelect's SelectGroup:
// OcCombobox text-filters a flat items array, so membership must survive per-item after filtering.
export interface OcGroupComboboxItem {
    label: string;
    value: string;
    /** Which group this item belongs to. Use `UNGROUPED_GROUP_ID` for items that belong to no named group. */
    group: string;
}

interface OcComboboxGroup {
    id: string;
    label: string;
}

interface OcGroupComboboxProps<T extends OcGroupComboboxItem = OcGroupComboboxItem> {
    items: T[];
    /**
     * Ordered group definitions. Only groups that have matching items after filtering are rendered.
     * ids must be unique and must not equal `UNGROUPED_GROUP_ID`.
     */
    groups: OcComboboxGroup[];
    value: string | string[];
    onValueChange: (value: string | string[]) => void;
    multiple?: boolean;
    placeholder?: string;
    disabled?: boolean;
    /** Shows selected items as removable tags above input. Defaults to true when multiple. */
    showTags?: boolean;
    emptyMessage?: string;
    renderItem?: (item: T) => ReactNode;
    portal?: boolean;
    openOnClick?: boolean;
    /** Pass dialog content ref for proper stacking in modals. Auto-detected when inside BaseDialog. */
    containerRef?: RefObject<HTMLElement | null>;
    /**
     * Visible section label for ungrouped items (items with group: UNGROUPED_GROUP_ID). When omitted
     * the label is still rendered but visually hidden so aria-labelledby resolves correctly (WCAG 4.1.2).
     */
    ungroupedLabel?: string;
}

/**
 * Sentinel value for `OcGroupComboboxItem.group` indicating an item belongs to no named group.
 * Import this constant in parent components instead of using a bare empty string.
 */
export const UNGROUPED_GROUP_ID = "__ungrouped__";

/**
 * zag-js always emits aria-labelledby on Combobox.ItemGroup pointing to the matching label element id.
 * When no Combobox.ItemGroupLabel is rendered the reference dangles (WCAG 4.1.2 violation). When
 * ungroupedLabel is not provided we still render the label but visually hide it with SR_ONLY styles.
 */

/** Searchable combobox with grouped items styled to match OcSelect group headers. */
export function OcGroupCombobox<T extends OcGroupComboboxItem = OcGroupComboboxItem>({
    items,
    groups,
    value,
    onValueChange,
    multiple = false,
    placeholder,
    disabled,
    showTags,
    emptyMessage,
    renderItem,
    portal = true,
    openOnClick = true,
    containerRef,
    ungroupedLabel,
}: OcGroupComboboxProps<T>) {
    const { t } = useTranslation();

    // zag drives Arrow/Home/End off the collection built from `items`, so the array order must
    // match the visual render order produced by `renderContent` below. Items with an unknown group
    // id are also filtered out here: renderContent doesn't emit a section for them, so they would
    // be keyboard-reachable but have no DOM node — their aria-activedescendant would dangle (WCAG 4.1.2).
    const orderedItems = useMemo(() => {
        const rank = new Map(groups.map((g, i) => [g.id, i]));
        return (
            [...items]
                // renderContent only draws known groups + the ungrouped bucket; anything else must stay
                // out of the collection too, or zag highlights an option with no DOM node.
                .filter((i) => rank.has(i.group) || i.group === UNGROUPED_GROUP_ID)
                .sort(
                    (a, b) =>
                        (rank.get(a.group) ?? groups.length) - (rank.get(b.group) ?? groups.length),
                )
        );
    }, [items, groups]);

    function renderItemNode(item: T): ReactNode {
        return renderItem ? renderItem(item) : <Combobox.ItemText>{item.label}</Combobox.ItemText>;
    }

    function renderGroupLabel(label: string, sectionIndex: number): ReactNode {
        return (
            <Combobox.ItemGroupLabel {...groupLabelStyleProps(sectionIndex)}>
                {label}
            </Combobox.ItemGroupLabel>
        );
    }

    // OcCombobox text-filters a flat `items` array, so group membership must survive per item
    // after filtering. Containment (like SelectGroup.items) can't express that without
    // re-partitioning on every keystroke — which is why this component uses a foreign-key model
    // while OcSelect (no search input) uses containment.
    function renderContent(filteredItems: T[]): ReactNode {
        const sections: ReactNode[] = [];

        for (const group of groups) {
            if (group.id === UNGROUPED_GROUP_ID) continue; // the ungrouped bucket is drawn once, below
            const groupItems = filteredItems.filter((item) => item.group === group.id);
            if (groupItems.length === 0) continue;

            sections.push(
                // zag namespaces group ids as `combobox:${ctx.id}:optgroup:${id}`, so the same
                // id (e.g. "recents") is safe to reuse across multiple mounted instances.
                <Combobox.ItemGroup key={group.id} id={group.id}>
                    {renderGroupLabel(group.label, sections.length)}
                    {groupItems.map((item) => (
                        <Combobox.Item key={item.value} item={item} ps="6">
                            {renderItemNode(item)}
                            <Combobox.ItemIndicator />
                        </Combobox.Item>
                    ))}
                </Combobox.ItemGroup>,
            );
        }

        const ungrouped = filteredItems.filter((item) => item.group === UNGROUPED_GROUP_ID);
        if (ungrouped.length > 0) {
            sections.push(
                <Combobox.ItemGroup key={UNGROUPED_GROUP_ID} id={UNGROUPED_GROUP_ID}>
                    {ungroupedLabel ? (
                        renderGroupLabel(ungroupedLabel, sections.length)
                    ) : (
                        // style= not Chakra's srOnly prop: jsdom doesn't load Panda CSS stylesheets,
                        // so toHaveStyle can only assert inline styles.
                        <Combobox.ItemGroupLabel style={SR_ONLY}>
                            {t("common.other")}
                        </Combobox.ItemGroupLabel>
                    )}
                    {ungrouped.map((item) => (
                        <Combobox.Item
                            key={item.value}
                            item={item}
                            ps={ungroupedLabel ? "6" : undefined}
                        >
                            {renderItemNode(item)}
                            <Combobox.ItemIndicator />
                        </Combobox.Item>
                    ))}
                </Combobox.ItemGroup>,
            );
        }

        return sections;
    }

    return (
        <OcCombobox
            items={orderedItems}
            value={value}
            onValueChange={onValueChange}
            multiple={multiple}
            placeholder={placeholder}
            disabled={disabled}
            showTags={showTags}
            emptyMessage={emptyMessage}
            renderContent={renderContent}
            portal={portal}
            openOnClick={openOnClick}
            containerRef={containerRef}
        />
    );
}
