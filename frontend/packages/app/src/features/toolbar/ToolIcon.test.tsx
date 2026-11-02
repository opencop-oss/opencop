// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "~/test";
import { describe, expect, it } from "vitest";
import { TbPointFilled } from "react-icons/tb";
import type { ToolDescriptor } from "~/features/contributions/tool-registry";
import { ToolIcon } from "./ToolIcon";

/**
 * A tool's icon is a contributed component like its editor and its popover
 * content — bound to the plugin context at registration, free to call hooks — but
 * it renders inside the toolbar's own row, where a throw unwinds past every other
 * tool. One bad icon must not empty the toolbar.
 */

const descriptor = (over: Partial<ToolDescriptor> = {}): ToolDescriptor =>
    ({ id: "someTool", label: "Some Tool", icon: TbPointFilled, ...over }) as ToolDescriptor;

const SIBLING = "host-sibling";

function Boom(): never {
    throw new Error("icon exploded");
}

describe("ToolIcon", () => {
    it("renders the contributed icon", () => {
        const { container } = render(<ToolIcon descriptor={descriptor()} />);
        expect(container.querySelector("svg")).toBeInTheDocument();
    });

    it("contains a throwing icon and still renders something visible", () => {
        // A fallback glyph rather than nothing: the button keeps its aria-label
        // either way, so an empty one would be a target with no indication
        // anything is wrong.
        const { container } = render(
            <div>
                <span>{SIBLING}</span>
                <ToolIcon descriptor={descriptor({ icon: Boom })} />
            </div>,
        );

        expect(screen.getByText(SIBLING)).toBeInTheDocument();
        expect(container.querySelector("svg")).toBeInTheDocument();
    });
});
