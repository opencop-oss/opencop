// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Flex, Icon, Text } from "@chakra-ui/react";
import type { MappingRule, OnMissing, TargetField } from "@opencop/api";
import { OcSelect, Tooltip } from "@opencop/ui";
import { type DragEvent, useEffect, useMemo, useRef, useState } from "react";
import { LuArrowDownToLine } from "react-icons/lu";
import scrollIntoView from "scroll-into-view-if-needed";
import { useTranslation } from "react-i18next";
import { v4 as uuid } from "uuid";
import { useMapperBuilderActions, useMapperBuilderStore } from "../mapper-builder.store";
import type { TreeNode } from "../mapper-builder.parsers";
import { DEFAULT_TARGETS, ERROR_TARGETS, getValueCountsForExpr } from "../mapper-builder.automap";
import {
    GEOMETRY_COMBINED,
    GEOMETRY_DISCRETE,
    buildSourceRefFromNode,
    getExprFromSource,
} from "../mapper-builder.util";
import { TREE_NODE_DRAG_TYPE } from "../SourceViewer";
import { deriveAttributeKey, isScalarNode } from "../mapper-builder.attribute-key";
import { RuleCard } from "./RuleCard";

/** Group definition */
interface TargetGroup {
    key: string;
    labelKey: string;
    fields: TargetField[];
    required?: boolean;
}

const TARGET_GROUPS: TargetGroup[] = [
    {
        key: "geometry",
        labelKey: "mapperBuilder.group.geometry",
        fields: ["GEOMETRY_LAT", "GEOMETRY_LON", "GEOMETRY_POINT", "GEOMETRY"],
        required: true,
    },
    {
        key: "identity",
        labelKey: "mapperBuilder.group.identity",
        fields: ["DISPLAY_NAME", "OBJECT_ID", "SOURCE_ID"],
    },
    {
        key: "time",
        labelKey: "mapperBuilder.group.time",
        fields: ["UPDATED_AT", "STALE_AT", "START_AT"],
    },
    {
        key: "motion",
        labelKey: "mapperBuilder.group.motion",
        fields: ["ELEMENT_SPEED", "ELEMENT_COURSE", "ELEMENT_HEADING"],
    },
    {
        key: "symbol",
        labelKey: "mapperBuilder.group.symbol",
        fields: ["ELEMENT_SYMBOL_SIDC", "ELEMENT_POINT_COLOR"],
    },
    {
        key: "mediaIcon",
        labelKey: "mapperBuilder.group.mediaIcon",
        fields: ["ELEMENT_ICON_LOCATION"],
    },
    {
        key: "style",
        labelKey: "mapperBuilder.group.style",
        fields: [
            "STYLE_STROKE_STYLE",
            "STYLE_STROKE_COLOR",
            "STYLE_STROKE_WIDTH",
            "STYLE_FILL_COLOR",
            "STYLE_FILL_OPACITY",
            "RAW_STYLE_URL",
        ],
    },
    {
        key: "attributes",
        labelKey: "mapperBuilder.group.attributes",
        fields: ["ATTRIBUTES"],
    },
];

/** Determine onMissing for a target field */
function getOnMissing(target: TargetField): OnMissing {
    if (ERROR_TARGETS.has(target)) return "ERROR";
    if (DEFAULT_TARGETS.has(target)) return "DEFAULT";
    return "NULL";
}

interface TargetFieldGroupsProps {
    usedTargets: Set<TargetField>;
    onUpdate: (id: string, rule: MappingRule) => void;
    onRemove: (id: string) => void;
}

