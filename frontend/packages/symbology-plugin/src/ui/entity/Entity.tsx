// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { OcSelect, SearchInput, type SelectItem, Section } from "@opencop/ui";
import { WIZARD_SECTION } from "../wizard-section";
import { useTranslation } from "react-i18next";
import { Catalog, type SIDC } from "@opencop/symbology";
import { LabeledInput } from "../LabeledInput";
import { EntityBrowser, type EntityBrowserValue } from "./EntityBrowser";
import { nodeKey } from "./node-key.util";
import { useSidc } from "../SidcContext";
import { items, selectProps } from "../editor.utils";
import { Checkbox, HStack, Text, VStack } from "@chakra-ui/react";
import { translateByKey } from "../translate-sidc.util";
import { sortBy } from "lodash";
import { useState } from "react";
import { useDebouncedValue } from "@tanstack/react-pacer";

/** Tree keys that must be open for `sidc`'s entity path to be visible in the browser. */
function expansionPath(sidc: SIDC): Set<string> {
    const keys = new Set<string>();
    if (sidc.entity !== "00") {
        keys.add(nodeKey(sidc.symbolSet, sidc.entity));
        if (sidc.entityType !== "00") {
            keys.add(nodeKey(sidc.symbolSet, sidc.entity, sidc.entityType));
        }
    }
    return keys;
}

export function Entity() {
    const { sidc, setSidc } = useSidc();
    const { t } = useTranslation(["translation", "symbology"]);

    const [filter, setFilter] = useState("");
    const [debouncedFilter] = useDebouncedValue(filter, { wait: 150 });
    const [matchFolders, setMatchFolders] = useState(true);
    const [matchItems, setMatchItems] = useState(true);
    const [expanded, setExpanded] = useState<Set<string>>(() => expansionPath(sidc));

    const symbolSets = Catalog.POINT_SYMBOL_SETS.array;
    const symbolSetItems = items(symbolSets, t);

    const symbolSetData = sidc.symbolSetData;

    // Sort "00" to the top if present. Symbol sets that declare "00" carry their
    // own label ("Unspecified", "Not Applicable", etc.) from the generated catalog, so we
    // never synthesise a label here. If "00" is absent, the select falls back
    // to its placeholder when the current value is "00".
    const buildModifiers = (
        source: Record<string, { localeKey: string; label: string }> | undefined,
    ): SelectItem[] => {
        const mods = Object.entries(source ?? {}).map(([code, mod]) => ({
            value: code,
            label: translateByKey(mod.localeKey, mod.label, t),
        }));
        return sortBy(mods, (m) => (m.value === "00" ? 0 : 1));
    };

    const modifiers1 = buildModifiers(symbolSetData?.sectorOneModifiers);
    const modifiers2 = buildModifiers(symbolSetData?.sectorTwoModifiers);

    function toggleExpanded(key: string) {
        setExpanded((prev) => {
            const next = new Set(prev);
            if (next.has(key)) next.delete(key);
            else next.add(key);
            return next;
        });
    }

    /**
     * A search result can live in any symbol set, so a pick carries the set with it.
     * Switching sets goes through `withSymbolSet` (which clears the entity path and
     * modifiers) before the picked path is reapplied.
     */
    function selectEntity({ symbolSet, entity, entityType, entitySubType }: EntityBrowserValue) {
        const base = symbolSet === sidc.symbolSet ? sidc : sidc.withSymbolSet(symbolSet);
        const next = base
            .withEntity(entity)
            .withEntityType(entityType)
            .withEntitySubType(entitySubType);
        setSidc(next);
        // Keep the path to the pick open once the search is cleared.
        setExpanded((prev) => new Set([...prev, ...expansionPath(next)]));
    }

    return (
        <Section {...WIZARD_SECTION} heading={t("symbology:symbolWizard.entity")}>
            <VStack w="full" gap="1">
                <SearchInput
                    value={filter}
                    onChange={setFilter}
                    placeholder={t("common.search")}
                    size="xs"
                    fill
                />
                {filter && (
                    <HStack w="full" gap="3" pl="1">
                        <Checkbox.Root
                            size="xs"
                            checked={matchFolders}
                            onCheckedChange={(e) => setMatchFolders(!!e.checked)}
                        >
                            <Checkbox.HiddenInput />
                            <Checkbox.Control>
                                <Checkbox.Indicator />
                            </Checkbox.Control>
                            <Checkbox.Label>
                                <Text textStyle="xs">
                                    {t("symbology:browser.matchDirectories")}
                                </Text>
                            </Checkbox.Label>
                        </Checkbox.Root>
                        <Checkbox.Root
                            size="xs"
                            checked={matchItems}
                            onCheckedChange={(e) => setMatchItems(!!e.checked)}
                        >
                            <Checkbox.HiddenInput />
                            <Checkbox.Control>
                                <Checkbox.Indicator />
                            </Checkbox.Control>
                            <Checkbox.Label>
                                <Text textStyle="xs">{t("symbology:browser.matchLeaves")}</Text>
                            </Checkbox.Label>
                        </Checkbox.Root>
                    </HStack>
                )}
            </VStack>
            <LabeledInput label={t("symbology:symbolWizard.symbolSet")}>
                <OcSelect
                    value={sidc.symbolSet}
                    items={symbolSetItems}
                    onValueChange={(value) => setSidc(sidc.withSymbolSet(value))}
                    {...selectProps}
                />
            </LabeledInput>
            <LabeledInput label={t("symbology:symbolWizard.entity")}>
                <EntityBrowser
                    symbolSets={symbolSets}
                    filter={filter === "" ? "" : debouncedFilter}
                    matchFolders={matchFolders}
                    matchItems={matchItems}
                    value={{
                        symbolSet: sidc.symbolSet,
                        entity: sidc.entity,
                        entityType: sidc.entityType,
                        entitySubType: sidc.entitySubType,
                    }}
                    onChange={selectEntity}
                    expanded={expanded}
                    onToggleExpanded={toggleExpanded}
                />
            </LabeledInput>
            <HStack w="full">
                <LabeledInput
                    key={sidc.symbolSet + "m1"}
                    label={t("symbology:symbolWizard.modifier1")}
                >
                    <OcSelect
                        items={modifiers1}
                        value={sidc.modifier1}
                        placeholder={t("symbology:symbolWizard.unspecified")}
                        disabled={modifiers1.length < 1}
                        onValueChange={(v) => setSidc(sidc.withModifier1(v))}
                        {...selectProps}
                    />
                </LabeledInput>
                <LabeledInput
                    key={sidc.symbolSet + "m2"}
                    label={t("symbology:symbolWizard.modifier2")}
                >
                    <OcSelect
                        items={modifiers2}
                        value={sidc.modifier2}
                        placeholder={t("symbology:symbolWizard.unspecified")}
                        disabled={modifiers2.length < 1}
                        onValueChange={(v) => setSidc(sidc.withModifier2(v))}
                        {...selectProps}
                    />
                </LabeledInput>
            </HStack>
        </Section>
    );
}
