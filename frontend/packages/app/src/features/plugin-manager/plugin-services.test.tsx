// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, fireEvent, render, screen } from "~/test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { TbPointFilled } from "react-icons/tb";
import { Point } from "@opencop/elements";
import { pointGrammar, useToolSession, type ToolParams } from "@opencop/tools";
import { PluginManager } from "@opencop/contributions/host";
import { SDK_API_VERSION, definePlugin, type PluginModule } from "@opencop/contributions/plugin";
import { __resetToolRegistry, getTool } from "~/features/contributions/tool-registry";
import { ToolEditorOutlet } from "~/features/toolbar/ToolEditorOutlet";
import { getInteractionState } from "~/features/map/interaction-runtime/session/interaction.store";
import { resetInteractionForTest } from "~/test/interaction";
import { activate, startEdit } from "~/features/map/interaction-runtime/session/commands";
import { attachHost } from "~/features/map/interaction-runtime/host";
import { fakeHost } from "~/test/interaction-runtime-harness";
import { createPluginHostServices } from "./plugin-services";
import { ELEMENT_STORE_ID } from "@opencop/tools";

/**
 * The plugin **identity boundary**, crossed in production and only in production:
 * the host namespaces a plugin's tool id on registration, so every id that comes
 * back the other way — an existing element's owning tool, an editor's own
 * `activate()` — has to be translated to the id the registry actually holds.
 *
 * These load a real module through {@link PluginManager} with the real host
 * services, because the decoration lives in `createPluginHostServices` and a test
 * registering an undecorated descriptor never reaches it.
 */

const PLUGIN_ID = "acme.identity-fixture";
const LOCAL_TOOL_ID = "fixtureTool";
const NAMESPACED_TOOL_ID = `${PLUGIN_ID}:${LOCAL_TOOL_ID}`;
const ELEMENT_TYPE = "point";
const EDITOR_TEXT = "fixture-editor-body";

// System boundaries only — the map manager. The plugin manager, the host services, the tool registry and the tool
// store are all real, because the seam under test is between them.
vi.mock("~/features/map/MapManagerContext", () => ({
    useMapManager: () => ({ selectElement: vi.fn() }),
}));

const ARM_LABEL = "arm with params";
const ARM_PARAMS: ToolParams = { sidc: "abc" };

/** Arms its own tool from inside its scope, as a plugin editor does. */
function FixtureEditor(): React.ReactElement {
    const { activate } = useToolSession();
    return (
        <div>
            {EDITOR_TEXT}
            <button type="button" onClick={() => activate(ARM_PARAMS)}>
                {ARM_LABEL}
            </button>
        </div>
    );
}

const fixturePlugin: PluginModule = definePlugin({
    manifest: {
        id: PLUGIN_ID,
        name: "Identity Fixture",
        version: "0.0.1",
        apiVersion: SDK_API_VERSION,
    },
    install(ctx) {
        ctx.registerTool({
            id: LOCAL_TOOL_ID,
            label: "Fixture",
            icon: TbPointFilled,
            produces: { store: ELEMENT_STORE_ID, type: ELEMENT_TYPE },
            draw: { grammar: pointGrammar },
            editor: FixtureEditor,
        });
    },
});

let manager: PluginManager;
let unconfigure: () => void;

beforeEach(() => {
    vi.clearAllMocks();
    __resetToolRegistry();
    act(() => resetInteractionForTest());
    // The commands run against an attached host, not React context — give them
    // the harness's fake host at the system boundaries the identity seam does
    // not involve.
    unconfigure = attachHost(fakeHost().host);
    manager = new PluginManager(createPluginHostServices());
    manager.load(fixturePlugin);
});

afterEach(() => {
    manager.unload(PLUGIN_ID);
    unconfigure();
    __resetToolRegistry();
    act(() => resetInteractionForTest());
});

describe("plugin tool identity", () => {
    it("registers the plugin's tool under its namespaced id", () => {
        expect(getTool(NAMESPACED_TOOL_ID)).toBeDefined();
        expect(getTool(LOCAL_TOOL_ID)).toBeUndefined();
    });

    it("owns the edit session under the registered id when editing an existing element of its type", () => {
        const element = Point.create({ position: [0, 0] });

        act(() => void startEdit(element, false));

        // Not `${type}Tool` — that descriptor does not exist, and every consumer
        // of the session's descriptorId does an exact lookup.
        expect(getInteractionState().session).toMatchObject({
            kind: "edit",
            descriptorId: NAMESPACED_TOOL_ID,
        });
    });

    it("mounts the plugin's editor when an existing element of its type is edited", () => {
        const element = Point.create({ position: [0, 0] });

        act(() => void startEdit(element, false));
        render(<ToolEditorOutlet />);

        expect(screen.getByText(EDITOR_TEXT)).toBeInTheDocument();
    });

    it("arms the plugin's own tool, under its namespaced id, from its editor's activate()", () => {
        // Driven from a DRAW session rather than an EDIT one: `activate` refuses
        // over an open edit by design (Save or Cancel first), so an edit session
        // would exercise the gate instead of the scope.
        act(() => void activate(NAMESPACED_TOOL_ID));
        render(<ToolEditorOutlet />);

        // The editor names no tool: its scope already knows which one is its own.
        fireEvent.click(screen.getByRole("button", { name: ARM_LABEL }));

        expect(getInteractionState().session).toMatchObject({
            kind: "draw",
            descriptorId: NAMESPACED_TOOL_ID,
            params: { sidc: "abc" },
        });
    });
});

describe("native owner ids", () => {
    it("refuses a plugin that claims a native plugin's owner id", () => {
        const impostor = definePlugin({
            manifest: {
                id: "opencop.shapes",
                name: "Impostor",
                version: "0.0.1",
                apiVersion: SDK_API_VERSION,
            },
            install: vi.fn(),
        });

        const record = new PluginManager(createPluginHostServices()).load(impostor);

        expect(record.status).toBe("failed");
        expect(record.error).toBe('manifest.id "opencop.shapes" is reserved by a native plugin');
        expect(impostor.install).not.toHaveBeenCalled();
    });
});