export function TargetFieldGroups({ usedTargets, onUpdate, onRemove }: TargetFieldGroupsProps) {
    const rules = useMapperBuilderStore((s) => s.rules);

    // Build a map of target → rule for quick lookup (used by all groups except ATTRIBUTES)
    const rulesByTarget = useMemo(() => {
        const map = new Map<TargetField, MappingRule>();
        for (const rule of rules) {
            if (rule.target !== "ATTRIBUTES") {
                map.set(rule.target, rule);
            }
        }
        return map;
    }, [rules]);

    return (
        <Flex direction="column" gap="2">
            {TARGET_GROUPS.map((group) =>
                group.key === "attributes" ? (
                    <AttributesFieldGroup
                        key={group.key}
                        group={group}
                        rules={rules}
                        onUpdate={onUpdate}
                        onRemove={onRemove}
                    />
                ) : (
                    <FieldGroup
                        key={group.key}
                        group={group}
                        rulesByTarget={rulesByTarget}
                        usedTargets={usedTargets}
                        onUpdate={onUpdate}
                        onRemove={onRemove}
                    />
                ),
            )}
        </Flex>
    );
}

// ---------------------------------------------------------------------------
// AttributesFieldGroup — multi-rule ATTRIBUTES group
// ---------------------------------------------------------------------------

interface AttributesFieldGroupProps {
    group: TargetGroup;
    rules: MappingRule[];
    onUpdate: (id: string, rule: MappingRule) => void;
    onRemove: (id: string) => void;
}

