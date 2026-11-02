// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, Icon, Text, VisuallyHidden, VStack } from "@chakra-ui/react";
import type { SIDCEntity, SIDCEntityType, SIDCLabel, SIDCSymbolSet } from "@opencop/symbology";
import { noop, sortBy } from "lodash";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { LuCheck, LuChevronDown, LuChevronRight } from "react-icons/lu";
import { translateByKey } from "../translate-sidc.util";
import { nodeKey } from "./node-key.util";

export interface EntityBrowserValue {
    symbolSet: string;
    entity: string;
    entityType: string;
    entitySubType: string;
}

export interface EntityBrowserProps {
    /**
     * Every symbol set the editor can reach. With no `filter` only the set named by
     * `value.symbolSet` is browsable; while filtering, all of them are searched and
     * results are grouped by set.
     */
    symbolSets: readonly [string, SIDCSymbolSet][];
    filter: string;
    /** Match entity and entity-type labels (the tree's interior nodes). */
    matchFolders: boolean;
    /** Match entity-subtype labels (the tree's leaves). */
    matchItems: boolean;
    /** Selects which set is browsable when unfiltered, and which rows read as selected. */
    value: EntityBrowserValue;
    onChange?: (value: EntityBrowserValue) => void;
    expanded: ReadonlySet<string>;
    onToggleExpanded: (key: string) => void;
}

