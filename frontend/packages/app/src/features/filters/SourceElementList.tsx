// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex } from "@chakra-ui/react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import * as turf from "@turf/turf";
import { SearchInput, TacticalText } from "@opencop/ui";
import { localEventSource, type Element } from "@opencop/elements";
import { useQuery } from "@tanstack/react-query";
import { eventQueries } from "~/features/events/events.queries";
import { useMapManager } from "~/features/map/MapManagerContext";
import { useElementsBySource, useSourceCount } from "@opencop/ingest";
import { useViewActions } from "../map/core/slices/view/view.hook";

const PAGE_SIZE = 10;

/**
 * The author's username (the last user to publish the object), or "System" when the object came
 * from a data source.
 *
 * The live socket does not carry the author, so it is read from the detail endpoint per visible
 * row. Local drafts have no server record yet, so they show nothing.
 */
function ElementAuthor({ element }: { element: Element }) {
    const { t } = useTranslation();
    const isLocal = element.source === localEventSource;
    // Every detail read also records the layer's "Last Event" time, so expanding a source advances
    // it. Carrying the author on the live element would avoid the read.
    const { data } = useQuery({
        ...eventQueries.detail(element.source, element.objectId),
        enabled: !isLocal,
        refetchInterval: false,
        staleTime: 60_000,
        refetchOnWindowFocus: false,
    });

    if (isLocal || !data) return null;

    const { author } = data;
    const label = author
        ? author.username || t("filters.authorUnknown")
        : t("filters.authorSystem");

    return (
        <TacticalText
            fontSize="xs"
            color="fg.muted"
            flexShrink={0}
            maxW="40%"
            overflow="hidden"
            textOverflow="ellipsis"
            whiteSpace="nowrap"
        >
            {label}
        </TacticalText>
    );
}

interface SourceElementListProps {
    sourceId: string;
}

export function SourceElementList({ sourceId }: SourceElementListProps) {
    const { t } = useTranslation();
    const mapManager = useMapManager();
    const [displayLimit, setDisplayLimit] = useState(PAGE_SIZE);
    const [searchTerm, setSearchTerm] = useState("");

    const sourceElements = useElementsBySource(sourceId);
    const sourceCount = useSourceCount(sourceId);
    const { fitBounds } = useViewActions();

    const filteredElements = useMemo(() => {
        const results: Element[] = [];
        const term = searchTerm.toLowerCase();
        const iter = sourceElements?.();
        if (!iter) return results;
        for (const element of iter) {
            if (term && !element.getDisplayName().toLowerCase().includes(term)) continue;
            results.push(element);
            // Stop collecting once we have enough for the current page + 1
            // (the +1 tells us whether "see more" should show)
            if (!term && results.length > displayLimit) break;
        }
        return results;
    }, [sourceElements, searchTerm, displayLimit]);

    const visibleElements = filteredElements.slice(0, displayLimit);
    const hasMore = filteredElements.length > displayLimit;

    function handleEventClick(element: Element) {
        mapManager.selectElement(element);
        try {
            const geojson = element.geometry.toGeoJSONGeometry();
            const [minLng, minLat, maxLng, maxLat] = turf.bbox(geojson);
            fitBounds(
                [
                    [minLng, minLat],
                    [maxLng, maxLat],
                ],
                { padding: 100, maxZoom: 15 },
            );
        } catch {
            // Single-point fallback — both corners are the same point
            const positions = element.geometry.getAllPos();
            if (positions.length >= 2) {
                fitBounds(
                    [
                        [positions[0], positions[1]],
                        [positions[0], positions[1]],
                    ],
                    { padding: 100, maxZoom: 15 },
                );
            }
        }
    }

    return (
        <Box bg="bg.surface" mx={3} mb={3}>
            <Flex
                px={3}
                py={2}
                align="center"
                justify="space-between"
                gap={3}
                borderBottomWidth="1px"
                borderBottomColor="border.muted"
            >
                <TacticalText
                    fontSize="xs"
                    fontWeight="600"
                    color="fg.muted"
                    textTransform="uppercase"
                    letterSpacing="wider"
                    flexShrink={0}
                >
                    {t("filters.objects")}
                </TacticalText>
                <SearchInput
                    value={searchTerm}
                    onChange={setSearchTerm}
                    size="sm"
                    placeholder={t("common.search")}
                />
            </Flex>
            {visibleElements.length === 0 ? (
                <Flex px={3} py={2} justify="center">
                    <TacticalText fontSize="xs" color="fg.muted" fontStyle="italic">
                        {t("common.noResults")}
                    </TacticalText>
                </Flex>
            ) : (
                <>
                    {visibleElements.map((element, index) => (
                        <Flex
                            key={element.key}
                            px={3}
                            py={1.5}
                            align="center"
                            gap={3}
                            cursor="pointer"
                            bg={index % 2 === 0 ? "bg.surfaceAlt" : undefined}
                            transition="background-color 0.15s ease-out"
                            _hover={{ bg: "bg.subtle" }}
                            onClick={(event) => {
                                event.stopPropagation();
                                handleEventClick(element);
                            }}
                        >
                            <TacticalText
                                fontSize="xs"
                                flex={1}
                                overflow="hidden"
                                textOverflow="ellipsis"
                                whiteSpace="nowrap"
                                color="fg"
                            >
                                {element.getDisplayName()}
                            </TacticalText>
                            <ElementAuthor element={element} />
                        </Flex>
                    ))}
                    {hasMore && (
                        <Flex
                            px={3}
                            py={1.5}
                            align="center"
                            cursor="pointer"
                            bg={visibleElements.length % 2 === 0 ? "bg.surfaceAlt" : undefined}
                            transition="background-color 0.15s ease-out"
                            _hover={{ bg: "bg.subtle" }}
                            onClick={(event) => {
                                event.stopPropagation();
                                setDisplayLimit((prev) => prev + PAGE_SIZE);
                            }}
                        >
                            <TacticalText fontSize="xs" color="fg.muted" fontStyle="italic">
                                {t("filters.seeMoreCount", {
                                    number: sourceCount - displayLimit,
                                })}
                            </TacticalText>
                        </Flex>
                    )}
                </>
            )}
        </Box>
    );
}
