// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Box,
    Field as ChakraField,
    Flex,
    IconButton,
    Input,
    NativeSelectField,
    NativeSelectRoot,
    Text,
} from "@chakra-ui/react";
import { type MappingRule, type OnMissing, type TransformOperation } from "@opencop/api";
import { Tooltip } from "@opencop/ui";
import { type ChangeEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import scrollIntoView from "scroll-into-view-if-needed";
import { useTranslation } from "react-i18next";
import { LuChevronDown, LuChevronUp, LuToggleLeft, LuToggleRight, LuTrash2 } from "react-icons/lu";
import { useMapperBuilderActions, useMapperBuilderStore } from "../mapper-builder.store";
import {
    basePath,
    buildRelativeJsonPointer,
    buildRelativeXPath,
    getExprFromSource,
} from "../mapper-builder.util";
import { collectAllNodes } from "../mapper-builder.validation";
import { TransformEditor } from "./TransformEditor";

const ON_MISSING_OPTIONS: OnMissing[] = ["NULL", "DEFAULT", "ERROR"];

/** Transforms that produce a value even when the source is null/blank. */
const NULL_ACCEPTING_OPS: Set<TransformOperation> = new Set(["DEFAULT_NOW"]);

interface RuleCardProps {
    ref?: React.Ref<HTMLElement>;
    rule: MappingRule;
    /** Client-side warning (e.g., non-unique OBJECT_ID values) */
    clientWarning?: string;
    flashing?: boolean;
    onFlashEnd?: () => void;
    onUpdate: (id: string, rule: MappingRule) => void;
    onRemove: (id: string) => void;
}

export function RuleCard({
    ref,
    rule,
    clientWarning,
    flashing,
    onFlashEnd,
    onUpdate,
    onRemove,
}: RuleCardProps) {
    const { t } = useTranslation();
    const ruleErrors = useMapperBuilderStore((s) => s.ruleErrors);
    const ruleWarnings = useMapperBuilderStore((s) => s.ruleWarnings);
    const focusedRuleId = useMapperBuilderStore((s) => s.focusedRuleId);
    const { clearFocusRule } = useMapperBuilderActions();

    // For whole-object ATTRIBUTES rules: look up the source node's child keys
    // in the parsed sample tree so users can see what properties will be mapped.
    const parsedTree = useMapperBuilderStore((s) => s.parsedTree);
    const recordNodeId = useMapperBuilderStore((s) => s.recordNodeId);
    const formatConfig = useMapperBuilderStore((s) => s.formatConfig);
    const isWholeObjectAttr = rule.target === "ATTRIBUTES" && rule.attributeKey === undefined;
    const objectChildKeys = useMemo(() => {
        if (!isWholeObjectAttr || !formatConfig) return null;
        const sourceExpr = getExprFromSource(rule.source);
        if (!sourceExpr) return null;

        for (const node of collectAllNodes(parsedTree)) {
            if (!node.children || node.children.length === 0) continue;
            let nodeExpr: string;
            if (formatConfig.type === "json") {
                if (recordNodeId) {
                    nodeExpr = buildRelativeJsonPointer(node, recordNodeId);
                } else {
                    // Flat JSON (no record boundary): compare against absolute path
                    const segments = basePath(node.id)
                        .split("/")
                        .slice(1)
                        .filter((p) => !/^\d+$/.test(p));
                    nodeExpr = segments.length === 0 ? "" : "/" + segments.join("/");
                }
            } else if (formatConfig.type === "xml" || formatConfig.type === "kml+kmz") {
                if (!recordNodeId) continue;
                nodeExpr = buildRelativeXPath(node, recordNodeId);
            } else {
                continue;
            }
            if (nodeExpr === sourceExpr) {
                return node.children.map((c) => c.name);
            }
        }
        return null;
    }, [isWholeObjectAttr, parsedTree, recordNodeId, formatConfig, rule.source]);

    const [expanded, setExpanded] = useState(rule.source.type === "const");
    const [ruleFlashing, setRuleFlashing] = useState(false);
    const cardRef = useRef<HTMLDivElement>(null);
    const mergedRef = useCallback(
        (el: HTMLDivElement | null) => {
            cardRef.current = el;
            if (typeof ref === "function") ref(el);
            else if (ref && "current" in ref) ref.current = el;
        },
        [ref],
    );

    // rule.id is string | undefined per API type; in our store it is always set
    const ruleId = rule.id ?? "";
    const isFocused = focusedRuleId === ruleId;

    // Adjust state during render (React 19 pattern for derived state)
    if (isFocused && !expanded) {
        setExpanded(true);
    }
    if (isFocused && !ruleFlashing) {
        setRuleFlashing(true);
    }

    // DOM side effect: scroll into view and clear focus signal
    useEffect(() => {
        if (!isFocused) return;
        if (cardRef.current) {
            scrollIntoView(cardRef.current, {
                scrollMode: "if-needed",
                behavior: "smooth",
                block: "nearest",
            });
        }
        clearFocusRule();
    }, [isFocused, clearFocusRule]);

    // Combine both flash sources: "Go to rule" (ruleFlashing) and "Go to Object ID" (flashing prop)
    const isFlashing = ruleFlashing || flashing;

    const sourceExpr =
        rule.source.type === "xpath" || rule.source.type === "jsonpath"
            ? rule.source.expr
            : rule.source.type === "csvColumn"
              ? (rule.source.column ?? String(rule.source.index ?? ""))
              : rule.source.type === "const"
                ? String(rule.source.value ?? "")
                : "";

    // Attribute key blur-based validation (ATTRIBUTES scalar-entry rules only)
    const [attrKeyTouched, setAttrKeyTouched] = useState(false);
    const isAttributesScalarRule = rule.target === "ATTRIBUTES" && rule.attributeKey !== undefined;
    const attrKeyError =
        attrKeyTouched && isAttributesScalarRule && !rule.attributeKey
            ? "mapperBuilder.validation.attributeKeyRequired"
            : undefined;

    // Local blur-based validation for const (default value) rules.
    // Skip when the first transform accepts null input (e.g. DEFAULT_NOW produces a value).
    const [constTouched, setConstTouched] = useState(false);
    const firstTransformAcceptsNull = NULL_ACCEPTING_OPS.has(rule.transforms[0]?.op);
    const constBlurError =
        constTouched &&
        rule.source.type === "const" &&
        !rule.source.value &&
        !firstTransformAcceptsNull
            ? "mapperBuilder.validation.defaultValueRequired"
            : undefined;

    const validationError =
        attrKeyError ?? constBlurError ?? (ruleId ? ruleErrors[ruleId] : undefined);
    const validationWarning =
        !validationError && ruleId
            ? (ruleWarnings[ruleId] ?? clientWarning)
            : !validationError
              ? clientWarning
              : undefined;

    // Available targets: those not used + the current rule's target

    return (
        <Box
            ref={mergedRef}
            borderWidth="1px"
            borderStyle={validationError || validationWarning ? "dashed" : undefined}
            borderColor={
                validationError ? "border.error" : validationWarning ? "border.warning" : undefined
            }
            borderRadius="md"
            px="2.5"
            py="1.5"
            style={
                isFlashing
                    ? ({
                          "--flash-color": validationError
                              ? "var(--chakra-colors-border-error)"
                              : validationWarning
                                ? "var(--chakra-colors-border-warning)"
                                : "var(--chakra-colors-border)",
                          animation: "flash-border 0.6s steps(1)",
                      } as React.CSSProperties)
                    : undefined
            }
            onAnimationEnd={() => {
                setRuleFlashing(false);
                onFlashEnd?.();
            }}
            mb="1.5"
            opacity={rule.enabled ? 1 : 0.5}
        >
            {/* Collapsed view */}
            <Flex align="center" justify="space-between">
                <Flex
                    as="button"
                    align="center"
                    gap="2"
                    flex="1"
                    cursor="pointer"
                    aria-expanded={expanded}
                    aria-label={t(`mapperBuilder.field.${rule.target}` as never)}
                    onClick={() => setExpanded(!expanded)}
                >
                    {expanded ? <LuChevronUp size={10} /> : <LuChevronDown size={10} />}
                    <Text fontSize="xs" fontWeight="medium">
                        {rule.target === "ATTRIBUTES"
                            ? rule.attributeKey !== undefined
                                ? t("mapperBuilder.attributeSingleLabel" as never)
                                : t("mapperBuilder.mapAsWholeObject" as never)
                            : t(`mapperBuilder.field.${rule.target}` as never)}
                    </Text>
                    <Text fontSize="xs" color="fg.subtle">
                        &larr;{" "}
                        {rule.source.type === "const"
                            ? t("mapperBuilder.defaultValue")
                            : sourceExpr}
                    </Text>
                </Flex>
                <Flex gap="0.5">
                    <Tooltip
                        content={t(
                            rule.enabled ? "mapperBuilder.disableRule" : "mapperBuilder.enableRule",
                        )}
                    >
                        <IconButton
                            aria-label={t(
                                rule.enabled
                                    ? "mapperBuilder.disableRule"
                                    : "mapperBuilder.enableRule",
                            )}
                            size="2xs"
                            variant="ghost"
                            color={rule.enabled ? "fg.subtle" : "fg.muted"}
                            _hover={{ color: rule.enabled ? "fg.muted" : "fg" }}
                            onClick={() => onUpdate(ruleId, { ...rule, enabled: !rule.enabled })}
                        >
                            {rule.enabled ? <LuToggleRight /> : <LuToggleLeft />}
                        </IconButton>
                    </Tooltip>
                    <Tooltip content={t("common.delete")}>
                        <IconButton
                            aria-label={t("common.delete")}
                            size="2xs"
                            variant="ghost"
                            color="fg.subtle"
                            _hover={{ color: "fg.error" }}
                            onClick={() => onRemove(ruleId)}
                        >
                            <LuTrash2 />
                        </IconButton>
                    </Tooltip>
                </Flex>
            </Flex>

            {validationError && (
                <Text fontSize="xs" color="fg.error" mt="1" ps="5">
                    {t(validationError as never)}
                </Text>
            )}
            {validationWarning && (
                <Text fontSize="xs" color="fg.warning" mt="1" ps="5">
                    {t(validationWarning as never)}
                </Text>
            )}

            {/* Expanded view */}
            {expanded && (
                <Box mt="3" pl="5">
                    {/* Attribute key input — scalar-entry ATTRIBUTES rules only */}
                    {rule.target === "ATTRIBUTES" && rule.attributeKey !== undefined && (
                        <ChakraField.Root mb="2" invalid={!!attrKeyError}>
                            <ChakraField.Label fontSize="xs">
                                {t("mapperBuilder.attributeKey" as never)}
                            </ChakraField.Label>
                            <Input
                                size="sm"
                                placeholder={t("mapperBuilder.attributeKeyPlaceholder" as never)}
                                value={rule.attributeKey}
                                onChange={(e) =>
                                    onUpdate(ruleId, {
                                        ...rule,
                                        attributeKey: e.target.value,
                                    })
                                }
                                onBlur={() => setAttrKeyTouched(true)}
                            />
                            {attrKeyError && (
                                <ChakraField.ErrorText fontSize="xs">
                                    {t(attrKeyError as never)}
                                </ChakraField.ErrorText>
                            )}
                        </ChakraField.Root>
                    )}

                    {/* Object keys preview — whole-object ATTRIBUTES rules */}
                    {isWholeObjectAttr && objectChildKeys && objectChildKeys.length > 0 && (
                        <Box mb="2">
                            <Text fontSize="xs" color="fg.muted" mb="1">
                                {t("mapperBuilder.attributeObjectKeys" as never)}
                            </Text>
                            <Flex flexWrap="wrap" gap="1">
                                {objectChildKeys.map((key) => (
                                    <Text
                                        key={key}
                                        fontSize="xs"
                                        px="1.5"
                                        py="0.5"
                                        bg="bg.subtle"
                                        borderRadius="sm"
                                        borderWidth="1px"
                                        fontFamily="mono"
                                    >
                                        {key}
                                    </Text>
                                ))}
                            </Flex>
                        </Box>
                    )}

                    {rule.source.type !== "const" && (
                        <Box mb="2">
                            <Text fontSize="xs" mb="1">
                                {t("mapperBuilder.sourceExpression")}
                            </Text>
                            <Input
                                size="sm"
                                aria-label={t("mapperBuilder.sourceExpression")}
                                value={sourceExpr}
                                onChange={(e) => {
                                    const val = e.target.value;
                                    const src = rule.source;
                                    if (src.type === "xpath" || src.type === "jsonpath") {
                                        onUpdate(ruleId, {
                                            ...rule,
                                            source: { ...src, expr: val },
                                        });
                                    } else if (src.type === "csvColumn") {
                                        onUpdate(ruleId, {
                                            ...rule,
                                            source: { ...src, column: val },
                                        });
                                    }
                                }}
                            />
                        </Box>
                    )}

                    <Flex gap="3" mb="2" align="end">
                        <Box flex="1">
                            <Text fontSize="xs" mb="1">
                                {t("mapperBuilder.onMissing")}
                            </Text>
                            <NativeSelectRoot size="sm">
                                <NativeSelectField
                                    aria-label={t("mapperBuilder.onMissing")}
                                    value={rule.onMissing}
                                    onChange={(e: ChangeEvent<HTMLSelectElement>) =>
                                        onUpdate(ruleId, {
                                            ...rule,
                                            onMissing: e.target.value as OnMissing,
                                        })
                                    }
                                >
                                    {ON_MISSING_OPTIONS.map((opt) => (
                                        <option key={opt} value={opt}>
                                            {t(`mapperBuilder.onMissingOption.${opt}` as never)}
                                        </option>
                                    ))}
                                </NativeSelectField>
                            </NativeSelectRoot>
                        </Box>
                    </Flex>

                    {rule.onMissing === "DEFAULT" && (
                        <Box mb="2">
                            <Text fontSize="xs" mb="1">
                                {t("mapperBuilder.defaultValue")}
                            </Text>
                            <Input
                                size="sm"
                                aria-label={t("mapperBuilder.defaultValue")}
                                autoFocus={rule.source.type === "const"}
                                disabled={firstTransformAcceptsNull}
                                placeholder={
                                    firstTransformAcceptsNull
                                        ? t("mapperBuilder.defaultValueProvidedByTransform")
                                        : undefined
                                }
                                value={
                                    firstTransformAcceptsNull
                                        ? ""
                                        : rule.source.type === "const"
                                          ? String(rule.source.value ?? "")
                                          : String(rule.defaultValue ?? "")
                                }
                                onChange={(e) => {
                                    if (rule.source.type === "const") {
                                        onUpdate(ruleId, {
                                            ...rule,
                                            source: {
                                                ...rule.source,
                                                value: e.target.value,
                                            },
                                        });
                                    } else {
                                        onUpdate(ruleId, {
                                            ...rule,
                                            defaultValue: e.target.value,
                                        });
                                    }
                                }}
                                onBlur={() => {
                                    if (rule.source.type === "const") {
                                        setConstTouched(true);
                                    }
                                }}
                            />
                        </Box>
                    )}

                    <TransformEditor
                        transforms={rule.transforms}
                        onChange={(transforms) => onUpdate(ruleId, { ...rule, transforms })}
                        targetField={rule.target}
                    />
                </Box>
            )}
        </Box>
    );
}
