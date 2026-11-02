// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Box,
    Checkbox,
    Code,
    Field as ChakraField,
    FileUpload,
    Flex,
    Icon,
    IconButton,
    NativeSelectField,
    NativeSelectRoot,
    Popover,
    Text,
} from "@chakra-ui/react";
import type { FormatConfig } from "@opencop/api";
import { OcSwitch, SettingsPopover, Tooltip } from "@opencop/ui";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { LuFileWarning, LuSettings2, LuTriangleAlert, LuUpload, LuX } from "react-icons/lu";
import { SourceViewer } from "./SourceViewer";
import { XmlNamespaceEditor } from "./XmlNamespaceEditor";
import type { TreeNode } from "./mapper-builder.parsers";
import { parseCsvToTree, parseJsonToTree, parseXmlToTree } from "./mapper-builder.parsers";
import { useMapperBuilderActions, useMapperBuilderStore } from "./mapper-builder.store";
import {
    buildDefaultFormatConfig,
    buildSourceRefFromNode,
    detectFormat,
    findNodeForRecordSelector,
    readFileAsText,
} from "./mapper-builder.util";
import {
    validateRecordSelector as checkRecordSelector,
    validateRules,
} from "./mapper-builder.validation";

const ACCEPTED_TYPES = {
    "application/xml": [".xml"],
    "application/json": [".json"],
    "text/csv": [".csv"],
    "application/vnd.google-earth.kml+xml": [".kml"],
    "application/vnd.google-earth.kmz": [".kmz"],
};
const MAX_FILE_SIZE = 1024 * 1024; // 1 MB

export function SourcePanel() {
    const sampleFile = useMapperBuilderStore((s) => s.sampleFile);
    const rules = useMapperBuilderStore((s) => s.rules);
    const recordSelector = useMapperBuilderStore((s) => s.recordSelector);

    if (!sampleFile) {
        // Only show the upload pane here when rules/record boundary exist (file was removed).
        // On the initial empty screen, the CenterPanel shows the upload pane instead.
        if (rules.length > 0 || recordSelector !== null) {
            return (
                <Flex align="center" justify="center" h="full" px="4">
                    <Box maxW="400px" w="full">
                        <UploadPane />
                    </Box>
                </Flex>
            );
        }
        return null;
    }

    return <ConfiguredSourcePane />;
}

// --- Upload pane (initial state) ----------------------------------------

export function UploadPane() {
    const { t } = useTranslation();
    const { uploadSample } = useMapperBuilderActions();
    const [fileError, setFileError] = useState<string | null>(null);

    async function handleFileAccept(details: { files: File[] }) {
        setFileError(null);
        const file = details.files[0];
        if (!file) return;
        const format = detectFormat(file);
        if (!format) return;
        const content = await readFileAsText(file);
        uploadSample(file, content, format);
    }

    function handleFileReject(details: { files: { file: File; errors: string[] }[] }) {
        const rejected = details.files[0];
        if (!rejected) return;
        const isTooLarge = rejected.errors.some((e) => e === "FILE_TOO_LARGE");
        setFileError(
            isTooLarge ? t("mapperBuilder.fileTooLarge") : t("mapperBuilder.fileRejected"),
        );
    }

    return (
        <ChakraField.Root invalid={!!fileError}>
            <FileUpload.Root
                maxFiles={1}
                accept={ACCEPTED_TYPES}
                maxFileSize={MAX_FILE_SIZE}
                invalid={!!fileError}
                onFileAccept={handleFileAccept}
                onFileReject={handleFileReject}
            >
                <FileUpload.HiddenInput />
                <FileUpload.Dropzone w="full" py="10" _invalid={{ borderColor: "border.error" }}>
                    <Flex direction="column" align="center" gap="3" textAlign="center" px="2">
                        <LuUpload size={28} />
                        <FileUpload.DropzoneContent>
                            <Text fontSize="sm" fontWeight="medium">
                                {t("mapperBuilder.uploadPrompt")}
                            </Text>
                            <Text fontSize="xs" color="fg.subtle" mt="1">
                                {t("mapperBuilder.uploadHint")}
                            </Text>
                        </FileUpload.DropzoneContent>
                    </Flex>
                </FileUpload.Dropzone>
            </FileUpload.Root>
            <ChakraField.ErrorText>{fileError}</ChakraField.ErrorText>
        </ChakraField.Root>
    );
}

