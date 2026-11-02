// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, IconButton, Input, Text } from "@chakra-ui/react";
import { Tooltip } from "@opencop/ui";
import { Tree } from "react-arborist";
import type { NodeApi, NodeRendererProps, TreeApi } from "react-arborist";
import { createContext, use, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { LuChevronsDownUp, LuChevronsUpDown, LuSearch } from "react-icons/lu";
import type { TreeNode } from "./mapper-builder.parsers";
import { basePath } from "./mapper-builder.util";

interface SourceViewerProps {
    tree: TreeNode[];
    /** When set, nodes outside this prefix are dimmed */
    recordNodeId?: string | null;
}

/** Context to pass recordNodeId into NodeRenderer without re-creating the component */
const RecordScopeContext = createContext<string | null>(null);

/** MIME type used for tree-node drag data */
export const TREE_NODE_DRAG_TYPE = "application/x-opencop-tree-node";

/** Base left padding (in px) applied to every tree row so the top-level toggle aligns with the filter input */
const NODE_BASE_PADDING_LEFT = 12; // matches toolbar px="3"

function NodeRenderer({ node, style }: NodeRendererProps<TreeNode>) {
    const { t } = useTranslation();
    const data = node.data;
    const hasChildren = (data.children?.length ?? 0) > 0;
    const recordNodeId = use(RecordScopeContext);

    let isRecordNode = false;
    let isField = false;
    if (recordNodeId !== null) {
        const recordBase = basePath(recordNodeId);
        const nodeBase = basePath(data.id);
        isRecordNode = nodeBase === recordBase;
        isField = nodeBase.startsWith(recordBase + "/");
    }

    // Before record selector: all nodes are draggable (for setting record selector).
    // Root must be draggable for single-object JSON or XML where root IS the record.
    // After record selector: only field nodes within the record are draggable (for creating rules).
    const draggable = recordNodeId === null || isField;
    const dimmed = recordNodeId !== null && !isRecordNode && !isField;

    // Merge our base padding with react-arborist's depth-based paddingLeft
    // (inline style overrides Chakra's class-based px, so we must combine them here)
    const mergedStyle = {
        ...style,
        paddingLeft: ((style.paddingLeft as number) ?? 0) + NODE_BASE_PADDING_LEFT,
    };

    return (
        <Flex
            style={mergedStyle}
            align="center"
            gap="1.5"
            pr="3"
            overflow="hidden"
            role="treeitem"
            aria-expanded={hasChildren ? node.isOpen : undefined}
            opacity={dimmed ? 0.4 : 1}
        >
            {/* Expand/collapse arrow — only toggles, doesn't select */}
            {hasChildren ? (
                <Text
                    as="button"
                    fontSize="xs"
                    color="fg.subtle"
                    w="3"
                    flexShrink={0}
                    cursor="pointer"
                    aria-label={
                        node.isOpen
                            ? t("mapperBuilder.collapseNode")
                            : t("mapperBuilder.expandNode")
                    }
                    aria-expanded={node.isOpen}
                    onClick={(e) => {
                        e.stopPropagation();
                        node.toggle();
                    }}
                >
                    {node.isOpen ? "▾" : "▸"}
                </Text>
            ) : (
                <Box w="3" flexShrink={0} />
            )}

            {/* Node name — draggable for both record selector and field rule creation */}
            <Text
                fontSize="sm"
                fontWeight={isRecordNode || hasChildren ? "medium" : "normal"}
                color={isRecordNode ? "fg" : draggable ? "active" : "fg.subtle"}
                cursor={draggable ? "grab" : "default"}
                _hover={draggable ? { textDecoration: "underline" } : {}}
                draggable={draggable}
                onDragStart={(e) => {
                    if (!draggable) return;
                    e.dataTransfer.setData(
                        TREE_NODE_DRAG_TYPE,
                        JSON.stringify({ id: data.id, name: data.name, nodeType: data.nodeType }),
                    );
                    e.dataTransfer.effectAllowed = "copy";
                }}
                flexShrink={0}
            >
                {data.name}
            </Text>

            {/* Leaf value */}
            {data.value !== undefined && (
                <Text fontSize="sm" color="fg.subtle" truncate>
                    {data.value}
                </Text>
            )}
        </Flex>
    );
}

export function SourceViewer({ tree, recordNodeId }: SourceViewerProps) {
    const { t } = useTranslation();
    const containerRef = useRef<HTMLDivElement>(null);
    const internalTreeRef = useRef<TreeApi<TreeNode> | null>(null);
    const [size, setSize] = useState({ width: 300, height: 400 });
    const [searchTerm, setSearchTerm] = useState("");
    const [expanded, setExpanded] = useState(false);

    const treeRefCallback = useCallback((instance: TreeApi<TreeNode> | null) => {
        internalTreeRef.current = instance;
    }, []);

    useEffect(() => {
        const el = containerRef.current;
        if (!el) return;

        let rafId = 0;
        const observer = new ResizeObserver((entries) => {
            const entry = entries[0];
            if (!entry) return;
            cancelAnimationFrame(rafId);
            rafId = requestAnimationFrame(() => {
                setSize({
                    width: Math.floor(entry.contentRect.width),
                    height: Math.floor(entry.contentRect.height),
                });
            });
        });
        observer.observe(el);
        return () => {
            cancelAnimationFrame(rafId);
            observer.disconnect();
        };
    }, []);

    function toggleExpandAll() {
        if (expanded) {
            internalTreeRef.current?.closeAll();
        } else {
            internalTreeRef.current?.openAll();
        }
        setExpanded(!expanded);
    }

    // Create a disconnected DOM element for react-dnd's HTML5Backend.
    // This prevents react-dnd from intercepting native drag events on the actual tree nodes.
    const dndSink = useMemo(() => document.createElement("div"), []);

    const searchMatch = useCallback((node: NodeApi<TreeNode>, term: string) => {
        const lower = term.toLowerCase();
        const data = node.data;
        return (
            data.name.toLowerCase().includes(lower) ||
            (data.value?.toLowerCase().includes(lower) ?? false)
        );
    }, []);

    return (
        <Flex direction="column" h="full">
            {/* Mini toolbar */}
            <Flex align="center" gap="1" px="3" py="1" flexShrink={0}>
                <Box position="relative" flex="1">
                    <Box
                        position="absolute"
                        left="1.5"
                        top="50%"
                        transform="translateY(-50%)"
                        zIndex={1}
                        color="fg.subtle"
                    >
                        <LuSearch size={12} />
                    </Box>
                    <Input
                        size="2xs"
                        aria-label={t("mapperBuilder.searchNodes")}
                        placeholder={t("mapperBuilder.searchNodes")}
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        pl="6"
                    />
                </Box>
                <Tooltip
                    content={
                        expanded ? t("mapperBuilder.collapseAll") : t("mapperBuilder.expandAll")
                    }
                >
                    <IconButton
                        aria-label={
                            expanded ? t("mapperBuilder.collapseAll") : t("mapperBuilder.expandAll")
                        }
                        size="2xs"
                        variant="ghost"
                        onClick={toggleExpandAll}
                    >
                        {expanded ? <LuChevronsDownUp /> : <LuChevronsUpDown />}
                    </IconButton>
                </Tooltip>
            </Flex>

            <Box ref={containerRef} flex="1" overflow="hidden">
                <RecordScopeContext value={recordNodeId ?? null}>
                    {size.height > 0 && (
                        <Tree
                            ref={treeRefCallback as React.Ref<TreeApi<TreeNode> | undefined>}
                            data={tree}
                            width={size.width}
                            height={size.height}
                            rowHeight={28}
                            indent={16}
                            searchTerm={searchTerm}
                            searchMatch={searchMatch}
                            disableDrag={true}
                            disableDrop={true}
                            dndRootElement={dndSink}
                        >
                            {NodeRenderer}
                        </Tree>
                    )}
                </RecordScopeContext>
            </Box>
        </Flex>
    );
}
