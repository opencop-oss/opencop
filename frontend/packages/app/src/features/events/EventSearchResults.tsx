// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, HStack, Text } from "@chakra-ui/react";
import { getElementKey } from "@opencop/elements";
import { useTranslation } from "react-i18next";
import { useMapManager } from "~/features/map/MapManagerContext";
import { useViewActions } from "~/features/map/core/slices/view/view.hook";
import { Tooltip, VirtualList } from "@opencop/ui";
import { EventListItem } from "./EventListItem";
import { focusElementFromList, selectFromList } from "./events-navigation.store";
import { MIN_QUERY_LENGTH, type SearchResult } from "./event-search.util";

const RESULT_ROW_HEIGHT = 88;
const RESULT_ROW_GAP = 6;

interface Props {
    results: SearchResult[];
    hasQuery: boolean;
    query: string;
}

function HighlightMatch({ text, query }: { text: string; query: string }) {
    if (!query) return <>{text}</>;
    const idx = text.toLocaleLowerCase().indexOf(query.toLocaleLowerCase());
    if (idx === -1) return <>{text}</>;
    return (
        <>
            {text.slice(0, idx)}
            <Box
                as="mark"
                bg="yellow.subtle"
                color="yellow.fg"
                borderRadius="sm"
                px="0.5"
                display="inline"
            >
                {text.slice(idx, idx + query.length)}
            </Box>
            {text.slice(idx + query.length)}
        </>
    );
}

interface FieldChipProps {
    label: string;
    value: string;
    query: string;
    uppercaseLabel?: boolean;
}

function FieldChip({ label, value, query, uppercaseLabel = false }: FieldChipProps) {
    return (
        <Tooltip content={value} uppercase={false}>
            <Flex align="baseline" gap="1" minW="0" maxW="fit-content">
                <Text
                    fontSize="xs"
                    color="fg.subtle"
                    fontFamily="mono"
                    textTransform={uppercaseLabel ? "uppercase" : undefined}
                    flexShrink={0}
                >
                    {uppercaseLabel ? <HighlightMatch text={label} query={query} /> : label}:
                </Text>
                <Text
                    fontSize="xs"
                    color="fg.muted"
                    fontFamily="mono"
                    overflow="hidden"
                    textOverflow="ellipsis"
                    whiteSpace="nowrap"
                    maxW="40"
                >
                    <HighlightMatch text={value} query={query} />
                </Text>
            </Flex>
        </Tooltip>
    );
}

export function EventSearchResults({ results, hasQuery, query }: Props) {
    const { t } = useTranslation();
    const mapManager = useMapManager();
    const viewActions = useViewActions();
    const resultCountMessage = hasQuery
        ? t("events.searchResultCount", { count: results.length })
        : "";

    if (!hasQuery) {
        const isTooShort = query.trim().length > 0 && query.trim().length < MIN_QUERY_LENGTH;
        return (
            <>
                <Text fontSize="xs" color="fg.muted" aria-live="polite">
                    {resultCountMessage}
                </Text>
                <Flex flex="1" align="center" justify="center" px="4">
                    <Text color="fg.muted" textAlign="center" maxW="sm" aria-live="polite">
                        {t(isTooShort ? "events.searchQueryTooShort" : "events.emptySearchPrompt")}
                    </Text>
                </Flex>
            </>
        );
    }

    return (
        <>
            <Text fontSize="xs" color="fg.muted" aria-live="polite">
                {resultCountMessage}
            </Text>
            {results.length === 0 && (
                <Flex flex="1" align="center" justify="center">
                    <Text color="fg.muted">{t("events.noSearchResults")}</Text>
                </Flex>
            )}
            {results.length > 0 && (
                <VirtualList
                    items={results}
                    estimateSize={() => RESULT_ROW_HEIGHT + RESULT_ROW_GAP}
                    gap={RESULT_ROW_GAP}
                    getItemKey={(r) => getElementKey(r.element)}
                    renderItem={(result, index) => {
                        const { element, matchedFields } = result;
                        const displayName = element.getDisplayName();
                        const normalizedQuery = query.trim();
                        return (
                            <EventListItem
                                element={element}
                                index={index}
                                onClick={() => selectFromList(mapManager, element, "search")}
                                onFocusOnMap={() =>
                                    focusElementFromList(mapManager, element, viewActions)
                                }
                                titleContent={
                                    <HighlightMatch text={displayName} query={normalizedQuery} />
                                }
                                detail={element.source}
                                metadata={
                                    <HStack w="full" justify="flex-start" flexWrap="wrap" gap="1">
                                        <FieldChip
                                            label={t("events.fieldSource")}
                                            value={element.source}
                                            query={normalizedQuery}
                                        />
                                        {matchedFields.slice(0, 3).map((field) => (
                                            <FieldChip
                                                key={field.label}
                                                label={field.label}
                                                value={field.value}
                                                query={normalizedQuery}
                                                uppercaseLabel
                                            />
                                        ))}
                                        {matchedFields.length > 3 && (
                                            <Text fontSize="xs" color="fg.subtle">
                                                {t("events.moreItems", {
                                                    count: matchedFields.length - 3,
                                                })}
                                            </Text>
                                        )}
                                    </HStack>
                                }
                            />
                        );
                    }}
                    containerProps={{ flex: 1, minH: "0" }}
                />
            )}
        </>
    );
}
