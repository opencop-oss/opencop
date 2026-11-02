// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Button, Center, HStack, Text, VStack } from "@chakra-ui/react";
import { useMapFilterStore, type MapFilter } from "./map-filter.store";
import { FilterCard } from "./FilterCard";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { MapFilterBuilder } from "./MapFilterBuilder";
import { AccentHeading, defaultPredicateDraft, type FilterDraft } from "@opencop/ui";

export function MapFilterPanel() {
    const { t } = useTranslation();
    const filters = useMapFilterStore((s) => s.filters);
    const { addFilter, activateFilter, deactivateFilter, removeFilter } = useMapFilterStore(
        (s) => s.actions,
    );
    const [draft, setDraft] = useState<FilterDraft | null>(null);

    return (
        <VStack w="full" h="full" minH="0">
            {draft ? (
                <MapFilterBuilder
                    defaultValue={draft}
                    onSave={(filter: MapFilter) => {
                        setDraft(null);
                        addFilter(filter);
                    }}
                    onBack={() => setDraft(null)}
                />
            ) : (
                <>
                    <AccentHeading>{t("mapbar.mapFilter")}</AccentHeading>
                    <HStack w="full" justify="end">
                        <Button
                            bg="active"
                            onClick={() =>
                                setDraft({
                                    combinator: "ALL",
                                    predicates: [defaultPredicateDraft({ operator: "EQUALS" })],
                                })
                            }
                        >
                            {t("filter.createFilter")}
                        </Button>
                    </HStack>
                    {filters.length === 0 ? (
                        <Center w="full" h="full">
                            <Text fontSize="xs" color="fg.muted">
                                {t("filter.noFilters")}
                            </Text>
                        </Center>
                    ) : (
                        <VStack w="full" flex="1" minH="0" overflowY="auto">
                            {filters.map((filter) => (
                                <FilterCard
                                    key={filter.name}
                                    filter={filter}
                                    activate={() => activateFilter(filter.name)}
                                    deactivate={() => deactivateFilter(filter.name)}
                                    remove={() => removeFilter(filter.name)}
                                />
                            ))}
                        </VStack>
                    )}
                </>
            )}
        </VStack>
    );
}
