// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { ExtractorField } from "@opencop/api";
import { FormProvider, useForm } from "react-hook-form";
import { render, screen, userEvent } from "~/test";
import { DescriptorField } from "./DescriptorField";
import type { ConnectionSetupFormValues } from "./use-connection-setup.hook";

function Harness({ field }: { field: ExtractorField }) {
    // Matches the wizard, which validates fields as they are left.
    const form = useForm<ConnectionSetupFormValues>({ mode: "onBlur" });
    return (
        <FormProvider {...form}>
            <DescriptorField field={field} />
        </FormProvider>
    );
}

describe("DescriptorField", () => {
    it("translates a standard key it knows", () => {
        render(
            <Harness
                field={{
                    name: "host",
                    label: "host",
                    required: true,
                    kind: "TEXT",
                    standardKey: "HOST",
                }}
            />,
        );

        expect(screen.getByLabelText(/^Host/)).toBeVisible();
    });

    it("keeps the backend's label for a standard key newer than this build", () => {
        // Simulates a field newer than this build; not representable by the current ExtractorField union.
        const field = {
            name: "apiKey",
            label: "API key",
            required: true,
            kind: "SECRET",
            standardKey: "API_KEY",
        } as unknown as ExtractorField;

        render(<Harness field={field} />);

        expect(screen.getByLabelText(/^API key/)).toHaveAttribute("type", "password");
    });

    it("says a field kind newer than this build cannot be entered, rather than guessing an input", () => {
        // Simulates a field newer than this build; not representable by the current ExtractorField union.
        const field = {
            name: "region",
            label: "Region",
            required: false,
            kind: "CHOICE",
        } as unknown as ExtractorField;

        render(<Harness field={field} />);

        expect(screen.getByText("Region")).toBeVisible();
        expect(screen.getByText("This field can't be entered here yet.")).toBeVisible();
        expect(screen.queryByRole("textbox")).toBeNull();
    });

    it("rejects a non-numeric port under the input once the field is left", async () => {
        const user = userEvent.setup();
        render(
            <Harness
                field={{
                    name: "port",
                    label: "port",
                    required: true,
                    kind: "TEXT",
                    standardKey: "PORT",
                }}
            />,
        );

        await user.type(screen.getByLabelText(/^Port/), "abc");
        await user.tab();

        expect(
            await screen.findByText("Port must be a whole number from 1 to 65535"),
        ).toBeVisible();
    });

    it("checks the port rule by standard key, whatever the field is named", async () => {
        const user = userEvent.setup();
        render(
            <Harness
                field={{
                    name: "listenPort",
                    label: "Listen port",
                    required: true,
                    kind: "TEXT",
                    standardKey: "PORT",
                }}
            />,
        );

        await user.type(screen.getByLabelText(/^Port/), "70000");
        await user.tab();

        expect(
            await screen.findByText("Port must be a whole number from 1 to 65535"),
        ).toBeVisible();
    });

    it("leaves a text field without the PORT standard key unchecked", async () => {
        const user = userEvent.setup();
        render(<Harness field={{ name: "port", label: "Port", required: true, kind: "TEXT" }} />);

        await user.type(screen.getByLabelText(/^Port/), "abc");
        await user.tab();

        expect(screen.queryByText("Port must be a whole number from 1 to 65535")).toBeNull();
    });
});
