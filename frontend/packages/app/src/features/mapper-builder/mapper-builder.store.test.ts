// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { FormatConfig, MappingRule, RecordSelector } from "@opencop/api";
import { createMapperBuilderStore } from "./mapper-builder.store";

const selector: RecordSelector = {
    source: { type: "xpath", expr: "/tracks/track" },
    onMissing: "NULL",
};
const formatConfig: FormatConfig = { type: "xml", namespaces: {}, strict: false };
const rule: MappingRule = {
    id: "r1",
    target: "DISPLAY_NAME",
    source: { type: "const", value: "test" },
    enabled: true,
    transforms: [],
    onMissing: "NULL",
};

// ---------------------------------------------------------------------------
// loadForEdit
// ---------------------------------------------------------------------------

describe("loadForEdit", () => {
    it("sets all fields and leaves isDirty and isSampleFileChanged false", () => {
        const store = createMapperBuilderStore();
        const file = new File(["<x/>"], "test.xml");
        store.getState().actions.loadForEdit(file, "<x/>", "xml", formatConfig, selector, [rule]);
        const s = store.getState();
        expect(s.sampleFile).toBe(file);
        expect(s.sampleContent).toBe("<x/>");
        expect(s.detectedFormat).toBe("xml");
        expect(s.formatConfig).toEqual(formatConfig);
        expect(s.recordSelector).toEqual(selector);
        expect(s.recordNodeId).toBeNull();
        expect(s.rules).toEqual([rule]);
        expect(s.isDirty).toBe(false);
        expect(s.isSampleFileChanged).toBe(false);
    });

    it("resets isDirty to false even after a prior mutation", () => {
        const store = createMapperBuilderStore();
        store.getState().actions.addRule(rule); // isDirty → true
        expect(store.getState().isDirty).toBe(true);
        const file = new File(["<x/>"], "test.xml");
        store.getState().actions.loadForEdit(file, "<x/>", "xml", formatConfig, selector, []);
        expect(store.getState().isDirty).toBe(false);
    });
});

// ---------------------------------------------------------------------------
// isSampleFileChanged
// ---------------------------------------------------------------------------

describe("isSampleFileChanged", () => {
    it("uploadSample sets isSampleFileChanged true", () => {
        const store = createMapperBuilderStore();
        store.getState().actions.uploadSample(new File(["<x/>"], "x.xml"), "<x/>", "xml");
        expect(store.getState().isSampleFileChanged).toBe(true);
    });

    it("loadForEdit resets isSampleFileChanged to false after uploadSample", () => {
        const store = createMapperBuilderStore();
        store.getState().actions.uploadSample(new File(["<x/>"], "x.xml"), "<x/>", "xml");
        expect(store.getState().isSampleFileChanged).toBe(true);
        store
            .getState()
            .actions.loadForEdit(
                new File(["<y/>"], "y.xml"),
                "<y/>",
                "xml",
                formatConfig,
                selector,
                [],
            );
        expect(store.getState().isSampleFileChanged).toBe(false);
    });

    it("removeSample does not reset isSampleFileChanged", () => {
        const store = createMapperBuilderStore();
        store.getState().actions.uploadSample(new File(["<x/>"], "x.xml"), "<x/>", "xml");
        store.getState().actions.removeSample();
        expect(store.getState().isSampleFileChanged).toBe(true);
    });
});

// ---------------------------------------------------------------------------
// restoreRecord
// ---------------------------------------------------------------------------

describe("restoreRecord", () => {
    it("sets recordNodeId and matchedRecordCount without marking isDirty", () => {
        const store = createMapperBuilderStore();
        const file = new File(["<x/>"], "test.xml");
        store.getState().actions.loadForEdit(file, "<x/>", "xml", formatConfig, selector, []);
        store.getState().actions.restoreRecord(selector, "tracks/track[0]", 5);
        const s = store.getState();
        expect(s.recordNodeId).toBe("tracks/track[0]");
        expect(s.matchedRecordCount).toBe(5);
        expect(s.isDirty).toBe(false);
    });
});
