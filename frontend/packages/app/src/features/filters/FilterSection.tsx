// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Spinner, Text, IconButton, VStack, HStack, Flex } from "@chakra-ui/react";
import type { ReactNode } from "react";
import { useFilterPanelStore, type SortOrder } from "./filter-panel.store";
import { SortIcon } from "./SortIcon";
import { CollapsibleSection, Divider } from "@opencop/ui";
import { useTranslation } from "react-i18next";
import { VisibilityMenu } from "./VisibilityMenu";

interface FilterSectionProps {
    id: string;
    label: string;
    emptyMessage: string;
    isLoading: boolean;
    itemCount: number;
    allVisible: boolean;
    anyVisible: boolean;
    anySyncing: boolean;
    anyTextVisible: boolean;
    anyTextHidden: boolean;
    sortOrder: SortOrder;
    /** The sort button is a dead control when the list order is fixed by the active filter. */
    sortDisabled?: boolean;
    flexSize: number;
    /** Open state used until the user has toggled the section; their choice is then persisted. */
    defaultExpanded?: boolean;
    minSectionH?: number;
    showAllItems: () => void;
    hideAllItems: () => void;
    showAllText: () => void;
    hideAllText: () => void;
    onToggleSort: () => void;
    children: ReactNode;
}

export function FilterSection({
    id,
    label,
    emptyMessage,
    isLoading,
    itemCount,
    allVisible,
    anyVisible,
    anySyncing,
    anyTextVisible,
    anyTextHidden,
    sortOrder,
    sortDisabled = false,
    flexSize,
    defaultExpanded = true,
    minSectionH,
    showAllItems,
    hideAllItems,
    showAllText,
    hideAllText,
    onToggleSort,
    children,
}: FilterSectionProps) {
    const { t } = useTranslation();
    const isExpanded = useFilterPanelStore((s) => s.expandedSections[id] ?? defaultExpanded);
    const setSectionExpanded = useFilterPanelStore((s) => s.setSectionExpanded);
    const toggleExpanded = () => setSectionExpanded(id, !isExpanded);

    return (
        <CollapsibleSection
            label={label}
            ariaLabel={`${t("common.toggleAccordion")} ${label}`}
            isOpen={isExpanded}
            onToggle={toggleExpanded}
            flexSize={flexSize}
            minSectionH={minSectionH}
            rightSlot={
                <HStack gap="1">
                    <IconButton
                        size="xs"
                        variant="plain"
                        color="fg.muted"
                        onClick={onToggleSort}
                        disabled={sortDisabled}
                        aria-label={`${t("common.sortBy")} ${label}`}
                    >
                        <SortIcon sort={sortOrder} />
                    </IconButton>

                    <Divider orientation="vertical" h="4" />

                    <VisibilityMenu
                        allVisible={allVisible}
                        anyVisible={anyVisible}
                        anySyncing={anySyncing}
                        anyTextHidden={anyTextHidden}
                        anyTextVisible={anyTextVisible}
                        showAll={showAllItems}
                        hideAll={hideAllItems}
                        showAllText={showAllText}
                        hideAllText={hideAllText}
                    />
                </HStack>
            }
        >
            {isLoading ? (
                <Flex align="center" justify="center" h="200px" w="full">
                    <Spinner size="lg" color="active" aria-label={t("common.loading")} />
                </Flex>
            ) : itemCount === 0 ? (
                <VStack w="full" py={10} gap={2}>
                    <Text color="fg.muted" fontSize="sm">
                        {emptyMessage}
                    </Text>
                </VStack>
            ) : (
                <VStack gap={1}>{children}</VStack>
            )}
        </CollapsibleSection>
    );
}
