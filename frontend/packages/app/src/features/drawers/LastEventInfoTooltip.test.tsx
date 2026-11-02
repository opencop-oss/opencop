// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "~/test";
import { describe, expect, it, vi } from "vitest";
import { LastEventInfoTooltip } from "./LastEventInfoTooltip";

// The real InfoTooltip mounts its content only on hover/focus, so render it inline for assertions.
vi.mock("@opencop/ui", async (importOriginal) => {
    const { createElement } = await import("react");
    interface InfoTooltipProps {
        content: unknown;
    }
    return {
        ...(await importOriginal<typeof import("@opencop/ui")>()),
        InfoTooltip: (props: InfoTooltipProps) =>
            createElement("div", null, props.content as never),
    };
});

describe("LastEventInfoTooltip", () => {
    it("explains the data source trigger and frequency, not the mapper's", () => {
        render(<LastEventInfoTooltip subject="dataSource" />);

        expect(screen.getByText(/the source receives a message/i)).toBeVisible();
        expect(screen.getByText(/on the connection heartbeat/i)).toBeVisible();
        expect(screen.queryByText(/features it produced are saved/i)).not.toBeInTheDocument();
    });

    it("explains the mapper trigger and frequency, not the data source's", () => {
        render(<LastEventInfoTooltip subject="mapper" />);

        expect(screen.getByText(/features it produced are saved/i)).toBeVisible();
        expect(screen.getByText(/as each batch of data is saved/i)).toBeVisible();
        expect(screen.queryByText(/the source receives a message/i)).not.toBeInTheDocument();
    });

    it("explains the layer trigger and frequency", () => {
        render(<LastEventInfoTooltip subject="layer" />);

        // Delivery and copy are the two the backend added; a layer no longer advances only on views.
        expect(screen.getByText(/data lands on a source it can see/i)).toBeVisible();
        expect(screen.getByText(/clones or merges into it/i)).toBeVisible();
        expect(screen.getByText(/at most once every 15 seconds/i)).toBeVisible();
    });

    it("always includes the blank-state explanation", () => {
        render(<LastEventInfoTooltip subject="layer" />);

        expect(screen.getByText(/blank means nothing has been recorded/i)).toBeVisible();
    });
});