// --- Configured source pane (after upload) ------------------------------

function ConfiguredSourcePane() {
    const { t } = useTranslation();
    const detectedFormat = useMapperBuilderStore((s) => s.detectedFormat);
    const formatConfig = useMapperBuilderStore((s) => s.formatConfig);
    const sampleContent = useMapperBuilderStore((s) => s.sampleContent);
    const sampleFile = useMapperBuilderStore((s) => s.sampleFile);
    const parseError = useMapperBuilderStore((s) => s.parseError);
    const parsedTree = useMapperBuilderStore((s) => s.parsedTree);
    const recordNodeId = useMapperBuilderStore((s) => s.recordNodeId);
    const rules = useMapperBuilderStore((s) => s.rules);
    const recordSelector = useMapperBuilderStore((s) => s.recordSelector);

    const {
        configureFormat,
        parseComplete,
        parseFailed,
        validateRecordSelector,
        reportRuleErrors,
        removeSample,
        restoreRecord,
    } = useMapperBuilderActions();

    const showSettings = formatConfig !== null;

    // Auto-apply default format config on first render after upload
    useEffect(() => {
        if (detectedFormat && !formatConfig) {
            configureFormat(buildDefaultFormatConfig(detectedFormat, sampleContent));
        }
    }, [detectedFormat, formatConfig, sampleContent, configureFormat]);

    // Parse tree when format config is set
    useEffect(() => {
        if (!sampleContent || !formatConfig) return;
        try {
            let tree: TreeNode[];
            switch (formatConfig.type) {
                case "xml":
                case "kml+kmz":
                    tree = parseXmlToTree(sampleContent);
                    break;
                case "json":
                    tree = parseJsonToTree(sampleContent);
                    break;
                case "csv":
                    tree = parseCsvToTree(sampleContent, formatConfig);
                    break;
                default:
                    return;
            }
            parseComplete(tree);
        } catch (err) {
            const message = err instanceof Error ? err.message : String(err);
            parseFailed(message);
        }
    }, [sampleContent, formatConfig, parseComplete, parseFailed]);

    // Revalidate record selector and rules after tree re-parse
    useEffect(() => {
        if (parsedTree.length === 0 || !recordNodeId || !formatConfig) {
            // No tree or no record boundary — clear validation errors
            reportRuleErrors({});
            return;
        }

        // Validate record selector
        const selectorValid = checkRecordSelector(recordNodeId, parsedTree);

        if (!selectorValid) {
            validateRecordSelector(false);
        } else if (recordSelector) {
            // Recompute matched record count
            const result = buildSourceRefFromNode(
                { id: recordNodeId, name: "", nodeType: "element" },
                formatConfig,
                sampleContent,
                parsedTree.length,
            );
            validateRecordSelector(true, result?.matchedCount ?? 0);
        }

        // Validate rules
        const errors = validateRules(rules, parsedTree, recordNodeId);
        reportRuleErrors(errors);
    }, [
        parsedTree,
        recordNodeId,
        formatConfig,
        rules,
        sampleContent,
        recordSelector,
        validateRecordSelector,
        reportRuleErrors,
    ]);

    // Reconstruct recordNodeId from recordSelector.source.expr after tree is parsed (edit mode)
    useEffect(() => {
        if (!recordSelector || recordNodeId || parsedTree.length === 0 || !formatConfig) return;

        const node = findNodeForRecordSelector(recordSelector, parsedTree);
        if (!node) return;

        const result = buildSourceRefFromNode(node, formatConfig, sampleContent, parsedTree.length);
        restoreRecord(recordSelector, node.id, result?.matchedCount ?? 0);
    }, [recordSelector, recordNodeId, parsedTree, formatConfig, sampleContent, restoreRecord]);

    return (
        <Flex direction="column" h="full">
            {/* File info bar */}
            <Flex
                direction="column"
                flexShrink={0}
                mx="2"
                mt="1"
                borderWidth="1px"
                borderStyle="dashed"
                borderColor={parseError ? "border.error" : "transparent"}
                borderRadius="l3"
                transition="borders"
            >
                <Flex align="center" gap="2" px="2" py="1.5">
                    <Text fontSize="xs" color="fg.subtle">
                        {t("mapperBuilder.file")}:
                    </Text>
                    <Code fontSize="xs" variant="plain" color="fg.muted" truncate>
                        {sampleFile?.name}
                    </Code>
                    <Box flex="1" />
                    {showSettings && (
                        <FormatSettingsButton
                            title={t(formatOptionsLabel(formatConfig?.type) as never)}
                            label={t("mapperBuilder.formatSettings")}
                        />
                    )}
                    <Tooltip content={t("mapperBuilder.changeFile")}>
                        <IconButton
                            aria-label={t("mapperBuilder.changeFile")}
                            size="2xs"
                            variant="ghost"
                            onClick={removeSample}
                        >
                            <LuX />
                        </IconButton>
                    </Tooltip>
                </Flex>
            </Flex>
            {parseError && (
                <Flex align="center" gap="1.5" px="3" pt="1">
                    <Icon color="fg.error" boxSize="3">
                        <LuTriangleAlert />
                    </Icon>
                    <Text fontSize="xs" color="fg.error">
                        {t("mapperBuilder.parseErrorTitle")}
                    </Text>
                </Flex>
            )}

            {/* Tree viewer */}
            <Box flex="1" overflow="hidden">
                {parseError ? (
                    <Flex h="full" align="center" justify="center" px="6">
                        <Flex
                            direction="column"
                            align="center"
                            gap="3"
                            maxW="320px"
                            textAlign="center"
                        >
                            <Box color="fg.error" fontSize="2xl">
                                <LuFileWarning />
                            </Box>
                            <Text fontSize="sm" fontWeight="medium" color="fg.muted">
                                {t("mapperBuilder.parseErrorTitle")}
                            </Text>
                            <Code
                                fontSize="xs"
                                color="fg.subtle"
                                variant="plain"
                                whiteSpace="pre-wrap"
                                wordBreak="break-word"
                            >
                                {parseError}
                            </Code>
                        </Flex>
                    </Flex>
                ) : parsedTree.length > 0 ? (
                    <SourceViewer tree={parsedTree} recordNodeId={recordNodeId} />
                ) : (
                    <Flex h="full" align="center" justify="center">
                        <Text fontSize="sm" color="fg.subtle">
                            {t("mapperBuilder.parsingFile")}
                        </Text>
                    </Flex>
                )}
            </Box>
        </Flex>
    );
}

