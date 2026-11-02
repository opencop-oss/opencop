// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Accordion, Alert, Flex, HStack, Icon, IconButton, Text } from "@chakra-ui/react";
import type { MappedRecord, MappingError, MappingResponse, MappingWarning } from "@opencop/api";
import { JsonTreeView, Tooltip } from "@opencop/ui";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
    LuChevronLeft,
    LuChevronRight,
    LuCircleAlert,
    LuList,
    LuTriangleAlert,
} from "react-icons/lu";
import { useMapperBuilderActions } from "../mapper-builder.store";
import { enrichErrors, enrichWarnings } from "../mapper-builder.util";
import { IssueList } from "./IssueList";
import { groupIssues } from "./issue-list.util";

interface LivePreviewProps {
    response: MappingResponse | null;
    hasFile: boolean;
    error?: unknown;
    /** Client-side validation warnings (e.g., OBJECT_ID not mapped / non-unique) */
    clientWarnings?: MappingWarning[];
}

export function LivePreview({ response, hasFile, error, clientWarnings }: LivePreviewProps) {
    const { t } = useTranslation();

    // Track response identity to reset child state (pagination, accordion) on new responses.
    // Using a key forces PreviewAccordion to remount with fresh useState defaults.
    const [responseKey, setResponseKey] = useState(0);
    const [prevResponse, setPrevResponse] = useState(response);
    if (prevResponse !== response) {
        setPrevResponse(response);
        setResponseKey((k) => k + 1);
    }

    if (!hasFile) return null;

    if (error) {
        const errorDetail =
            error instanceof Error ? error.message : t("mapperBuilder.previewRequestFailed");
        return (
            <Flex direction="column" gap="2" px="3" py="2">
                <Alert.Root status="error" size="sm">
                    <Alert.Indicator />
                    <Alert.Content>
                        <Alert.Title>{t("mapperBuilder.previewFailed")}</Alert.Title>
                        <Alert.Description fontSize="xs">{errorDetail}</Alert.Description>
                    </Alert.Content>
                </Alert.Root>
            </Flex>
        );
    }

    return (
        <PreviewAccordion key={responseKey} response={response} clientWarnings={clientWarnings} />
    );
}

/** A record entry in the unified list — either a successful record or an error placeholder */
type RecordEntry =
    | { type: "success"; index: number; record: MappedRecord }
    | { type: "error"; index: number; errors: MappingError[] };

/** Build a unified record list that includes dropped (failed) records as error placeholders */
function buildAllRecords(response: MappingResponse, enrichedErrors: MappingError[]): RecordEntry[] {
    const successIndices = new Set(response.records.map((r) => r.index));

    // Collect per-record errors for indices not in the success list
    const droppedByIndex = new Map<number, MappingError[]>();
    for (const err of enrichedErrors) {
        if (
            err.recordIndex !== undefined &&
            err.recordIndex >= 0 &&
            !successIndices.has(err.recordIndex)
        ) {
            const existing = droppedByIndex.get(err.recordIndex);
            if (existing) {
                existing.push(err);
            } else {
                droppedByIndex.set(err.recordIndex, [err]);
            }
        }
    }

    const entries: RecordEntry[] = [];

    for (const rec of response.records) {
        entries.push({ type: "success", index: rec.index, record: rec });
    }

    for (const [index, errors] of droppedByIndex) {
        entries.push({ type: "error", index, errors });
    }

    entries.sort((a, b) => a.index - b.index);
    return entries;
}