function AttributesFieldGroup({ group, rules, onUpdate, onRemove }: AttributesFieldGroupProps) {
    const { t } = useTranslation();
    const sectionRef = useRef<HTMLDivElement>(null);

    const formatConfig = useMapperBuilderStore((s) => s.formatConfig);
    const sampleContent = useMapperBuilderStore((s) => s.sampleContent);
    const parsedTree = useMapperBuilderStore((s) => s.parsedTree);
    const parsedTreeLength = parsedTree.length;
    const recordNodeId = useMapperBuilderStore((s) => s.recordNodeId);
    const focusedSectionKey = useMapperBuilderStore((s) => s.focusedSectionKey);
    const errorSectionKeys = useMapperBuilderStore((s) => s.errorSectionKeys);
    const warningSectionKeys = useMapperBuilderStore((s) => s.warningSectionKeys);

    const { addRule, clearFocusSection } = useMapperBuilderActions();

    const attributeRules = useMemo(() => rules.filter((r) => r.target === "ATTRIBUTES"), [rules]);

    const hasError = errorSectionKeys.includes(group.key);
    const hasWarning = !hasError && warningSectionKeys.includes(group.key);

    const isSectionFocused = focusedSectionKey === group.key;
    const [flashingSection, setFlashingSection] = useState(false);

    if (isSectionFocused && !flashingSection) {
        setFlashingSection(true);
    }

    useEffect(() => {
        if (!isSectionFocused) return;
        if (sectionRef.current) {
            scrollIntoView(sectionRef.current, {
                scrollMode: "if-needed",
                behavior: "smooth",
                block: "nearest",
            });
        }
        clearFocusSection();
    }, [isSectionFocused, clearFocusSection]);

    const parseDrop = (e: DragEvent) => {
        const raw = e.dataTransfer.getData(TREE_NODE_DRAG_TYPE);
        if (!raw || !formatConfig) return null;
        let nodeData: { id: string; name: string; nodeType: string };
        try {
            nodeData = JSON.parse(raw) as { id: string; name: string; nodeType: string };
        } catch {
            return null;
        }
        const result = buildSourceRefFromNode(
            nodeData as TreeNode,
            formatConfig,
            sampleContent,
            parsedTreeLength,
            recordNodeId,
        );
        if (!result) return null;
        return { nodeData, source: result.source };
    };

    const handleDropScalar = (e: DragEvent) => {
        e.preventDefault();
        const parsed = parseDrop(e);
        if (!parsed || !formatConfig) return;
        if (!isScalarNode(parsed.nodeData.nodeType, formatConfig.type)) return;
        addRule({
            id: uuid(),
            enabled: true,
            target: "ATTRIBUTES",
            attributeKey: deriveAttributeKey(parsed.nodeData, parsed.source),
            source: parsed.source,
            transforms: [],
            onMissing: getOnMissing("ATTRIBUTES"),
        });
    };

    const handleDropObject = (e: DragEvent) => {
        e.preventDefault();
        const parsed = parseDrop(e);
        if (!parsed) return;
        addRule({
            id: uuid(),
            enabled: true,
            target: "ATTRIBUTES",
            source: parsed.source,
            transforms: [],
            onMissing: "NULL",
        });
    };

    const handleAddDefaultScalar = () => {
        addRule({
            id: uuid(),
            enabled: true,
            target: "ATTRIBUTES",
            attributeKey: "",
            source: { type: "const", value: "" },
            transforms: [],
            onMissing: "DEFAULT",
        });
    };

    const attributeCount = attributeRules.length;

    return (
        <Box
            ref={sectionRef}
            borderWidth={hasError || hasWarning ? "1px" : undefined}
            borderStyle={hasError || hasWarning ? "dashed" : undefined}
            borderColor={hasError ? "border.error" : hasWarning ? "border.warning" : undefined}
            borderRadius="md"
            style={
                flashingSection
                    ? ({
                          "--flash-color": hasError
                              ? "var(--chakra-colors-border-error)"
                              : hasWarning
                                ? "var(--chakra-colors-border-warning)"
                                : "var(--chakra-colors-border)",
                          animation: "flash-border 0.6s steps(1)",
                      } as React.CSSProperties)
                    : undefined
            }
            onAnimationEnd={() => setFlashingSection(false)}
        >
            {/* Group header */}
            <Flex
                align="center"
                gap="2"
                px="3"
                py="2"
                bg="bg.subtle"
                borderRadius="md"
                borderWidth="1px"
            >
                <Text
                    fontSize="sm"
                    fontWeight="medium"
                    flex="1"
                    color={hasError ? "fg.error" : hasWarning ? "fg.warning" : undefined}
                >
                    {t(group.labelKey as never)}
                </Text>
                <Text fontSize="xs" color="fg.subtle">
                    {t("mapperBuilder.attributeCount", { count: attributeCount })}
                </Text>
            </Flex>

            {/* Existing attribute rules */}
            {attributeCount > 0 && (
                <Flex direction="column" gap="0" pt="2">
                    {attributeRules.map((rule) => (
                        <RuleCard
                            key={rule.id}
                            rule={rule}
                            onUpdate={onUpdate}
                            onRemove={onRemove}
                        />
                    ))}
                </Flex>
            )}

            {/* Two persistent drop zones */}
            <Flex direction="column" gap="2" pt="2">
                <AttrDropZone
                    label={t("mapperBuilder.attributeSingleLabel" as never)}
                    hint={t("mapperBuilder.dropFieldHint")}
                    tooltipContent={t("mapperBuilder.fieldDescription.ATTRIBUTE_ENTRY" as never)}
                    showModeSelect
                    onDrop={handleDropScalar}
                    onSetDefault={handleAddDefaultScalar}
                />
                <AttrDropZone
                    label={t("mapperBuilder.attributeObjectLabel" as never)}
                    hint={t("mapperBuilder.dropObjectHint" as never)}
                    tooltipContent={t("mapperBuilder.fieldDescription.ATTRIBUTE_OBJECT" as never)}
                    onDrop={handleDropObject}
                />
            </Flex>
        </Box>
    );
}

// ---------------------------------------------------------------------------
// AttrDropZone — drop zone row for AttributesFieldGroup (styled like LabeledDropZone)
// ---------------------------------------------------------------------------