function formatOptionsLabel(type?: string): string {
    switch (type) {
        case "csv":
            return "mapperBuilder.csvOptions";
        case "json":
            return "mapperBuilder.jsonOptions";
        case "xml":
            return "mapperBuilder.xmlOptions";
        case "kml+kmz":
            return "mapperBuilder.kmlOptions";
        default:
            return "mapperBuilder.formatSettings";
    }
}

// --- Format settings button (Tooltip + Popover on the same element) -----

function FormatSettingsButton({ title, label }: { title: string; label: string }) {
    return (
        <SettingsPopover title={title} content={<InlineFormatConfig />}>
            <Tooltip content={label}>
                <Box as="span" display="inline-flex">
                    <Popover.Trigger asChild>
                        <IconButton aria-label={label} size="2xs" variant="ghost">
                            <LuSettings2 />
                        </IconButton>
                    </Popover.Trigger>
                </Box>
            </Tooltip>
        </SettingsPopover>
    );
}

// --- Inline format config -----------------------------------------------

function InlineFormatConfig() {
    const { t } = useTranslation();
    const formatConfig = useMapperBuilderStore((s) => s.formatConfig);
    const { configureFormat } = useMapperBuilderActions();

    if (!formatConfig) return null;

    // Capture non-null config for use in nested function (closure doesn't narrow)
    const currentConfig = formatConfig;

    function update(patch: Record<string, unknown>) {
        configureFormat({ ...currentConfig, ...patch } as FormatConfig);
    }

    if (formatConfig.type === "csv") {
        return (
            <Flex direction="column" gap="3" minW="220px">
                <Flex align="center" justify="space-between" gap="4">
                    <Text fontSize="xs" color="fg.muted" whiteSpace="nowrap">
                        {t("mapperBuilder.delimiter")}
                    </Text>
                    <NativeSelectRoot size="xs" w="auto" minW="120px" variant="outline">
                        <NativeSelectField
                            value={formatConfig.delimiter}
                            onChange={(e) => update({ delimiter: e.target.value })}
                            cursor="pointer"
                        >
                            <option value=",">{t("mapperBuilder.delimiterComma")}</option>
                            <option value={"\t"}>{t("mapperBuilder.delimiterTab")}</option>
                            <option value=";">{t("mapperBuilder.delimiterSemicolon")}</option>
                            <option value="|">{t("mapperBuilder.delimiterPipe")}</option>
                            <option value=" ">{t("mapperBuilder.delimiterSpace")}</option>
                        </NativeSelectField>
                    </NativeSelectRoot>
                </Flex>
                <Flex align="center" justify="space-between" gap="4">
                    <Text fontSize="xs" color="fg.muted" whiteSpace="nowrap">
                        {t("mapperBuilder.quoteChar")}
                    </Text>
                    <NativeSelectRoot size="xs" w="auto" minW="120px" variant="outline">
                        <NativeSelectField
                            value={formatConfig.quote}
                            onChange={(e) => update({ quote: e.target.value })}
                            cursor="pointer"
                        >
                            <option value={'"'}>{t("mapperBuilder.quoteDouble")}</option>
                            <option value="'">{t("mapperBuilder.quoteSingle")}</option>
                        </NativeSelectField>
                    </NativeSelectRoot>
                </Flex>
                <OcSwitch
                    colorPalette="brand"
                    rootProps={{ size: "sm", gap: "4" }}
                    label={
                        <Text as="span" fontSize="xs" color="fg.muted" whiteSpace="nowrap">
                            {t("mapperBuilder.hasHeader")}
                        </Text>
                    }
                    checked={formatConfig.hasHeader}
                    onCheckedChange={(checked) => update({ hasHeader: checked })}
                />
            </Flex>
        );
    }

    if (formatConfig.type === "json") {
        return (
            <Checkbox.Root
                size="sm"
                checked={formatConfig.allowComments}
                onCheckedChange={(e) => update({ allowComments: !!e.checked })}
            >
                <Checkbox.HiddenInput />
                <Checkbox.Control>
                    <Checkbox.Indicator />
                </Checkbox.Control>
                <Checkbox.Label>
                    <Text fontSize="xs">{t("mapperBuilder.allowComments")}</Text>
                </Checkbox.Label>
            </Checkbox.Root>
        );
    }

    // XML / KML — strict mode toggle + namespace editor
    if (formatConfig.type === "xml" || formatConfig.type === "kml+kmz") {
        return (
            <XmlNamespaceEditor
                config={formatConfig}
                onStrictChange={(checked) => update({ strict: checked })}
                onNamespacesChange={(namespaces) => update({ namespaces })}
            />
        );
    }

    return null;
}