function PreviewAccordion({
    response,
    clientWarnings,
}: {
    response: MappingResponse | null;
    clientWarnings?: MappingWarning[];
}) {
    const { t } = useTranslation();
    const { focusSection, focusRule } = useMapperBuilderActions();

    // Enrich errors to infer sectionKey for element-phase errors
    const enrichedErrors = useMemo(
        () => (response ? enrichErrors(response.errors) : []),
        [response],
    );

    // Enrich warnings — aggregate top-level, per-record, and client-side warnings
    const enrichedWarnings = useMemo(() => {
        const serverWarnings = response
            ? [...response.warnings, ...response.records.flatMap((r) => r.warnings)]
            : [];
        return enrichWarnings([...serverWarnings, ...(clientWarnings ?? [])]);
    }, [response, clientWarnings]);

    const errorCount = enrichedErrors.length;
    const warningCount = enrichedWarnings.length;

    const groupedErrors = useMemo(() => groupIssues(enrichedErrors), [enrichedErrors]);
    const groupedWarnings = useMemo(() => groupIssues(enrichedWarnings), [enrichedWarnings]);

    // Build unified record list with dropped record placeholders
    const allRecords = useMemo(
        () => (response ? buildAllRecords(response, enrichedErrors) : []),
        [response, enrichedErrors],
    );
    const totalRecords = allRecords.length;

    // Record pagination state — starts at 0, resets on remount (response change)
    const [currentPage, setCurrentPage] = useState(0);

    // Controlled accordion state — auto-expand sections with content on mount.
    // Component remounts (via key) when response changes, so initial value captures fresh state.
    const [expandedSections, setExpandedSections] = useState<string[]>(() => {
        if (!response) return [];
        const expanded: string[] = [];
        if (errorCount > 0) expanded.push("errors");
        if (warningCount > 0) expanded.push("warnings");
        if (totalRecords > 0) expanded.push("output");
        return expanded;
    });

    const currentEntry = totalRecords > 0 ? allRecords[currentPage] : undefined;

    const handleFocusSection = focusSection;
    const handleFocusRule = focusRule;

    return (
        <Accordion.Root
            multiple
            value={expandedSections}
            onValueChange={({ value }) => setExpandedSections(value)}
            h="full"
            display="flex"
            flexDirection="column"
            overflow="hidden"
        >
            {/* Errors */}
            <Accordion.Item
                value="errors"
                maxH="40%"
                display="flex"
                flexDirection="column"
                minH="0"
            >
                <Accordion.ItemTrigger px="3" py="1.5" cursor="pointer">
                    <Flex align="center" gap="2" flex="1">
                        <Icon color={errorCount > 0 ? "fg.error" : "fg.subtle"} boxSize="4">
                            <LuCircleAlert />
                        </Icon>
                        <Text fontSize="xs" fontWeight="medium" color="fg.subtle">
                            {t("mapperBuilder.tabErrors")}
                        </Text>
                    </Flex>
                    <Accordion.ItemIndicator />
                </Accordion.ItemTrigger>
                <Accordion.ItemContent
                    flex="1"
                    overflow="hidden"
                    display="flex"
                    flexDirection="column"
                    minH="0"
                >
                    <Accordion.ItemBody
                        px="3"
                        pb="2"
                        pt="0"
                        overflow="auto"
                        flex="1"
                        minH="0"
                        overscrollBehavior="contain"
                    >
                        <IssueList
                            items={groupedErrors}
                            codeColor="fg.error"
                            emptyMessage={t("mapperBuilder.noErrors")}
                            onSectionClick={handleFocusSection}
                            onRuleClick={handleFocusRule}
                        />
                    </Accordion.ItemBody>
                </Accordion.ItemContent>
            </Accordion.Item>

            {/* Warnings */}
            <Accordion.Item
                value="warnings"
                maxH="40%"
                display="flex"
                flexDirection="column"
                minH="0"
            >
                <Accordion.ItemTrigger px="3" py="1.5" cursor="pointer">
                    <Flex align="center" gap="2" flex="1">
                        <Icon color={warningCount > 0 ? "fg.warning" : "fg.subtle"} boxSize="4">
                            <LuTriangleAlert />
                        </Icon>
                        <Text fontSize="xs" fontWeight="medium" color="fg.subtle">
                            {t("mapperBuilder.tabWarnings")}
                        </Text>
                    </Flex>
                    <Accordion.ItemIndicator />
                </Accordion.ItemTrigger>
                <Accordion.ItemContent
                    flex="1"
                    overflow="hidden"
                    display="flex"
                    flexDirection="column"
                    minH="0"
                >
                    <Accordion.ItemBody
                        px="3"
                        pb="2"
                        pt="0"
                        overflow="auto"
                        flex="1"
                        minH="0"
                        overscrollBehavior="contain"
                    >
                        <IssueList
                            items={groupedWarnings}
                            codeColor="fg.warning"
                            emptyMessage={t("mapperBuilder.noWarnings")}
                            onSectionClick={handleFocusSection}
                            onRuleClick={handleFocusRule}
                        />
                    </Accordion.ItemBody>
                </Accordion.ItemContent>
            </Accordion.Item>

            {/* Output */}
            <Accordion.Item
                value="output"
                flex="1"
                display="flex"
                flexDirection="column"
                minH="0"
                borderBottomWidth="0"
            >
                <Accordion.ItemTrigger px="3" py="1.5" cursor="pointer">
                    <Flex align="center" gap="2" flex="1">
                        <Icon color="fg.subtle" boxSize="4">
                            <LuList />
                        </Icon>
                        <Text fontSize="xs" fontWeight="medium" color="fg.subtle">
                            {t("mapperBuilder.tabRecords")}
                        </Text>
                    </Flex>
                    <Accordion.ItemIndicator />
                </Accordion.ItemTrigger>
                <Accordion.ItemContent
                    flex="1"
                    overflow="hidden"
                    display="flex"
                    flexDirection="column"
                    minH="0"
                >
                    <Accordion.ItemBody
                        px="0"
                        pb="0"
                        pt="0"
                        overflow="auto"
                        flex="1"
                        minH="0"
                        display="flex"
                        flexDirection="column"
                        overscrollBehavior="contain"
                    >
                        {totalRecords === 0 ? (
                            <Text fontSize="xs" color="fg.subtle" px="3">
                                {t("mapperBuilder.previewEmpty")}
                            </Text>
                        ) : (
                            <>
                                {/* Record header with pagination */}
                                <HStack mb="1" flexShrink={0} px="3">
                                    <Text fontSize="xs" fontWeight="medium">
                                        {t("mapperBuilder.recordLabel", {
                                            index: (currentEntry?.index ?? 0) + 1,
                                        })}
                                    </Text>
                                    <HStack gap="0.5" ms="auto">
                                        <Tooltip content={t("mapperBuilder.previousRecord")}>
                                            <IconButton
                                                aria-label={t("mapperBuilder.previousRecord")}
                                                size="2xs"
                                                variant="ghost"
                                                disabled={currentPage === 0}
                                                onClick={() => setCurrentPage((p) => p - 1)}
                                            >
                                                <LuChevronLeft />
                                            </IconButton>
                                        </Tooltip>
                                        <Text fontSize="xs" color="fg.subtle">
                                            {t("mapperBuilder.recordXOfY", {
                                                current: currentPage + 1,
                                                total: totalRecords,
                                            })}
                                        </Text>
                                        <Tooltip content={t("mapperBuilder.nextRecord")}>
                                            <IconButton
                                                aria-label={t("mapperBuilder.nextRecord")}
                                                size="2xs"
                                                variant="ghost"
                                                disabled={currentPage >= totalRecords - 1}
                                                onClick={() => setCurrentPage((p) => p + 1)}
                                            >
                                                <LuChevronRight />
                                            </IconButton>
                                        </Tooltip>
                                    </HStack>
                                </HStack>

                                {/* Current record display */}
                                {currentEntry?.type === "success" && (
                                    <JsonTreeView data={currentEntry.record.output} />
                                )}

                                {/* Dropped record placeholder */}
                                {currentEntry?.type === "error" && (
                                    <Text fontSize="xs" color="fg.subtle" px="3">
                                        {t("mapperBuilder.droppedRecord")}
                                    </Text>
                                )}
                            </>
                        )}
                    </Accordion.ItemBody>
                </Accordion.ItemContent>
            </Accordion.Item>
        </Accordion.Root>
    );
}