function AttrDropZone({
    label,
    hint,
    tooltipContent,
    showModeSelect,
    onDrop,
    onSetDefault,
}: {
    label: string;
    hint: string;
    tooltipContent: string;
    showModeSelect?: boolean;
    onDrop: (e: DragEvent) => void;
    onSetDefault?: () => void;
}) {
    const { t } = useTranslation();
    const [isDragOver, setIsDragOver] = useState(false);

    const handleDragOver = (e: DragEvent) => {
        if (!e.dataTransfer.types.includes(TREE_NODE_DRAG_TYPE)) return;
        e.preventDefault();
        e.dataTransfer.dropEffect = "copy";
        setIsDragOver(true);
    };

    return (
        <Tooltip content={tooltipContent} uppercase={false}>
            <Flex
                align="center"
                px="3"
                py="2"
                borderWidth="2px"
                borderStyle={isDragOver ? "solid" : "dashed"}
                borderColor={isDragOver ? "colorPalette.solid" : undefined}
                borderRadius="l3"
                bg={isDragOver ? "colorPalette.subtle" : "bg"}
                transition="backgrounds"
                _hover={{ bg: "bg.subtle" }}
                onDragOver={handleDragOver}
                onDragLeave={() => setIsDragOver(false)}
                onDrop={(e: DragEvent) => {
                    setIsDragOver(false);
                    onDrop(e);
                }}
            >
                {/* Left: label */}
                <Text fontSize="sm" color="fg.muted" whiteSpace="nowrap" flexShrink={0} w="140px">
                    {label}
                </Text>

                {/* Center: drop hint */}
                <Flex align="center" gap="1" flex="1" justify="center" color="fg.subtle">
                    <Icon asChild boxSize="3">
                        <LuArrowDownToLine />
                    </Icon>
                    <Text fontSize="xs" whiteSpace="nowrap">
                        {hint}
                    </Text>
                </Flex>

                {/* Right: "Add default" button (scalar zone only) or alignment spacer */}
                {showModeSelect ? (
                    <Box flexShrink={0} minW="130px" onClick={(e) => e.stopPropagation()}>
                        <Button size="xs" variant="ghost" onClick={() => onSetDefault?.()}>
                            {t("mapperBuilder.modeDefault" as never)}
                        </Button>
                    </Box>
                ) : (
                    <Box w="130px" flexShrink={0} />
                )}
            </Flex>
        </Tooltip>
    );
}

// ---------------------------------------------------------------------------
// FieldGroup — standard one-rule-per-target groups
// ---------------------------------------------------------------------------

interface FieldGroupProps {
    group: TargetGroup;
    rulesByTarget: Map<TargetField, MappingRule>;
    usedTargets: Set<TargetField>;
    onUpdate: (id: string, rule: MappingRule) => void;
    onRemove: (id: string) => void;
}

