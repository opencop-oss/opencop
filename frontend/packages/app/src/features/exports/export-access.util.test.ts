// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DataSourceRoles, LayerRoles } from "@opencop/api";
import { describe, expect, it } from "vitest";
import { canExportDataSource, canExportLayer, canMergeLayer } from "./export-access.util";

describe("layer access helpers", () => {
    it("allows layer owners and admins", () => {
        expect(canExportLayer(LayerRoles.OWNER, false)).toBe(true);
        expect(canExportLayer(LayerRoles.ADMIN, false)).toBe(true);
        expect(canMergeLayer(LayerRoles.OWNER, false)).toBe(true);
        expect(canMergeLayer(LayerRoles.ADMIN, false)).toBe(true);
    });

    it("blocks layer members and editors unless user is global admin", () => {
        expect(canExportLayer(LayerRoles.MEMBER, false)).toBe(false);
        expect(canExportLayer(LayerRoles.EDITOR, false)).toBe(false);
        expect(canExportLayer(LayerRoles.MEMBER, true)).toBe(true);
        expect(canExportLayer(LayerRoles.EDITOR, true)).toBe(true);
        expect(canMergeLayer(LayerRoles.MEMBER, false)).toBe(false);
        expect(canMergeLayer(LayerRoles.EDITOR, false)).toBe(false);
        expect(canMergeLayer(LayerRoles.MEMBER, true)).toBe(true);
        expect(canMergeLayer(LayerRoles.EDITOR, true)).toBe(true);
    });
});

describe("canExportDataSource", () => {
    it("allows data source owners and admins", () => {
        expect(canExportDataSource(DataSourceRoles.OWNER, false)).toBe(true);
        expect(canExportDataSource(DataSourceRoles.ADMIN, false)).toBe(true);
    });

    it("blocks viewers unless user is global admin", () => {
        expect(canExportDataSource(DataSourceRoles.VIEWER, false)).toBe(false);
        expect(canExportDataSource(DataSourceRoles.VIEWER, true)).toBe(true);
    });
});