/** Searchable tree browser for SIDC entity / type / subtype hierarchy. */
export function EntityBrowser({
    symbolSets,
    filter,
    matchFolders,
    matchItems,
    value,
    onChange,
    expanded,
    onToggleExpanded,
}: EntityBrowserProps) {
    const { t } = useTranslation(["translation", "symbology"]);

    const lowerFilter = filter.toLowerCase();

    // Symbol set sorted to the top of the results, anchored to whatever was selected
    // when the search began. Re-anchoring on every selection would yank the
    // just-clicked group to the top mid-scan.
    const [pinnedSymbolSet, setPinnedSymbolSet] = useState(value.symbolSet);
    const [prevFilter, setPrevFilter] = useState(filter);
    if (filter !== prevFilter) {
        setPrevFilter(filter);
        if (!prevFilter && filter) setPinnedSymbolSet(value.symbolSet);
    }

    // When filtering, check if any descendant matches. Symbol-set labels are
    // deliberately not matchable: a set name match would inherit down to every
    // entity it owns and bury the real hits under hundreds of rows.
    const entityMatches = useMemo(() => {
        if (!lowerFilter) return null;
        const matches = new Map<string, boolean>();

        for (const [setCode, symbolSet] of symbolSets) {
            for (const [entityCode, entity] of Object.entries(symbolSet.entities)) {
                const entityLabelMatch =
                    matchFolders &&
                    translateByKey(entity.localeKey, entity.label, t)
                        .toLowerCase()
                        .includes(lowerFilter);
                let entityMatch = entityLabelMatch;
                for (const [typeCode, type] of Object.entries(entity.entityTypes)) {
                    const typeLabelMatch =
                        matchFolders &&
                        translateByKey(type.localeKey, type.label, t)
                            .toLowerCase()
                            .includes(lowerFilter);
                    let typeMatch = typeLabelMatch;
                    for (const [subtypeCode, subtype] of Object.entries(type.entitySubTypes)) {
                        const itemLabelMatch =
                            matchItems &&
                            translateByKey(subtype.localeKey, subtype.label, t)
                                .toLowerCase()
                                .includes(lowerFilter);
                        const subtypeMatch = itemLabelMatch || entityLabelMatch || typeLabelMatch;
                        matches.set(
                            nodeKey(setCode, entityCode, typeCode, subtypeCode),
                            subtypeMatch,
                        );
                        if (subtypeMatch) {
                            typeMatch = true;
                        }
                    }
                    matches.set(nodeKey(setCode, entityCode, typeCode), typeMatch);
                    if (typeMatch) entityMatch = true;
                }
                matches.set(nodeKey(setCode, entityCode), entityMatch);
            }
        }
        return matches;
    }, [symbolSets, lowerFilter, matchFolders, matchItems, t]);

    function isVisible(key: string): boolean {
        if (!entityMatches) return true;
        return entityMatches.get(key) ?? false;
    }

    // Unfiltered, the browser stays scoped to the selected set; filtered, it spans
    // every set that still has a visible entity.
    const visibleSets = useMemo(() => {
        if (!lowerFilter) {
            return symbolSets.filter(([setCode]) => setCode === value.symbolSet);
        }
        const withMatches = symbolSets.filter(([setCode, symbolSet]) =>
            Object.keys(symbolSet.entities).some((entityCode) =>
                entityMatches?.get(nodeKey(setCode, entityCode)),
            ),
        );
        // The pinned set first: "search within what I'm already on" is the common case.
        return sortBy(withMatches, ([setCode]) => (setCode === pinnedSymbolSet ? 0 : 1));
    }, [symbolSets, lowerFilter, entityMatches, value.symbolSet, pinnedSymbolSet]);

    return (
        <VStack
            data-testid="entity-browser"
            align="start"
            bg="bg.elevated"
            w="full"
            h="52"
            gap="0"
            overflowY="auto"
            scrollbar="hidden"
            borderWidth="1px"
            borderRadius="2px"
        >
            {/* Always mounted so screen readers catch the text change. */}
            <VisuallyHidden role="status">
                {!!lowerFilter && visibleSets.length === 0 ? t("common.noResults") : ""}
            </VisuallyHidden>
            {!!lowerFilter && visibleSets.length === 0 && (
                <Text textStyle="xs" color="fg.subtle" px="2" py="2">
                    {t("common.noResults")}
                </Text>
            )}
            {visibleSets.map(([setCode, symbolSet]) => {
                const setLabel = translateByKey(symbolSet.localeKey, symbolSet.label, t);
                return (
                    // Grouped so a row read on its own still carries its symbol set.
                    <Box
                        key={setCode}
                        w="full"
                        role={lowerFilter ? "group" : undefined}
                        aria-label={lowerFilter ? setLabel : undefined}
                    >
                        {!!lowerFilter && <SymbolSetHeader label={setLabel} code={setCode} />}
                        {renderEntities(setCode, symbolSet.entities)}
                    </Box>
                );
            })}
        </VStack>
    );

    function renderEntities(setCode: string, entities: Record<string, SIDCEntity>) {
        return Object.entries(entities).map(([entityCode, entity]) => {
            const key = nodeKey(setCode, entityCode);
            if (!isVisible(key)) return null;
            const hasChildren = Object.keys(entity.entityTypes).length > 0;
            const isExpanded = expanded.has(key) || !!lowerFilter;
            const isSelected =
                value.symbolSet === setCode &&
                value.entity === entityCode &&
                value.entityType === "00" &&
                value.entitySubType === "00";

            return (
                <Box key={key} w="full">
                    <TreeRow
                        depth={0}
                        label={translateByKey(entity.localeKey, entity.label, t)}
                        code={entityCode}
                        hasChildren={hasChildren}
                        isExpanded={isExpanded}
                        isSelected={isSelected}
                        onToggle={() => onToggleExpanded(key)}
                        onSelect={() =>
                            onChange?.({
                                symbolSet: setCode,
                                entity: entityCode,
                                entityType: "00",
                                entitySubType: "00",
                            })
                        }
                        highlightText={lowerFilter}
                    />
                    {isExpanded &&
                        hasChildren &&
                        renderTypes(setCode, entityCode, entity.entityTypes)}
                </Box>
            );
        });
    }

    function renderTypes(
        setCode: string,
        entityCode: string,
        types: Record<string, SIDCEntityType>,
    ) {
        return Object.entries(types).map(([typeCode, type]) => {
            const key = nodeKey(setCode, entityCode, typeCode);
            if (!isVisible(key)) return null;
            const hasChildren = Object.keys(type.entitySubTypes).length > 0;
            const isExpanded = expanded.has(key) || !!lowerFilter;
            const isSelected =
                value.symbolSet === setCode &&
                value.entity === entityCode &&
                value.entityType === typeCode &&
                value.entitySubType === "00";

            return (
                <Box key={key} w="full">
                    <TreeRow
                        depth={1}
                        label={translateByKey(type.localeKey, type.label, t)}
                        code={typeCode}
                        hasChildren={hasChildren}
                        isExpanded={isExpanded}
                        isSelected={isSelected}
                        onToggle={() => onToggleExpanded(key)}
                        onSelect={() =>
                            onChange?.({
                                symbolSet: setCode,
                                entity: entityCode,
                                entityType: typeCode,
                                entitySubType: "00",
                            })
                        }
                        highlightText={lowerFilter}
                    />
                    {isExpanded &&
                        hasChildren &&
                        renderSubtypes(setCode, entityCode, typeCode, type.entitySubTypes)}
                </Box>
            );
        });
    }

    function renderSubtypes(
        setCode: string,
        entityCode: string,
        typeCode: string,
        subtypes: Record<string, SIDCLabel>,
    ) {
        return Object.entries(subtypes).map(([subtypeCode, subtype]) => {
            const key = nodeKey(setCode, entityCode, typeCode, subtypeCode);
            if (!isVisible(key)) return null;
            const isSelected =
                value.symbolSet === setCode &&
                value.entity === entityCode &&
                value.entityType === typeCode &&
                value.entitySubType === subtypeCode;

            return (
                <TreeRow
                    key={key}
                    depth={2}
                    label={translateByKey(subtype.localeKey, subtype.label, t)}
                    code={subtypeCode}
                    hasChildren={false}
                    isExpanded={false}
                    isSelected={isSelected}
                    onToggle={noop}
                    onSelect={() =>
                        onChange?.({
                            symbolSet: setCode,
                            entity: entityCode,
                            entityType: typeCode,
                            entitySubType: subtypeCode,
                        })
                    }
                    highlightText={lowerFilter}
                />
            );
        });
    }
}

