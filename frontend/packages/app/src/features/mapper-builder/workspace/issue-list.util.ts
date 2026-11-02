// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/** Group duplicate errors/warnings by code+ruleId+sectionKey, collecting affected record indices */
export interface GroupedIssue {
    code: string;
    message: string;
    ruleId?: string;
    sectionKey?: string;
    recordIndices: number[];
    /** Number of raw issues collapsed into this group */
    count: number;
}

interface EnrichedItem {
    code: string;
    message: string;
    ruleId?: string;
    sectionKey?: string;
    recordIndex?: number;
}

export function groupIssues(items: EnrichedItem[]): GroupedIssue[] {
    const map = new Map<string, GroupedIssue>();
    for (const item of items) {
        const ruleId = item.ruleId ?? "";
        const key = `${item.code}\0${ruleId}\0${item.sectionKey ?? ""}`;
        const existing = map.get(key);
        if (existing) {
            existing.count += 1;
            if (item.recordIndex !== undefined) existing.recordIndices.push(item.recordIndex);
        } else {
            map.set(key, {
                code: item.code,
                message: item.message,
                ruleId: item.ruleId,
                sectionKey: item.sectionKey,
                recordIndices: item.recordIndex !== undefined ? [item.recordIndex] : [],
                count: 1,
            });
        }
    }
    return Array.from(map.values());
}
