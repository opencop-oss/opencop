// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Text } from "@chakra-ui/react";
import { useAllElements } from "@opencop/ingest";
import type { Element } from "@opencop/elements";
import { useDebouncedValue, useThrottledValue } from "@tanstack/react-pacer";
import { useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { ErrorBoundaryWrapper } from "~/features/errors/ErrorBoundaryWrapper";
import { useHistoricalMode } from "~/features/historical/data-plane/historical.store";
import { EventSearchInput } from "./EventSearchInput";
import { EventSearchResults } from "./EventSearchResults";
import { EventsListLayout } from "./EventsListLayout";
import { MIN_QUERY_LENGTH, searchElements } from "./event-search.util";

const SEARCH_DEBOUNCE_MS = 250;
/** Caps how often the live element set can force a re-search while the panel
 * is open — otherwise every WS add/update/delete for any of the ~8,000 live
 * elements (not just query changes) re-runs the full match+sort. */
const ELEMENTS_THROTTLE_MS = 150;

interface EventsListSearchStartProps {
    query: string;
    onQueryChange: (query: string) => void;
}

/**
 * Thin guard: replaces the search UI with an unavailable notice in historical
 * mode so that `EventsListSearchContent`'s hooks never run against stale live data.
 */
export function EventsListSearchStart({ query, onQueryChange }: EventsListSearchStartProps) {
    const isHistorical = useHistoricalMode() === "historical";
    if (isHistorical) return <EventsSearchUnavailable />;
    return <EventsListSearchContent query={query} onQueryChange={onQueryChange} />;
}

function EventsSearchUnavailable() {
    const { t } = useTranslation();
    return (
        <EventsListLayout>
            <Flex flex="1" w="full" align="center" justify="center" px="6">
                <Text fontSize="sm" color="fg.muted" textAlign="center">
                    {t("events.searchUnavailableHistorical")}
                </Text>
            </Flex>
        </EventsListLayout>
    );
}

function EventsListSearchContent({ query, onQueryChange }: EventsListSearchStartProps) {
    const hasQuery = query.trim().length >= MIN_QUERY_LENGTH;
    const elements = useAllElements({ enabled: hasQuery });
    const [throttledElements] = useThrottledValue(elements, { wait: ELEMENTS_THROTTLE_MS });
    const [debouncedQuery, debouncer] = useDebouncedValue(query, { wait: SEARCH_DEBOUNCE_MS });
    // Clearing the input reverts to the empty-search prompt immediately rather
    // than lagging behind the trailing debounce window — there's no expensive
    // work to defer for a blank query, and a stale "no results" flash reads as
    // a bug.
    const effectiveQuery = hasQuery ? debouncedQuery : query;
    const hasDebouncedQuery = effectiveQuery.trim().length >= MIN_QUERY_LENGTH;

    // Force the debouncer to settle on the empty query the moment the input is
    // cleared. Otherwise the previous query stays queued in the debouncer, and
    // a fast clear-then-retype shows that stale query's results again for up
    // to SEARCH_DEBOUNCE_MS before the new query settles.
    useEffect(() => {
        if (!hasQuery) debouncer.flush();
    }, [hasQuery, debouncer]);

    return (
        <EventsListLayout
            controls={<EventSearchInput query={query} onQueryChange={onQueryChange} />}
        >
            <ErrorBoundaryWrapper componentName="EventSearchResults">
                <EventSearchResultsContent
                    elements={throttledElements}
                    query={effectiveQuery}
                    hasQuery={hasDebouncedQuery}
                />
            </ErrorBoundaryWrapper>
        </EventsListLayout>
    );
}

interface EventSearchResultsContentProps {
    elements: readonly Element[];
    query: string;
    hasQuery: boolean;
}

/**
 * Matching (`searchElements`) runs here, inside the `ErrorBoundaryWrapper` in
 * the parent, rather than in `EventsListSearchContent`'s own render. Element
 * accessors (`getDisplayName`, `getCode`, `getModifiers`) can throw on
 * malformed WebSocket data, and a throw during the parent's render would
 * happen above the boundary — taking out the search input along with it.
 */
function EventSearchResultsContent({ elements, query, hasQuery }: EventSearchResultsContentProps) {
    const results = useMemo(() => searchElements(elements, query), [elements, query]);
    return <EventSearchResults results={results} hasQuery={hasQuery} query={query} />;
}