function FieldGroup({ group, rulesByTarget, usedTargets, onUpdate, onRemove }: FieldGroupProps) {
    const { t } = useTranslation();
    const sectionRef = useRef<HTMLDivElement>(null);

    const formatConfig = useMapperBuilderStore((s) => s.formatConfig);
    const sampleContent = useMapperBuilderStore((s) => s.sampleContent);
    const parsedTree = useMapperBuilderStore((s) => s.parsedTree);
    const parsedTreeLength = parsedTree.length;
    const recordNodeId = useMapperBuilderStore((s) => s.recordNodeId);
    const focusedSectionKey = useMapperBuilderStore((s) => s.focusedSectionKey);
    const errorSectionKeys = useMapperBuilderStore((s) => s.errorSectionKeys);
    const warningSectionKeys = useMapperBuilderStore((s) => s.warningSectionKeys);

    const { addRule, clearFocusSection } = useMapperBuilderActions();

    const hasError = errorSectionKeys.includes(group.key);
    const hasWarning = !hasError && warningSectionKeys.includes(group.key);

    // Support both section-level ("identity") and field-level ("field:OBJECT_ID") focus.
    // "section" flashes the group header; "field:X" flashes a specific drop zone or rule card.
    const isSectionFocused = focusedSectionKey === group.key;
    const focusedField = focusedSectionKey?.startsWith("field:")
        ? (focusedSectionKey.slice(6) as TargetField)
        : null;
    const isFieldFocused = focusedField !== null && group.fields.includes(focusedField);
    const fieldRef = useRef<Map<TargetField, HTMLElement>>(new Map());
    const [flashingTarget, setFlashingTarget] = useState<"section" | TargetField | null>(null);

    // Set flash state during render (React 19 pattern — avoids setState-in-effect lint rule)
    if (isSectionFocused && flashingTarget !== "section") {
        setFlashingTarget("section");
    }
    if (isFieldFocused && focusedField && flashingTarget !== focusedField) {
        setFlashingTarget(focusedField);
    }

    // DOM side effect: scroll into view and clear focus signal
    useEffect(() => {
        if (!isSectionFocused && !isFieldFocused) return;

        if (isSectionFocused && sectionRef.current) {
            scrollIntoView(sectionRef.current, {
                scrollMode: "if-needed",
                behavior: "smooth",
                block: "nearest",
            });
        } else if (isFieldFocused && focusedField) {
            const el = fieldRef.current.get(focusedField);
            if (el) {
                scrollIntoView(el, {
                    scrollMode: "if-needed",
                    behavior: "smooth",
                    block: "nearest",
                });
            }
        }
        clearFocusSection();
    }, [isSectionFocused, isFieldFocused, focusedField, clearFocusSection]);

    // Compute disabled fields for geometry mutual exclusivity
    const disabledFields = useMemo(() => {
        if (group.key !== "geometry") return new Set<TargetField>();

        const hasDiscrete = group.fields.some(
            (f) => GEOMETRY_DISCRETE.has(f) && usedTargets.has(f),
        );
        const hasCombined = group.fields.some(
            (f) => GEOMETRY_COMBINED.has(f) && usedTargets.has(f),
        );

        const disabled = new Set<TargetField>();
        if (hasDiscrete) {
            for (const f of GEOMETRY_COMBINED) disabled.add(f);
        }
        if (hasCombined) {
            for (const f of GEOMETRY_DISCRETE) disabled.add(f);
            // Also disable other COMBINED targets (GEOMETRY vs GEOMETRY_POINT are redundant)
            for (const f of GEOMETRY_COMBINED) {
                if (!usedTargets.has(f)) disabled.add(f);
            }
        }
        return disabled;
    }, [group, usedTargets]);

    // Count mapped fields in this group
    const mappedCount = group.fields.filter((f) => rulesByTarget.has(f)).length;

    // OBJECT_ID non-unique warning with counts
    const objectIdWarning = useMemo<
        { key: string; total: number; unique: number } | undefined
    >(() => {
        if (group.key !== "identity") return undefined;
        const objectIdRule = rulesByTarget.get("OBJECT_ID");
        if (!objectIdRule) return undefined;
        const expr = getExprFromSource(objectIdRule.source);
        if (!expr || !recordNodeId) return undefined;
        const counts = getValueCountsForExpr(parsedTree, recordNodeId, expr);
        if (counts.total <= 1 || counts.unique === counts.total) return undefined;
        return {
            key: "mapperBuilder.validation.objectIdNotUnique",
            total: counts.total,
            unique: counts.unique,
        };
    }, [group.key, rulesByTarget, parsedTree, recordNodeId]);

    /** Create a const-source rule for a default value */
    const handleSetDefault = (forTarget: TargetField) => {
        const rule: MappingRule = {
            id: uuid(),
            enabled: true,
            target: forTarget,
            source: { type: "const", value: "" },
            transforms: [],
            onMissing: "DEFAULT",
        };
        addRule(rule);
    };

    /** Drop handler for a specific target field */
    const handleDropForTarget = (e: DragEvent, forceTarget: TargetField) => {
        e.preventDefault();

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
            recordNodeId,
        );
        if (!result) return;

        const rule: MappingRule = {
            id: uuid(),
            enabled: true,
            target: forceTarget,
            source: result.source,
            transforms: [],
            onMissing: getOnMissing(forceTarget),
        };

        addRule(rule);
    };

    return (
        <Box
            ref={sectionRef}
            borderWidth={hasError || hasWarning ? "1px" : undefined}
            borderStyle={hasError || hasWarning ? "dashed" : undefined}
            borderColor={hasError ? "border.error" : hasWarning ? "border.warning" : undefined}
            borderRadius="md"
            style={
                flashingTarget === "section"
                    ? ({
                          "--flash-color": hasError
                              ? "var(--chakra-colors-border-error)"
                              : hasWarning
                                ? "var(--chakra-colors-border-warning)"
                                : "var(--chakra-colors-border)",
                          animation: "flash-border 0.6s steps(1)",
                      } as React.CSSProperties)
                    : undefined
            }
            onAnimationEnd={() => setFlashingTarget(null)}
        >
            {/* Group header */}
            <Flex
                align="center"
                gap="2"
                px="3"
                py="2"
                bg="bg.subtle"
                borderRadius="md"
                borderWidth="1px"
            >
                <Text
                    fontSize="sm"
                    fontWeight="medium"
                    flex="1"
                    color={hasError ? "fg.error" : hasWarning ? "fg.warning" : undefined}
                >
                    {t(group.labelKey as never)}
                    {group.required && (
                        <Text as="span" fontSize="xs" fontWeight="normal" color="fg.muted" ms="1">
                            ({t("mapperBuilder.required")})
                        </Text>
                    )}
                </Text>
                <Text fontSize="xs" color="fg.subtle">
                    {t("mapperBuilder.mappedCount", {
                        mapped: mappedCount,
                        total: group.fields.length,
                    })}
                </Text>
            </Flex>

            {/* Per-field slots */}
            <Flex direction="column" gap="2" pt="2">
                {group.fields.map((field) => {
                    const rule = rulesByTarget.get(field);
                    const isDisabled = disabledFields.has(field);
                    const setFieldRef = (el: HTMLElement | null) => {
                        if (el) fieldRef.current.set(field, el);
                        else fieldRef.current.delete(field);
                    };

                    const isFlashing = flashingTarget === field;

                    if (rule) {
                        return (
                            <RuleCard
                                key={field}
                                ref={setFieldRef}
                                rule={rule}
                                flashing={isFlashing}
                                onFlashEnd={() => setFlashingTarget(null)}
                                clientWarning={
                                    field === "OBJECT_ID" && objectIdWarning
                                        ? t(objectIdWarning.key as never, {
                                              total: objectIdWarning.total,
                                              unique: objectIdWarning.unique,
                                          })
                                        : undefined
                                }
                                onUpdate={onUpdate}
                                onRemove={onRemove}
                            />
                        );
                    }

                    return (
                        <LabeledDropZone
                            key={field}
                            ref={setFieldRef}
                            field={field}
                            disabled={isDisabled}
                            required={group.required}
                            flashing={isFlashing}
                            onFlashEnd={() => setFlashingTarget(null)}
                            warning={
                                field === "OBJECT_ID" && rulesByTarget.size > 0
                                    ? "mapperBuilder.validation.objectIdNotMapped"
                                    : undefined
                            }
                            onDropForTarget={handleDropForTarget}
                            onSetDefault={handleSetDefault}
                        />
                    );
                })}
            </Flex>
        </Box>
    );
}

