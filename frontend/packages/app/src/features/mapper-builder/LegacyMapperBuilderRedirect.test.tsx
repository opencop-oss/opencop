// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Route, Routes } from "react-router";
import { describe, expect, it } from "vitest";
import { render, waitFor } from "~/test";
import type { ObservedLocation } from "~/test";
import {
    LEGACY_MAPPER_BUILDER_PATH,
    LEGACY_MAPPERS_PAGE_PATH,
    LegacyMapperBuilderRedirect,
    LegacyMappersPageRedirect,
} from "./LegacyMapperBuilderRedirect";

async function landingFor(initialRoute: string) {
    const locations: ObservedLocation[] = [];
    render(
        <Routes>
            <Route
                path={`${LEGACY_MAPPER_BUILDER_PATH}/new`}
                element={<LegacyMapperBuilderRedirect />}
            />
            <Route
                path={`${LEGACY_MAPPER_BUILDER_PATH}/:id/edit`}
                element={<LegacyMapperBuilderRedirect />}
            />
            <Route
                path={`${LEGACY_MAPPERS_PAGE_PATH}/mappers/new`}
                element={<LegacyMapperBuilderRedirect />}
            />
            <Route
                path={`${LEGACY_MAPPERS_PAGE_PATH}/mappers/:id/edit`}
                element={<LegacyMapperBuilderRedirect />}
            />
            <Route path={`${LEGACY_MAPPERS_PAGE_PATH}/*`} element={<LegacyMappersPageRedirect />} />
            <Route path="*" element={null} />
        </Routes>,
        { initialRoute, onLocationChange: (l) => locations.push(l) },
    );
    await waitFor(() =>
        expect(locations.at(-1)?.pathname).not.toMatch(
            /^\/admin\/(data-sources|mappers-delimiters)/,
        ),
    );
    return locations.at(-1);
}

describe("LegacyMapperBuilderRedirect", () => {
    it("forwards the old new-mapper URL", async () => {
        expect(await landingFor("/admin/data-sources/mappers/new")).toEqual({
            pathname: "/admin/mappers/new",
            search: "",
        });
    });

    it("keeps the query string, so a duplicate link still names its source", async () => {
        expect(await landingFor("/admin/data-sources/mappers/new?from=m1")).toEqual({
            pathname: "/admin/mappers/new",
            search: "?from=m1",
        });
    });

    it("forwards the old edit URL for the same mapper", async () => {
        expect(await landingFor("/admin/data-sources/mappers/m1/edit")).toEqual({
            pathname: "/admin/mappers/m1/edit",
            search: "",
        });
    });
});

describe("old Mappers & Delimiters URLs", () => {
    it("forwards the old page URL, keeping a drawer link's query", async () => {
        expect(await landingFor("/admin/mappers-delimiters?id=m1&type=mapper")).toEqual({
            pathname: "/admin/mappers",
            search: "?id=m1&type=mapper",
        });
    });

    it("forwards the old new-mapper URL, keeping a duplicate's source", async () => {
        expect(await landingFor("/admin/mappers-delimiters/mappers/new?from=m1")).toEqual({
            pathname: "/admin/mappers/new",
            search: "?from=m1",
        });
    });

    it("forwards the old edit URL for the same mapper", async () => {
        expect(await landingFor("/admin/mappers-delimiters/mappers/m1/edit")).toEqual({
            pathname: "/admin/mappers/m1/edit",
            search: "",
        });
    });

    it("drops an old delimiter link's id and type, keeping any other query", async () => {
        expect(await landingFor("/admin/mappers-delimiters?id=d1&type=delimiter&keep=1")).toEqual({
            pathname: "/admin/mappers",
            search: "?keep=1",
        });
    });

    it("lands an old delimiter link on the bare page when nothing else is in its query", async () => {
        expect(await landingFor("/admin/mappers-delimiters?id=d1&type=delimiter")).toEqual({
            pathname: "/admin/mappers",
            search: "",
        });
    });

    it("sends any other old sub-path to the mappers page", async () => {
        expect(await landingFor("/admin/mappers-delimiters/mappers/new/extra")).toEqual({
            pathname: "/admin/mappers",
            search: "",
        });
    });
});
