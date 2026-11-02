// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Box, Flex, IconButton, VStack } from "@chakra-ui/react";
import { LuChevronUp, LuChevronDown, LuGripVertical } from "react-icons/lu";
import type { ReactNode } from "react";

export interface SortableLayerEntryProps {
    id: string;
    canMoveUp: boolean;
    canMoveDown: boolean;
    onMoveUp: () => void;
    onMoveDown: () => void;
    dragAriaLabel: string;
    moveUpAriaLabel: string;
    moveDownAriaLabel: string;
    handleBorderWidth?: string;
    children: ReactNode;
}

export function SortableLayerEntry({
    id,
    canMoveUp,
    canMoveDown,
    onMoveUp,
    onMoveDown,
    dragAriaLabel,
    moveUpAriaLabel,
    moveDownAriaLabel,
    handleBorderWidth = "1px",
    children,
}: SortableLayerEntryProps) {
    const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
        id,
    });

    const style = {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.1 : 1,
    };

    return (
        <Flex ref={setNodeRef} style={style} alignItems="stretch" w="full">
            <VStack
                gap="0"
                flexShrink={0}
                justify="center"
                borderRightWidth={handleBorderWidth}
                borderColor="border.muted"
            >
                <Box
                    {...attributes}
                    {...listeners}
                    cursor={isDragging ? "grabbing" : "grab"}
                    color="fg.subtle"
                    px="1"
                    pt="0.5"
                    _hover={{ color: "fg.muted" }}
                    aria-label={dragAriaLabel}
                >
                    <LuGripVertical size={14} />
                </Box>
                <IconButton
                    aria-label={moveUpAriaLabel}
                    variant="ghost"
                    size="2xs"
                    color="fg.subtle"
                    disabled={!canMoveUp}
                    onClick={onMoveUp}
                >
                    <LuChevronUp />
                </IconButton>
                <IconButton
                    aria-label={moveDownAriaLabel}
                    variant="ghost"
                    size="2xs"
                    color="fg.subtle"
                    disabled={!canMoveDown}
                    onClick={onMoveDown}
                >
                    <LuChevronDown />
                </IconButton>
            </VStack>
            <Flex flex="1" minW={0} direction="column" justify="center">
                {children}
            </Flex>
        </Flex>
    );
}
