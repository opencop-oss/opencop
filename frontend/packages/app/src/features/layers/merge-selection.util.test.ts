// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { deriveMergeSelection, getMergeDialogSelection, MERGE_MAX } from "./merge-selection.util";

describe("getMergeDialogSelection", () => {
    it("returns the full selection when it is within the merge limit", () => {
        const layers = Array.from({ length: MERGE_MAX }, (_, index) => `layer-${index + 1}`);

        expect(getMergeDialogSelection(layers)).toEqual({
            layers,
            selectionLimitExceeded: false,
        });
    });

    it("keeps only the first 10 layers when the selection exceeds the merge limit", () => {
        const layers = Array.from({ length: MERGE_MAX + 2 }, (_, index) => `layer-${index + 1}`);

        expect(getMergeDialogSelection(layers)).toEqual({
            layers: layers.slice(0, MERGE_MAX),
            selectionLimitExceeded: true,
        });
    });
});

describe("deriveMergeSelection", () => {
    it("allows merging when every selected row is merge-eligible", () => {
        const rows = [
            { id: "layer-1", name: "Layer 1", canMerge: true },
            { id: "layer-2", name: "Layer 2", canMerge: true },
        ];

        expect(deriveMergeSelection(rows, (row) => row.canMerge)).toEqual({
            selectedMergeLayerItems: [
                { id: "layer-1", name: "Layer 1" },
                { id: "layer-2", name: "Layer 2" },
            ],
            mergeDialogSelection: {
                layers: [
                    { id: "layer-1", name: "Layer 1" },
                    { id: "layer-2", name: "Layer 2" },
                ],
                selectionLimitExceeded: false,
            },
            canMergeSelection: true,
        });
    });

    it("blocks merging when any selected row is not merge-eligible", () => {
        const rows = [
            { id: "layer-1", name: "Layer 1", canMerge: true },
            { id: "group-1", name: "Group 1", canMerge: false },
            { id: "layer-2", name: "Layer 2", canMerge: true },
        ];

        expect(deriveMergeSelection(rows, (row) => row.canMerge)).toEqual({
            selectedMergeLayerItems: [
                { id: "layer-1", name: "Layer 1" },
                { id: "layer-2", name: "Layer 2" },
            ],
            mergeDialogSelection: {
                layers: [
                    { id: "layer-1", name: "Layer 1" },
                    { id: "layer-2", name: "Layer 2" },
                ],
                selectionLimitExceeded: false,
            },
            canMergeSelection: false,
        });
    });
});