/** Drop zone for a specific target field */
function LabeledDropZone({
    ref,
    field,
    disabled,
    required,
    flashing,
    onFlashEnd,
    warning,
    onDropForTarget,
    onSetDefault,
}: {
    ref?: React.Ref<HTMLDivElement>;
    field: TargetField;
    disabled?: boolean;
    required?: boolean;
    flashing?: boolean;
    onFlashEnd?: () => void;
    warning?: string;
    onDropForTarget: (e: DragEvent, target: TargetField) => void;
    onSetDefault?: (target: TargetField) => void;
}) {
    const { t } = useTranslation();
    const [isDragOver, setIsDragOver] = useState(false);

    const handleDragOver = (e: DragEvent) => {
        if (disabled) return;
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
        if (disabled) return;
        setIsDragOver(false);
        onDropForTarget(e, field);
    };

    const tooltipContent = disabled
        ? t("mapperBuilder.fieldDisabledReason")
        : t(`mapperBuilder.fieldDescription.${field}` as never);

    const showModeSelect = !required && !disabled && onSetDefault;

    const modeItems = useMemo(
        () => [
            { label: t("mapperBuilder.modeMapped" as never), value: "mapped" },
            { label: t("mapperBuilder.modeDefault" as never), value: "default" },
        ],
        [t],
    );

    const handleModeChange = (value: string) => {
        if (value === "default" && onSetDefault) {
            onSetDefault(field);
        }
    };

    return (
        <Flex direction="column">
            <Tooltip content={tooltipContent} uppercase={false}>
                <Flex
                    ref={ref}
                    align="center"
                    px="3"
                    py="2"
                    borderWidth={warning ? "1px" : "2px"}
                    borderStyle={isDragOver ? "solid" : "dashed"}
                    borderColor={
                        isDragOver
                            ? "colorPalette.solid"
                            : warning
                              ? "border.warning"
                              : disabled
                                ? "border.muted"
                                : undefined
                    }
                    borderRadius="l3"
                    bg={isDragOver ? "colorPalette.subtle" : "bg"}
                    opacity={disabled ? 0.4 : 1}
                    cursor={disabled ? "not-allowed" : "default"}
                    transition="backgrounds"
                    aria-disabled={disabled || undefined}
                    _hover={disabled ? undefined : { bg: "bg.subtle" }}
                    style={
                        flashing
                            ? ({
                                  "--flash-color": warning
                                      ? "var(--chakra-colors-border-warning)"
                                      : "var(--chakra-colors-border)",
                                  animation: "flash-border 0.6s steps(1)",
                              } as React.CSSProperties)
                            : undefined
                    }
                    onAnimationEnd={onFlashEnd}
                    onDragOver={handleDragOver}
                    onDragLeave={handleDragLeave}
                    onDrop={handleDrop}
                >
                    {/* Left: field name */}
                    <Text
                        fontSize="sm"
                        color={warning ? "fg.warning" : "fg.muted"}
                        whiteSpace="nowrap"
                        flexShrink={0}
                        w="140px"
                    >
                        {t(`mapperBuilder.field.${field}` as never)}
                    </Text>

                    {/* Center: drop hint */}
                    <Flex align="center" gap="1" flex="1" justify="center" color="fg.subtle">
                        <Icon asChild boxSize="3">
                            <LuArrowDownToLine />
                        </Icon>
                        <Text fontSize="xs" whiteSpace="nowrap">
                            {t("mapperBuilder.dropFieldHint")}
                        </Text>
                    </Flex>

                    {/* Right: mode selector (non-required only) */}
                    {showModeSelect ? (
                        <Box flexShrink={0} minW="130px" onClick={(e) => e.stopPropagation()}>
                            <OcSelect
                                items={modeItems}
                                value="mapped"
                                onValueChange={handleModeChange}
                                variant="plain"
                                size="xs"
                                portal={false}
                            />
                        </Box>
                    ) : (
                        /* Reserve space for alignment with rows that have the selector */
                        <Box w="130px" flexShrink={0} />
                    )}
                </Flex>
            </Tooltip>
            {warning && (
                <Text fontSize="xs" color="fg.warning" mt="1" px="3">
                    {t(warning as never)}
                </Text>
            )}
        </Flex>
    );
}
