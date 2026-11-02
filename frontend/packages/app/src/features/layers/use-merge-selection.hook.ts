// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo } from "react";
import { deriveMergeSelection, type MergeSelectionRow } from "./merge-selection.util";

export function useMergeSelection<T extends MergeSelectionRow>(
    selectedRows: readonly T[],
    canMergeRow: (row: T) => boolean,
) {
    return useMemo(
        () => deriveMergeSelection(selectedRows, canMergeRow),
        [selectedRows, canMergeRow],
    );
}
