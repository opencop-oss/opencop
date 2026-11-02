// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { FormatConfig, FormatType, MappingRule, RecordSelector } from "@opencop/api";
import { createElement, createContext, use, useState, type ReactNode } from "react";
import { createStore, useStore, type StoreApi } from "zustand";
import type { TreeNode } from "./mapper-builder.parsers";

interface MapperBuilderActions {
    uploadSample: (file: File, content: string, format: FormatType) => void;
    removeSample: () => void;
    configureFormat: (config: FormatConfig) => void;
    parseComplete: (tree: TreeNode[]) => void;
    parseFailed: (error: string) => void;
    selectRecord: (selector: RecordSelector, nodeId: string, matchedCount: number) => void;
    removeRecordSelector: () => void;
    validateRecordSelector: (...args: [valid: true, matchedCount: number] | [valid: false]) => void;
    navigateRecord: (index: number) => void;
    addRule: (rule: MappingRule) => void;
    addRules: (rules: MappingRule[]) => void;
    /** Update a rule by its stable ID */
    updateRule: (id: string, rule: MappingRule) => void;
    /** Remove a rule by its stable ID */
    removeRule: (id: string) => void;
    clearRules: () => void;
    reportRuleErrors: (errors: Record<string, string>) => void;
    reportRuleWarnings: (warnings: Record<string, string>) => void;
    reportErrorSections: (keys: string[]) => void;
    reportWarningSections: (keys: string[]) => void;
    focusRule: (ruleId: string) => void;
    clearFocusRule: () => void;
    focusSection: (sectionKey: string) => void;
    clearFocusSection: () => void;
    /** Restore the record selector after tree parse in edit mode — does not mark store dirty */
    restoreRecord: (selector: RecordSelector, nodeId: string, matchedCount: number) => void;
    /** Atomically load all edit-mode state in one set() call to avoid SourcePanel auto-apply race */
    loadForEdit: (
        file: File,
        content: string,
        format: FormatType,
        formatConfig: FormatConfig,
        recordSelector: RecordSelector,
        rules: MappingRule[],
    ) => void;
    reset: () => void;
}

interface MapperBuilderState {
    /** The uploaded sample file */
    sampleFile: File | null;
    /** Raw text content of the sample file */
    sampleContent: string;
    /** Detected format from file extension */
    detectedFormat: FormatType | null;
    /** User-configured format settings */
    formatConfig: FormatConfig | null;

    /** Parsed tree data (used for rendering, validation, automap, and source refs) */
    parsedTree: TreeNode[];
    /** Error message when file parsing fails */
    parseError: string | null;

    /** Record selector (XPath/JSONPath expression for repeating record element) */
    recordSelector: RecordSelector | null;
    /** Tree node ID prefix of the selected record element (used to scope field selection) */
    recordNodeId: string | null;
    /** Number of records matched by the selector */
    matchedRecordCount: number;
    /** Index of the currently viewed record (0-based) */
    currentRecordIndex: number;
    /** Whether the current record selector points to a valid node in the parsed tree */
    recordSelectorValid: boolean;

    /** Mapping rules */
    rules: MappingRule[];
    /** Per-rule validation errors keyed by rule ID */
    ruleErrors: Record<string, string>;
    /** Per-rule preview warnings keyed by rule ID */
    ruleWarnings: Record<string, string>;
    /** Section keys (geometry, symbol, style, …) that have errors in the latest preview */
    errorSectionKeys: string[];
    /** Section keys that have warnings in the latest preview */
    warningSectionKeys: string[];

    /** ID of a rule to scroll-to and expand (set from live preview error links) */
    focusedRuleId: string | null;
    /** Section key to scroll-to (set from live preview error links) */
    focusedSectionKey: string | null;

    /** True after the user has mutated the store (false after loadForEdit/reset, so Cancel in edit mode
     *  without any changes does not trigger the discard-changes confirm dialog) */
    isDirty: boolean;
    /** True only when the user uploads a new sample file; false after loadForEdit/reset.
     *  Used to skip re-compressing + re-sending unchanged payload bytes on PATCH. */
    isSampleFileChanged: boolean;

    actions: MapperBuilderActions;
}

const initialData = {
    sampleFile: null,
    sampleContent: "",
    detectedFormat: null,
    formatConfig: null,
    parsedTree: [],
    parseError: null,
    recordSelector: null,
    recordNodeId: null,
    matchedRecordCount: 0,
    currentRecordIndex: 0,
    recordSelectorValid: true,
    rules: [],
    ruleErrors: {},
    ruleWarnings: {},
    errorSectionKeys: [],
    warningSectionKeys: [],
    focusedRuleId: null,
    focusedSectionKey: null,
    isDirty: false,
    isSampleFileChanged: false,
} as const satisfies Omit<MapperBuilderState, "actions">;

export /** Fields that must be reset whenever the parsed tree is invalidated (new file, format change, edit load). */
const parseResetFields = {
    parsedTree: [],
    parseError: null,
    matchedRecordCount: 0,
    currentRecordIndex: 0,
    ruleErrors: {},
    ruleWarnings: {},
} as const satisfies Partial<Omit<MapperBuilderState, "actions">>;

