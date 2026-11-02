// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Which tool an edit session is given: the tool that produces the element's
 * type, else the generic edit. The fixture stands in for symbology — its tool and
 * its `symbol` mapper — and the real media-icons subject draws `media-icon`
 * elements and has no tool, so a media icon gets the generic edit by type alone.
 */
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { MediaIcon, Point, Symbol } from "@opencop/elements";
import type { ToolDescriptor } from "@opencop/tools";
import { createPointGrammar } from "@opencop/tools";
import { ELEMENT_STORE_ID } from "@opencop/tools";
import { pluginOwner } from "~/features/contributions/contribution-guard";
import { registerTool } from "~/features/contributions/tool-registry";
import { registerElementMapper } from "~/features/contributions/element-mapper-registry";
import { installNativeSubjects } from "~/features/contributions/native-install";
import { contributionContextFor } from "~/features/contributions/contribution-installer";
import { installMediaIcons } from "~/plugins/media-icons/install-media-icons";
import { getInteractionState } from "./interaction.store";
import { cancel, startEdit } from "./commands";
import { attachHost } from "../host";
import { fakeDeps, installInteractionRuntimeHarness } from "~/test/interaction-runtime-harness";

installInteractionRuntimeHarness();

const FIXTURE_OWNER = pluginOwner("fixture.symbology");

const symbolToolFixture: ToolDescriptor = {
    id: "fixtureSymbol",
    label: "fixture-symbol",
    icon: () => null,
    produces: { store: ELEMENT_STORE_ID, type: "symbol" },
    draw: {
        grammar: createPointGrammar({
            create: (cursor) => Symbol.create({ position: cursor, sidc: "10031000001211000000" }),
        }),
    },
};

function editedDescriptorId(): string | null | undefined {
    const session = getInteractionState().session;
    return session.kind === "edit" ? session.descriptorId : undefined;
}

describe("startEdit — which tool edits an element", () => {
    let unconfigure: () => void;
    let unregister: () => void;

    beforeEach(() => {
        unconfigure = attachHost(fakeDeps().host);
        const disposeTool = registerTool(symbolToolFixture);
        const disposeMapper = registerElementMapper(FIXTURE_OWNER, {
            type: "symbol",
            map: () => [],
        });
        const disposeMedia = installNativeSubjects(
            [{ id: "opencop.media-icons", install: installMediaIcons }],
            contributionContextFor,
        );
        unregister = () => {
            disposeMedia();
            disposeMapper();
            disposeTool();
        };
    });

    afterEach(() => {
        cancel();
        unregister();
        unconfigure();
    });

    it("gives a symbol the tool that edits symbols", () => {
        startEdit(Symbol.create({ position: [0, 0], sidc: "10031000001211000000" }), false);
        expect(editedDescriptorId()).toBe("fixtureSymbol");
    });

    it("gives a media icon the generic edit, not the symbol tool", () => {
        const mediaIcon = MediaIcon.create({
            position: [0, 0],
            href: "opencop:url:/v1/layers/abc/media/def#32x32",
        });
        startEdit(mediaIcon, false);
        expect(editedDescriptorId()).toBeNull();
    });

    it("gives an element whose type no tool produces the generic edit", () => {
        startEdit(Point.create({ position: [0, 0] }), false);
        expect(editedDescriptorId()).toBeNull();
    });
});
