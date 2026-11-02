// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Alert, Box, Code, Flex, Icon, IconButton, Text } from "@chakra-ui/react";
import { Tooltip } from "@opencop/ui";
import { type DragEvent, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { LuArrowDownToLine, LuArrowRightLeft, LuTriangleAlert, LuX } from "react-icons/lu";
import { useMapperBuilderActions, useMapperBuilderStore } from "./mapper-builder.store";
import type { TreeNode } from "./mapper-builder.parsers";
import { buildSourceRefFromNode } from "./mapper-builder.util";
import { TREE_NODE_DRAG_TYPE } from "./SourceViewer";
import { UploadPane } from "./SourcePanel";
import { RuleBuilder } from "./workspace/RuleBuilder";

export function CenterPanel({ saveError }: { saveError: boolean }) {
    const { t } = useTranslation();
    const recordSelector = useMapperBuilderStore((s) => s.recordSelector);
    const rules = useMapperBuilderStore((s) => s.rules);
    const matchedRecordCount = useMapperBuilderStore((s) => s.matchedRecordCount);
    const recordSelectorValid = useMapperBuilderStore((s) => s.recordSelectorValid);
    const sampleFile = useMapperBuilderStore((s) => s.sampleFile);
    const { removeRecordSelector } = useMapperBuilderActions();

    // Show the rule builder when a record selector exists OR rules have been created
    const showRuleBuilder = recordSelector !== null || rules.length > 0;

    const selectorExpr = useMemo(() => {
        if (!recordSelector?.source) return null;
        const src = recordSelector.source;
        if (src.type === "xpath" || src.type === "jsonpath") return src.expr;
        if (src.type === "csvColumn") return src.column ?? t("mapperBuilder.csvRowFallback");
        return null;
    }, [recordSelector, t]);

    // Before file upload: show the upload dropzone (only if no rules or record selector exist yet).
    // When rules or a record selector exist, the SourcePanel shows the upload pane instead.
    if (!sampleFile && rules.length === 0 && recordSelector === null) {
        return (
            <Flex align="center" justify="center" h="full" px="4">
                <Box maxW="400px" w="full">
                    <UploadPane />
                </Box>
            </Flex>
        );
    }

    return (
        <Flex direction="column" h="full" px="3" pb="4">
            {saveError && (
                <Alert.Root status="error" size="sm" mb="2">
                    <Alert.Indicator />
                    <Alert.Content>
                        <Alert.Title>{t("mapperBuilder.saveError")}</Alert.Title>
                    </Alert.Content>
                </Alert.Root>
            )}
            {/* Record selector: drop zone (before set) or card (after set) */}
            {recordSelector ? (
                <Flex direction="column" gap="1" mb="1" flexShrink={0}>
                    <Flex align="center" gap="2" py="2">
                        <Text fontSize="xs" color="fg.subtle">
                            {t("mapperBuilder.recordSelector")}:
                        </Text>
                        <Code fontSize="xs" variant="plain" color="fg.muted">
                            {selectorExpr || "/"}
                        </Code>
                        <Text fontSize="xs" color="fg.subtle">
                            ({t("mapperBuilder.summaryRecords", { count: matchedRecordCount })})
                        </Text>
                        <Box flex="1" />
                        <Tooltip content={t("mapperBuilder.removeRecordSelector")}>
                            <IconButton
                                aria-label={t("mapperBuilder.removeRecordSelector")}
                                size="2xs"
                                variant="ghost"
                                onClick={removeRecordSelector}
                            >
                                <LuX />
                            </IconButton>
                        </Tooltip>
                    </Flex>
                    {!recordSelectorValid && (
                        <Flex align="center" gap="1.5" px="1" pb="1">
                            <Icon color="fg.error" boxSize="3">
                                <LuTriangleAlert />
                            </Icon>
                            <Text fontSize="xs" color="fg.error">
                                {t("mapperBuilder.validation.recordBoundaryNotFound" as never)}
                            </Text>
                        </Flex>
                    )}
                </Flex>
            ) : (
                <RecordSelectorDropZone />
            )}

            {/* Main content area */}
            <Box flex="1" overflow="auto">
                {!showRuleBuilder ? <GuidePlaceholder /> : <RuleBuilder />}
            </Box>
        </Flex>
    );
}

/** Drop zone rendered in the same position as the record selector card */
function RecordSelectorDropZone() {
    const { t } = useTranslation();
    const sampleFile = useMapperBuilderStore((s) => s.sampleFile);
    const formatConfig = useMapperBuilderStore((s) => s.formatConfig);
    const sampleContent = useMapperBuilderStore((s) => s.sampleContent);
    const parsedTreeLength = useMapperBuilderStore((s) => s.parsedTree.length);
    const rules = useMapperBuilderStore((s) => s.rules);
    const { selectRecord } = useMapperBuilderActions();

    const [isDragOver, setIsDragOver] = useState(false);

    const hasError = rules.length > 0;
    const canDrop = sampleFile !== null && formatConfig !== null;

    // Only show when there's a file ready for drops, or when rules exist (to show error state)
    if (!canDrop && !hasError) return null;

    const handleDragOver = (e: DragEvent) => {
        if (!canDrop) return;
        if (e.dataTransfer.types.includes(TREE_NODE_DRAG_TYPE)) {
            e.preventDefault();
            e.dataTransfer.dropEffect = "copy";
            setIsDragOver(true);
        }
    };

    const handleDragLeave = () => {
        setIsDragOver(false);
    };

    const handleDrop = (e: DragEvent) => {
        e.preventDefault();
        setIsDragOver(false);

        const raw = e.dataTransfer.getData(TREE_NODE_DRAG_TYPE);
        if (!raw || !formatConfig) return;

        let nodeData: { id: string; name: string; nodeType: string };
        try {
            nodeData = JSON.parse(raw) as { id: string; name: string; nodeType: string };
        } catch {
            return;
        }
        const result = buildSourceRefFromNode(
            nodeData as TreeNode,
            formatConfig,
            sampleContent,
            parsedTreeLength,
        );
        if (!result) return;

        selectRecord(
            { source: result.source, onMissing: "NULL" },
            nodeData.id,
            result.matchedCount,
        );
    };

    return (
        <Flex direction="column" gap="1" flexShrink={0}>
            <Tooltip content={t("mapperBuilder.dropRecordDescription")} uppercase={false}>
                <Flex
                    align="center"
                    px="3"
                    py="2"
                    mt="2"
                    mb={hasError ? "0" : "1"}
                    borderWidth={hasError && !isDragOver ? "1px" : "2px"}
                    borderStyle={isDragOver ? "solid" : "dashed"}
                    borderColor={
                        isDragOver ? "colorPalette.solid" : hasError ? "border.error" : undefined
                    }
                    borderRadius="l3"
                    bg={isDragOver ? "colorPalette.subtle" : "bg"}
                    transition="backgrounds"
                    _hover={{ bg: "bg.subtle" }}
                    onDragOver={handleDragOver}
                    onDragLeave={handleDragLeave}
                    onDrop={handleDrop}
                >
                    <Text fontSize="sm" color="fg.muted" whiteSpace="nowrap">
                        {t("mapperBuilder.dropRecordHint")}
                    </Text>
                    <Flex align="center" gap="1" ms="auto" color="fg.subtle">
                        <Icon asChild boxSize="3">
                            <LuArrowDownToLine />
                        </Icon>
                        <Text fontSize="xs" whiteSpace="nowrap">
                            {t("mapperBuilder.dropFieldHint")}
                        </Text>
                    </Flex>
                </Flex>
            </Tooltip>
            {hasError && (
                <Flex align="center" gap="1.5" px="1" pb="1">
                    <Icon color="fg.error" boxSize="3">
                        <LuTriangleAlert />
                    </Icon>
                    <Text fontSize="xs" color="fg.error">
                        {t("mapperBuilder.validation.recordBoundaryRequired" as never)}
                    </Text>
                </Flex>
            )}
        </Flex>
    );
}

function GuidePlaceholder() {
    const { t } = useTranslation();

    return (
        <Flex
            direction="column"
            align="center"
            justify="center"
            h="full"
            textAlign="center"
            gap="3"
            px="4"
        >
            <Icon color="fg.subtle" boxSize="8">
                <LuArrowRightLeft />
            </Icon>
            <Text fontSize="sm" color="fg.subtle">
                {t("mapperBuilder.guideTitle")}
            </Text>
            <Text fontSize="xs" color="fg.subtle" maxW="400px">
                {t("mapperBuilder.guideRecordHint")}
            </Text>
        </Flex>
    );
}