export function createMapperBuilderStore() {
    return createStore<MapperBuilderState>()((set) => ({
        ...initialData,

        actions: {
            uploadSample: (file, content, format) => {
                set({
                    sampleFile: file,
                    sampleContent: content,
                    detectedFormat: format,
                    // Reset downstream parsing state; preserve rules + record selector
                    formatConfig: null,
                    ...parseResetFields,
                    recordSelectorValid: false,
                    isDirty: true,
                    isSampleFileChanged: true,
                });
            },

            removeSample: () =>
                set({
                    sampleFile: null,
                    sampleContent: "",
                    detectedFormat: null,
                    formatConfig: null,
                    ...parseResetFields,
                    recordSelectorValid: false,
                    isDirty: true,
                }),

            configureFormat: (config) =>
                set({
                    formatConfig: config,
                    parsedTree: [], // Must re-parse with new config
                    parseError: null,
                    isDirty: true,
                }),

            parseComplete: (tree) => set({ parsedTree: tree, parseError: null }),

            parseFailed: (error) => set({ parsedTree: [], parseError: error }),

            selectRecord: (selector, nodeId, matchedCount) =>
                set({
                    recordSelector: selector,
                    recordNodeId: nodeId,
                    matchedRecordCount: matchedCount,
                    currentRecordIndex: 0,
                    recordSelectorValid: true,
                    ruleErrors: {},
                    ruleWarnings: {},
                    isDirty: true,
                }),

            removeRecordSelector: () =>
                set({
                    recordSelector: null,
                    recordNodeId: null,
                    matchedRecordCount: 0,
                    currentRecordIndex: 0,
                    recordSelectorValid: true,
                    ruleErrors: {},
                    ruleWarnings: {},
                    isDirty: true,
                }),

            validateRecordSelector: (...args) => {
                const [valid, matchedCount] = args;
                set({
                    recordSelectorValid: valid,
                    matchedRecordCount: valid ? matchedCount : 0,
                });
            },

            navigateRecord: (index) => set({ currentRecordIndex: index }),

            addRule: (rule) => set((s) => ({ rules: [...s.rules, rule], isDirty: true })),

            addRules: (rules) => set((s) => ({ rules: [...s.rules, ...rules], isDirty: true })),

            updateRule: (id, rule) =>
                set((s) => ({
                    rules: s.rules.map((r) => (r.id === id ? rule : r)),
                    isDirty: true,
                })),

            removeRule: (id) =>
                set((s) => ({ rules: s.rules.filter((r) => r.id !== id), isDirty: true })),

            clearRules: () => set({ rules: [], ruleErrors: {}, ruleWarnings: {}, isDirty: true }),

            reportRuleErrors: (errors) => set({ ruleErrors: errors }),
            reportRuleWarnings: (warnings) => set({ ruleWarnings: warnings }),

            reportErrorSections: (keys) => set({ errorSectionKeys: keys }),
            reportWarningSections: (keys) => set({ warningSectionKeys: keys }),

            focusRule: (ruleId) => set({ focusedRuleId: ruleId }),
            clearFocusRule: () => set({ focusedRuleId: null }),
            focusSection: (sectionKey) => set({ focusedSectionKey: sectionKey }),
            clearFocusSection: () => set({ focusedSectionKey: null }),

            restoreRecord: (selector, nodeId, matchedCount) =>
                set({
                    recordSelector: selector,
                    recordNodeId: nodeId,
                    matchedRecordCount: matchedCount,
                    currentRecordIndex: 0,
                    recordSelectorValid: true,
                    ruleErrors: {},
                    ruleWarnings: {},
                }),

            loadForEdit: (file, content, format, formatConfig, recordSelector, rules) => {
                set({
                    sampleFile: file,
                    sampleContent: content,
                    detectedFormat: format,
                    formatConfig,
                    recordSelector,
                    recordNodeId: null,
                    rules,
                    ...parseResetFields,
                    recordSelectorValid: true,
                    isDirty: false,
                    isSampleFileChanged: false,
                });
            },

            reset: () => set({ ...initialData }),
        },
    }));
}

type MapperBuilderStoreApi = StoreApi<MapperBuilderState>;

const MapperBuilderStoreContext = createContext<MapperBuilderStoreApi | null>(null);

export function MapperBuilderStoreProvider({ children }: { children: ReactNode }) {
    const [store] = useState(createMapperBuilderStore);
    return createElement(MapperBuilderStoreContext, { value: store }, children);
}

export function useMapperBuilderStore<T>(selector: (state: MapperBuilderState) => T): T {
    const store = use(MapperBuilderStoreContext);
    if (!store) {
        throw new Error("useMapperBuilderStore must be used within a MapperBuilderStoreProvider");
    }
    return useStore(store, selector);
}

export function useMapperBuilderActions(): MapperBuilderActions {
    return useMapperBuilderStore((s) => s.actions);
}
