// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { render, screen } from "~/test";
import { DataSourceAccessModeField } from "./DataSourceAccessModeField";

describe("DataSourceAccessModeField", () => {
    it("keeps a TCP Read source on Read, explaining that write access is set at creation", () => {
        render(<DataSourceAccessModeField value="R" transportType="TCP" onChange={() => {}} />);

        const read = screen.getByRole("radio", { name: "Read" });
        expect(read).toBeEnabled();
        expect(read).toBeChecked();
        expect(read).toHaveAccessibleDescription(
            "Ingests data from this source only. Never pushes data to it.",
        );
        const write = screen.getByRole("radio", { name: "Write" });
        expect(write).toBeDisabled();
        expect(write).toHaveAccessibleDescription(
            "Lets you manually push data to this source only. Never ingests data from it. Can't change: write access is set when the source is created.",
        );
        expect(screen.getByRole("radio", { name: "Read & Write" })).toBeDisabled();
        expect(screen.queryByRole("img")).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
    });

    it("keeps a TCP writing source on its stored mode, with no capability warning", () => {
        render(<DataSourceAccessModeField value="RW" transportType="TCP" onChange={() => {}} />);

        const stored = screen.getByRole("radio", { name: "Read & Write" });
        expect(stored).toBeEnabled();
        expect(stored).toBeChecked();
        const read = screen.getByRole("radio", { name: "Read" });
        expect(read).toBeDisabled();
        expect(read).toHaveAccessibleDescription(
            "Ingests data from this source only. Never pushes data to it. Can't change: write access is set when the source is created.",
        );
        expect(screen.getByRole("radio", { name: "Write" })).toBeDisabled();
        expect(screen.queryByRole("img")).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
    });

    it("keeps transport-unsupported choices visible, disabled, and accessibly explained", () => {
        render(<DataSourceAccessModeField value="R" transportType="UDP" onChange={() => {}} />);

        expect(screen.getByRole("radio", { name: "Read" })).toBeEnabled();
        const write = screen.getByRole("radio", { name: "Write" });
        expect(write).toBeDisabled();
        expect(write).toHaveAccessibleDescription(
            "Lets you manually push data to this source only. Never ingests data from it. Write is unavailable because UDP supports Read only.",
        );
        const readWrite = screen.getByRole("radio", { name: "Read & Write" });
        expect(readWrite).toBeDisabled();
        expect(readWrite).toHaveAccessibleDescription(
            "Ingests data from this source, and lets you manually push data to it. Read & Write is unavailable because UDP supports Read only.",
        );
    });

    it("names the transport by its display name in the unavailable reason", () => {
        render(<DataSourceAccessModeField value="R" transportType="KAFKA" onChange={() => {}} />);

        expect(screen.getByRole("radio", { name: "Write" })).toHaveAccessibleDescription(
            "Lets you manually push data to this source only. Never ingests data from it. Write is unavailable because Kafka supports Read only.",
        );
    });

    it("warns about a stored mode its transport doesn't allow", () => {
        render(<DataSourceAccessModeField value="W" transportType="UDP" onChange={() => {}} />);

        expect(screen.getByRole("radio", { name: "Write" })).toBeChecked();
        expect(screen.getByRole("img", { name: "Write is incompatible with UDP." })).toBeVisible();
    });
});
