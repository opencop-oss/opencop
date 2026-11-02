// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { VStack } from "@chakra-ui/react";
import type { HistoryEntry } from "./history.util";
import { HistoryEntryCard } from "./HistoryEntry";
import { EmptyHistoryState } from "./EmptyHistoryState";

interface HistoryTimelineProps {
    entries: HistoryEntry[];
}

export function HistoryTimeline({ entries }: HistoryTimelineProps) {
    return (
        <VStack flex={1} overflowY="auto" align="stretch">
            {entries.length > 0 ? (
                entries.map((entry, index) => (
                    <HistoryEntryCard
                        key={entry.id}
                        entry={entry}
                        isLast={index === entries.length - 1}
                    />
                ))
            ) : (
                <EmptyHistoryState />
            )}
        </VStack>
    );
}
