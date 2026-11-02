// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, render, screen } from "~/test";
import { describe, expect, it, vi } from "vitest";
import { Menu, useMenu } from "@chakra-ui/react";
import type { ReactNode } from "react";
import { ElementRow } from "./StackedElementRow";
import type { Element } from "@opencop/elements";

// ElementPreviewCanvas draws to a real canvas — skip it in happy-dom, which has
// no canvas/WebGL context.
vi.mock("./ElementPreviewCanvas", () => ({
    ElementPreviewCanvas: () => null,
}));

vi.mock("~/features/preferences/use-user-timezone.hook", () => ({
    useUserTimezone: () => "UTC",
}));

function makeElement(name: string): Element {
    return {
        key: `test ${name}`,
        objectId: name,
        source: "test",
        getDisplayName: () => name,
        time: null,
    } as unknown as Element;
}

// Theme + i18n come from the harness `render`; this wrapper only supplies the
// Chakra Menu context the row expects to render inside.
function MenuWrapper({ children }: { children: ReactNode }) {
    const menu = useMenu({ open: true });
    return (
        <Menu.RootProvider value={menu}>
            <Menu.Content>{children}</Menu.Content>
        </Menu.RootProvider>
    );
}

async function renderRow() {
    const onSelect = vi.fn();
    const element = makeElement("Bravo");
    await act(async () => {
        render(
            <MenuWrapper>
                <ElementRow element={element} onSelect={onSelect} />
            </MenuWrapper>,
        );
    });
    return { onSelect };
}

describe("ElementRow", () => {
    it("renders the element display name", async () => {
        await renderRow();
        expect(screen.getByText("Bravo")).toBeTruthy();
    });

    it("shows a chevron icon", async () => {
        await renderRow();
        // FaChevronRight renders as an SVG — no checkmark title present
        expect(screen.queryByTitle("checkmark") ?? document.querySelector("svg")).toBeTruthy();
        expect(screen.queryByTitle(/check/i)).toBeNull();
    });
});
