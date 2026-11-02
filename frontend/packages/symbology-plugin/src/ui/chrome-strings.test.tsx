// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { noop } from "lodash";
import { screen } from "@testing-library/react";
import { createTestHost } from "@opencop/plugin-sdk/testing";
import symbologyPlugin from "../plugin";
import { ToolEditorShell } from "./ToolEditorShell";
import { FavoritesTab } from "./FavoritesTab";
import { SidcProvider } from "./SidcContext";

// The test host loads only the plugin's own bundles, so a string still addressed to
// the host catalog renders as its raw key here.
describe("symbology chrome strings resolve from the plugin's own namespace", () => {
    it("labels the editor shell's panel controls", () => {
        createTestHost({ plugin: symbologyPlugin }).render(
            <ToolEditorShell title="Symbol" showPreview={false}>
                <div />
            </ToolEditorShell>,
        );
        expect(screen.getByRole("button", { name: /^(Pin|Unpin) panel$/ })).toBeTruthy();
        expect(screen.getByRole("button", { name: /^(Collapse|Expand) panel$/ })).toBeTruthy();
    });

    it("shows the empty favorites copy", () => {
        createTestHost({ plugin: symbologyPlugin }).render(
            <SidcProvider>
                <FavoritesTab
                    favorites={[]}
                    onRemoveFavorite={noop}
                    historyKeyPrefix="symbology:symbolHistory"
                />
            </SidcProvider>,
        );
        expect(screen.getByText("No favorite symbols yet")).toBeTruthy();
    });
});
