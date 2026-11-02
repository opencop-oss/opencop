// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { I18nextProvider } from "react-i18next";
import { i18n } from "~/i18n/config";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import { OcSwitch } from "./OcSwitch";

// Mirrors an owner that refuses a toggle (e.g. a cancelled/rejected confirmation): `checked`
// never advances, but the component still re-renders in response to the interaction.
function RejectingHarness({ onCheckedChange }: { onCheckedChange: (checked: boolean) => void }) {
    const [, forceRender] = useState(0);
    return (
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <OcSwitch
                    checked={false}
                    label={i18n.t("common.confirm")}
                    onCheckedChange={(next) => {
                        onCheckedChange(next);
                        forceRender((n) => n + 1);
                    }}
                />
            </OcThemeProvider>
        </I18nextProvider>
    );
}

describe("OcSwitch", () => {
    it("forwards its ref to the focusable hidden input", () => {
        const ref = vi.fn<(node: HTMLInputElement | null) => void>();
        render(
            <I18nextProvider i18n={i18n}>
                <OcThemeProvider>
                    <OcSwitch
                        ref={ref}
                        checked={false}
                        label={i18n.t("common.confirm")}
                        onCheckedChange={vi.fn()}
                    />
                </OcThemeProvider>
            </I18nextProvider>,
        );

        expect(ref).toHaveBeenCalledWith(screen.getByRole("switch"));
    });

    it("still reports a second click after the owner rejects the first toggle", async () => {
        const onCheckedChange = vi.fn();
        const user = userEvent.setup();
        render(<RejectingHarness onCheckedChange={onCheckedChange} />);

        const toggle = screen.getByRole("switch");
        await user.click(toggle);
        expect(onCheckedChange).toHaveBeenCalledTimes(1);

        await user.click(toggle);
        expect(onCheckedChange).toHaveBeenCalledTimes(2);
    });
});
