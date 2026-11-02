// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Flex, Text } from "@chakra-ui/react";
import type { MappingRule } from "@opencop/api";
import { type DragEvent, useCallback, useMemo, useRef } from "react";
import { useTranslation } from "react-i18next";
import { LuWandSparkles, LuX } from "react-icons/lu";
import { autoMapFields } from "../mapper-builder.automap";
import { useMapperBuilderActions, useMapperBuilderStore } from "../mapper-builder.store";
import { TargetFieldGroups } from "./TargetFieldGroups";

/** How close (px) to the edge of the scroll container before auto-scrolling starts */
const SCROLL_EDGE_THRESHOLD = 48;
/** Pixels to scroll per dragover tick */
const SCROLL_SPEED = 12;

export function RuleBuilder() {
    const { t } = useTranslation();
    const rules = useMapperBuilderStore((s) => s.rules);
    const recordNodeId = useMapperBuilderStore((s) => s.recordNodeId);
    const formatConfig = useMapperBuilderStore((s) => s.formatConfig);
    const parsedTree = useMapperBuilderStore((s) => s.parsedTree);
    const { addRules, clearRules, updateRule, removeRule } = useMapperBuilderActions();

    const scrollRef = useRef<HTMLDivElement>(null);

    const usedTargets = useMemo(() => new Set(rules.map((r) => r.target)), [rules]);

    const handleAutoMap = useCallback(() => {
        if (!recordNodeId || !formatConfig) return;

        const excludeTargets = new Set(rules.map((r) => r.target));
        const newRules = autoMapFields(parsedTree, recordNodeId, formatConfig, excludeTargets);

        if (newRules.length > 0) {
            addRules(newRules);
        }
    }, [recordNodeId, formatConfig, parsedTree, rules, addRules]);

    function handleUpdate(id: string, rule: MappingRule) {
        updateRule(id, rule);
    }

    function handleRemove(id: string) {
        removeRule(id);
    }

    /** Auto-scroll the container when dragging near the top or bottom edge */
    const handleDragOver = (e: DragEvent) => {
        const container = scrollRef.current;
        if (!container) return;

        const rect = container.getBoundingClientRect();
        const y = e.clientY - rect.top;

        if (y < SCROLL_EDGE_THRESHOLD) {
            container.scrollTop -= SCROLL_SPEED;
        } else if (y > rect.height - SCROLL_EDGE_THRESHOLD) {
            container.scrollTop += SCROLL_SPEED;
        }
    };

    return (
        <Flex direction="column" h="full">
            <Flex align="center" justify="space-between" mb="2">
                <Text fontSize="xs" fontWeight="medium" color="fg.subtle">
                    {t("mapperBuilder.mappingRules")}
                </Text>
                <Flex gap="2">
                    {rules.length > 0 && (
                        <Button size="sm" variant="ghost" onClick={clearRules}>
                            <LuX />
                            {t("mapperBuilder.clearRules")}
                        </Button>
                    )}
                    <Button size="sm" variant="ghost" color="active" onClick={handleAutoMap}>
                        <LuWandSparkles />
                        {t("mapperBuilder.autoMap")}
                    </Button>
                </Flex>
            </Flex>

            <Box ref={scrollRef} flex="1" overflow="auto" onDragOver={handleDragOver}>
                <TargetFieldGroups
                    usedTargets={usedTargets}
                    onUpdate={handleUpdate}
                    onRemove={handleRemove}
                />
            </Box>
        </Flex>
    );
}
