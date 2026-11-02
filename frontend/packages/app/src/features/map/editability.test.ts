// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, test } from "vitest";
import { getEditability } from "./editability";
import type { MyLayerInfo, MyDataSource } from "@opencop/api";

const layer = (role: MyLayerInfo["role"]): MyLayerInfo[] => [
    { layerId: "layer-1", layerName: "Ops", role, accessScope: "PRIVATE", defaultVisible: true },
];

const ds = (
    role: MyDataSource["role"],
    directRole: MyDataSource["directRole"],
    via: MyDataSource["via"],
): MyDataSource[] => [
    {
        serverId: "ds-1",
        serverName: "TAK Feed",
        role,
        directRole,
        serverState: "CONNECTED",
        via,
        access: "RW",
        transportType: "TCP",
        defaultVisible: true,
    },
];

const dataSource = ds("OWNER", "OWNER", "DIRECT");

describe("getEditability", () => {
    test("local features are fully editable", () => {
        const result = getEditability("local", [], []);
        expect(result).toMatchObject({
            canEditGeometry: true,
            canEditProperties: true,
            canDelete: true,
            canBroadcast: true,
        });
        expect(result.mutationTarget).toBeUndefined();
        expect(result.reason).toBeUndefined();
    });

    test("data source VIEWER cannot edit/delete", () => {
        const result = getEditability("ds-1", [], ds("VIEWER", "VIEWER", "DIRECT"));
        expect(result).toMatchObject({
            canEditGeometry: false,
            canEditProperties: false,
            canDelete: false,
            canBroadcast: false,
        });
        expect(result.reason).toBe("dataSourceReadOnly");
    });

    test.each([
        ["OWNER", "OWNER", "DIRECT"],
        ["ADMIN", "ADMIN", "DIRECT"],
        ["OWNER", "ADMIN", "BOTH"],
    ] as const)(
        "data source effective %s with direct %s via %s can edit properties and delete",
        (role, directRole, via) => {
            const result = getEditability("ds-1", [], ds(role, directRole, via));
            expect(result).toMatchObject({
                canEditGeometry: false,
                canEditProperties: true,
                canDelete: true,
                canBroadcast: false,
                mutationTarget: {
                    kind: "source",
                    id: "ds-1",
                },
            });
            expect(result.reason).toBe("dataSourceOwner");
        },
    );

    test("effective OWNER via BOTH with direct VIEWER can edit/delete", () => {
        const result = getEditability("ds-1", [], ds("OWNER", "VIEWER", "BOTH"));
        expect(result).toMatchObject({
            canEditGeometry: false,
            canEditProperties: true,
            canDelete: true,
            canBroadcast: false,
            mutationTarget: {
                kind: "source",
                id: "ds-1",
            },
        });
        expect(result.reason).toBe("dataSourceOwner");
    });

    test("data source OWNER + INDIRECT can edit/delete", () => {
        const result = getEditability("ds-1", [], ds("OWNER", null, "INDIRECT"));
        expect(result).toMatchObject({
            canEditGeometry: false,
            canEditProperties: true,
            canDelete: true,
            canBroadcast: false,
            mutationTarget: {
                kind: "source",
                id: "ds-1",
            },
        });
        expect(result.reason).toBe("dataSourceOwner");
    });

    test("data source ADMIN + INDIRECT can edit/delete", () => {
        const result = getEditability("ds-1", [], ds("ADMIN", null, "INDIRECT"));
        expect(result).toMatchObject({
            canEditGeometry: false,
            canEditProperties: true,
            canDelete: true,
            canBroadcast: false,
            mutationTarget: {
                kind: "source",
                id: "ds-1",
            },
        });
        expect(result.reason).toBe("dataSourceOwner");
    });

    test.each(["OWNER", "ADMIN", "EDITOR"] as const)("layer %s role can edit", (role) => {
        const result = getEditability("layer-1", layer(role), []);
        expect(result).toMatchObject({
            canEditGeometry: true,
            canEditProperties: true,
            canDelete: true,
            canBroadcast: true,
            mutationTarget: {
                kind: "layer",
                id: "layer-1",
            },
        });
        expect(result.reason).toBeUndefined();
    });

    test("layer MEMBER cannot edit", () => {
        const result = getEditability("layer-1", layer("MEMBER"), []);
        expect(result).toMatchObject({
            canEditGeometry: false,
            canEditProperties: false,
            canDelete: false,
            canBroadcast: false,
        });
        expect(result.mutationTarget).toBeUndefined();
        expect(result.reason).toBe("insufficientLayerRole");
    });

    test("unknown source is not editable", () => {
        const result = getEditability("unknown-id", [], []);
        expect(result.canEditGeometry).toBe(false);
        expect(result.mutationTarget).toBeUndefined();
        expect(result.reason).toBeDefined();
    });

    test("data source check takes priority over layer role", () => {
        // Source matches both a data source and a layer — data source wins
        const layers: MyLayerInfo[] = [
            {
                layerId: "ds-1",
                layerName: "Ops",
                role: "OWNER",
                accessScope: "PRIVATE",
                defaultVisible: true,
            },
        ];
        const result = getEditability("ds-1", layers, dataSource);
        expect(result.canEditGeometry).toBe(false);
        // OWNER+DIRECT data source, so mutate is allowed
        expect(result.mutationTarget).toEqual({
            kind: "source",
            id: "ds-1",
        });
        expect(result.reason).toBe("dataSourceOwner");
    });
});
