// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, Text } from "@chakra-ui/react";
import { useCallback, useEffect, useId, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

/** A single rendered line in the JSON tree */
interface JsonLine {
    /** Unique key for React */
    key: string;
    /** Indentation depth (number of spaces) */
    indent: number;
    /** The text content of this line */
    text: string;
    /** If this line starts a collapsible block, this is the path to track expand/collapse */
    collapsiblePath?: string;
    /** Number of children hidden when collapsed (for the "..." summary) */
    childCount?: number;
}

/** Build a flat list of renderable lines from a JSON value */
function buildJsonLines(
    value: unknown,
    collapsed: Set<string>,
    path = "",
    indent = 0,
    trailingComma = false,
): JsonLine[] {
    const comma = trailingComma ? "," : "";

    if (value === null) return [{ key: path || "null", indent, text: `null${comma}` }];
    if (typeof value === "boolean")
        return [{ key: path || "bool", indent, text: `${value}${comma}` }];
    if (typeof value === "number")
        return [{ key: path || "num", indent, text: `${value}${comma}` }];
    if (typeof value === "string") {
        const escaped = JSON.stringify(value);
        return [{ key: path || "str", indent, text: `${escaped}${comma}` }];
    }

    if (Array.isArray(value)) {
        if (value.length === 0) return [{ key: path || "[]", indent, text: `[]${comma}` }];

        const collapsiblePath = path || "root";
        const isCollapsed = collapsed.has(collapsiblePath);

        if (isCollapsed) {
            return [
                {
                    key: `${collapsiblePath}-open`,
                    indent,
                    text: `[...${comma}`,
                    collapsiblePath,
                    childCount: value.length,
                },
            ];
        }

        const lines: JsonLine[] = [
            {
                key: `${collapsiblePath}-open`,
                indent,
                text: "[",
                collapsiblePath,
                childCount: value.length,
            },
        ];
        for (let i = 0; i < value.length; i++) {
            const childPath = `${collapsiblePath}[${i}]`;
            const isLast = i === value.length - 1;
            lines.push(...buildJsonLines(value[i], collapsed, childPath, indent + 2, !isLast));
        }
        lines.push({ key: `${collapsiblePath}-close`, indent, text: `]${comma}` });
        return lines;
    }

    if (typeof value === "object") {
        const entries = Object.entries(value as Record<string, unknown>);
        if (entries.length === 0) return [{ key: path || "{}", indent, text: `{}${comma}` }];

        const collapsiblePath = path || "root";
        const isCollapsed = collapsed.has(collapsiblePath);

        if (isCollapsed) {
            return [
                {
                    key: `${collapsiblePath}-open`,
                    indent,
                    text: `{...}${comma}`,
                    collapsiblePath,
                    childCount: entries.length,
                },
            ];
        }

        const lines: JsonLine[] = [
            {
                key: `${collapsiblePath}-open`,
                indent,
                text: "{",
                collapsiblePath,
                childCount: entries.length,
            },
        ];
        for (let i = 0; i < entries.length; i++) {
            const [k, v] = entries[i];
            const childPath = `${collapsiblePath}.${k}`;
            const isLast = i === entries.length - 1;

            // For primitive values, render key: value on one line
            if (v === null || typeof v !== "object") {
                const valStr =
                    v === null ? "null" : typeof v === "string" ? JSON.stringify(v) : String(v);
                lines.push({
                    key: childPath,
                    indent: indent + 2,
                    text: `${JSON.stringify(k)}: ${valStr}${isLast ? "" : ","}`,
                });
            } else {
                // For objects/arrays, render key: then the nested structure
                const nested = buildJsonLines(v, collapsed, childPath, indent + 2, !isLast);
                if (nested.length > 0) {
                    nested[0] = {
                        ...nested[0],
                        text: `${JSON.stringify(k)}: ${nested[0].text}`,
                    };
                }
                lines.push(...nested);
            }
        }
        lines.push({ key: `${collapsiblePath}-close`, indent, text: `}${comma}` });
        return lines;
    }

    return [{ key: path || "unknown", indent, text: `${String(value)}${comma}` }];
}

export interface JsonTreeViewProps {
    /** The JSON data to display */
    data: unknown;
}

/** Height of each row in px — matches fontSize="xs" + lineHeight="tall" + no padding */
const ROW_HEIGHT = 20;
/** Extra rows rendered above/below the viewport to reduce flicker during scroll */
const OVERSCAN = 5;

/** Collapsible JSON tree viewer with line numbers (virtualized) */
export function JsonTreeView({ data }: JsonTreeViewProps) {
    const { t } = useTranslation();
    const treeId = useId();
    const containerRef = useRef<HTMLDivElement>(null);
    const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set());
    const [containerHeight, setContainerHeight] = useState(0);
    const [scrollTop, setScrollTop] = useState(0);

    const toggle = useCallback((path: string) => {
        setCollapsed((prev) => {
            const next = new Set(prev);
            if (next.has(path)) {
                next.delete(path);
            } else {
                next.add(path);
            }
            return next;
        });
    }, []);

    const lines = useMemo(() => buildJsonLines(data, collapsed), [data, collapsed]);
    const totalHeight = lines.length * ROW_HEIGHT;

    // Measure container height via ResizeObserver
    useEffect(() => {
        const el = containerRef.current;
        if (!el) return;

        let rafId = 0;
        const observer = new ResizeObserver((entries) => {
            const entry = entries[0];
            if (!entry) return;
            cancelAnimationFrame(rafId);
            rafId = requestAnimationFrame(() => {
                setContainerHeight(Math.floor(entry.contentRect.height));
            });
        });
        observer.observe(el);
        return () => {
            cancelAnimationFrame(rafId);
            observer.disconnect();
        };
    }, []);

    // Track scroll position
    const handleScroll = useCallback(() => {
        const el = containerRef.current;
        if (el) setScrollTop(el.scrollTop);
    }, []);

    // Compute visible window
    const startIndex = Math.max(0, Math.floor(scrollTop / ROW_HEIGHT) - OVERSCAN);
    const visibleCount = Math.ceil(containerHeight / ROW_HEIGHT) + 2 * OVERSCAN;
    const endIndex = Math.min(lines.length, startIndex + visibleCount);

    return (
        <Box
            ref={containerRef}
            as="pre"
            role="tree"
            aria-label={t("jsonTreeView.label")}
            fontFamily="mono"
            fontSize="xs"
            lineHeight="tall"
            p="2"
            m="0"
            bg="bg.subtle"
            borderRadius="0"
            borderWidth="1px"
            borderColor="border"
            flex="1"
            minH="0"
            overflow="auto"
            onScroll={handleScroll}
        >
            <div style={{ height: totalHeight, position: "relative" }}>
                {lines.slice(startIndex, endIndex).map((line, i) => {
                    const absoluteIndex = startIndex + i;
                    const isCollapsible = !!line.collapsiblePath;
                    const isCollapsed = isCollapsible && collapsed.has(line.collapsiblePath!);

                    return (
                        <Flex
                            key={line.key}
                            align="center"
                            gap="0"
                            _hover={{ bg: "bg.muted" }}
                            role="treeitem"
                            aria-expanded={isCollapsible ? !isCollapsed : undefined}
                            style={{
                                position: "absolute",
                                top: absoluteIndex * ROW_HEIGHT,
                                left: 0,
                                right: 0,
                                height: ROW_HEIGHT,
                            }}
                        >
                            {/* Line number */}
                            <Text
                                as="span"
                                aria-hidden="true"
                                color="fg.subtle"
                                w="8"
                                textAlign="right"
                                pr="3"
                                flexShrink={0}
                                userSelect="none"
                                fontFamily="mono"
                                fontSize="xs"
                            >
                                {absoluteIndex + 1}
                            </Text>

                            {/* Collapse/expand arrow for collapsible lines */}
                            {isCollapsible ? (
                                <Text
                                    as="button"
                                    w="3"
                                    flexShrink={0}
                                    color="fg.subtle"
                                    cursor="pointer"
                                    userSelect="none"
                                    fontSize="xs"
                                    fontFamily="body"
                                    aria-label={
                                        isCollapsed
                                            ? t("jsonTreeView.expand")
                                            : t("jsonTreeView.collapse")
                                    }
                                    aria-controls={`${treeId}-${line.collapsiblePath}`}
                                    onClick={() => toggle(line.collapsiblePath!)}
                                >
                                    {isCollapsed ? "▸" : "▾"}
                                </Text>
                            ) : (
                                <Text
                                    as="span"
                                    w="3"
                                    flexShrink={0}
                                    userSelect="none"
                                    fontSize="xs"
                                    aria-hidden="true"
                                />
                            )}

                            {/* Content */}
                            <Text
                                as="span"
                                whiteSpace="pre"
                                fontFamily="mono"
                                fontSize="xs"
                                id={isCollapsible ? `${treeId}-${line.collapsiblePath}` : undefined}
                            >
                                {"  ".repeat(line.indent / 2)}
                                {line.text}
                                {isCollapsible && isCollapsed && (
                                    <Text as="span" color="fg.subtle" ms="1">
                                        {t("jsonTreeView.collapsedCount", {
                                            count: line.childCount,
                                        })}
                                    </Text>
                                )}
                            </Text>
                        </Flex>
                    );
                })}
            </div>
        </Box>
    );
}