// ---------------------------------------------------------------------------
// Symbol set group header
// ---------------------------------------------------------------------------

/** Groups cross-symbol-set search results; not selectable, the rows beneath it are. */
function SymbolSetHeader({ label, code }: { label: string; code: string }) {
    return (
        <Flex
            data-testid="symbol-set-header"
            aria-hidden
            w="full"
            h="6"
            alignItems="center"
            px="2"
            gap="1"
            pos="sticky"
            top="0"
            zIndex="1"
            bg="bg.subtle"
            borderBottomWidth="1px"
            userSelect="none"
        >
            <Text
                flex="1"
                textStyle="2xs"
                fontWeight="bold"
                color="fg.muted"
                truncate
                title={label}
            >
                {label}
            </Text>
            <Text textStyle="2xs" fontFamily="mono" color="fg.subtle" flexShrink={0}>
                {code}
            </Text>
        </Flex>
    );
}

// ---------------------------------------------------------------------------
// Tree row
// ---------------------------------------------------------------------------

interface TreeRowProps {
    depth: number;
    label: string;
    code: string;
    hasChildren: boolean;
    isExpanded: boolean;
    isSelected: boolean;
    onToggle: () => void;
    onSelect: () => void;
    highlightText?: string;
}

function TreeRow({
    depth,
    label,
    code,
    hasChildren,
    isExpanded,
    isSelected,
    onToggle,
    onSelect,
    highlightText,
}: TreeRowProps) {
    const renderLabel = () => {
        if (!highlightText) {
            return label;
        }

        const lowerLabel = label.toLowerCase();
        const index = lowerLabel.indexOf(highlightText);

        if (index === -1) {
            return label;
        }

        const before = label.slice(0, index);
        const match = label.slice(index, index + highlightText.length);
        const after = label.slice(index + highlightText.length);

        return (
            <>
                {before}
                <Box as="span" bg="active/30" color="inherit" px="0.5">
                    {match}
                </Box>
                {after}
            </>
        );
    };

    return (
        <Flex
            w="full"
            h="7"
            alignItems="center"
            pl={`${depth * 16 + 4}px`}
            pr="2"
            gap="1"
            cursor="pointer"
            bg="transparent"
            _hover={{ bg: "bg.subtle" }}
            onClick={onSelect}
            userSelect="none"
        >
            {/* Disclosure icon */}
            <Box
                w="4"
                h="4"
                flexShrink={0}
                display="flex"
                alignItems="center"
                justifyContent="center"
                onClick={(e) => {
                    if (hasChildren && !highlightText) {
                        e.stopPropagation();
                        onToggle();
                    }
                }}
            >
                {hasChildren && !highlightText && (
                    <Icon size="xs" color="fg.muted">
                        {isExpanded ? <LuChevronDown /> : <LuChevronRight />}
                    </Icon>
                )}
            </Box>

            {/* Label */}
            <Text flex="1" textStyle="xs" color={isSelected ? "active" : "fg.muted"} truncate>
                {renderLabel()}
            </Text>

            {/* Check mark */}
            {isSelected && (
                <Icon size="xs" color="active" flexShrink={0}>
                    <LuCheck />
                </Icon>
            )}

            {/* Code */}
            <Text textStyle="2xs" fontFamily="mono" color="fg.subtle" flexShrink={0}>
                {code}
            </Text>
        </Flex>
    );
}
