// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

export const MERGE_MAX = 10;

export interface MergeSelectionRow {
    id: string;
    name: string;
}

export interface MergeDialogSelection<T> {
    layers: T[];
    selectionLimitExceeded: boolean;
}

export interface MergeSelectionState {
    selectedMergeLayerItems: MergeSelectionRow[];
    mergeDialogSelection: MergeDialogSelection<MergeSelectionRow>;
    canMergeSelection: boolean;
}

export function getMergeDialogSelection<T>(layers: readonly T[]): MergeDialogSelection<T> {
    return {
        layers: layers.slice(0, MERGE_MAX),
        selectionLimitExceeded: layers.length > MERGE_MAX,
    };
}

export function deriveMergeSelection<T extends MergeSelectionRow>(
    selectedRows: readonly T[],
    canMergeRow: (row: T) => boolean,
): MergeSelectionState {
    const selectedMergeLayerItems = selectedRows
        .filter(canMergeRow)
        .map(({ id, name }) => ({ id, name }));

    return {
        selectedMergeLayerItems,
        mergeDialogSelection: getMergeDialogSelection(selectedMergeLayerItems),
        canMergeSelection:
            selectedMergeLayerItems.length >= 2 &&
            selectedMergeLayerItems.length === selectedRows.length,
    };
}
