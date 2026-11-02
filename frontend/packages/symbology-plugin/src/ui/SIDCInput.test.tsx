// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, screen, waitFor } from "@testing-library/react";
import { createTestHost } from "@opencop/plugin-sdk/testing";
import symbologyPlugin from "../plugin";
import { fireEvent } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SIDC } from "@opencop/symbology";
import { SIDCInput, type SIDCInputProps } from "./SIDCInput";
import { SidcProvider } from "./SidcContext";

// Mock the Validator namespace to avoid DOM/canvas dependencies in tests.
// The fixture treats specific SIDC prefixes as fixed test fixtures so the
// component-level test doesn't have to know anything about the real lookup
// tables — kind-of-error mapping is the only thing under test here.
vi.mock("@opencop/symbology", async () => {
    const actual = await vi.importActual<typeof import("@opencop/symbology")>("@opencop/symbology");
    const validateSidc = (sidc: string) => {
        if (!/^\d{20}$/.test(sidc) || sidc === "99999999999999999999") {
            return { ok: false, kind: "invalid" as const };
        }
        if (sidc === "00000000000000000000" || sidc.slice(4, 6) === "25") {
            return { ok: false, kind: "unsupported" as const };
        }
        return { ok: true as const };
    };

    return {
        ...actual,
        Validator: { validateSidc },
    };
});

const RENDERABLE_SYMBOL_SIDC = "10031000000000000000";
const ALT_SYMBOL_SIDC = "10033000000000000000";
const GRAPHIC_SIDC = "10002500002421000000";

describe("SIDCInput", () => {
    const mockSetSidc = vi.fn();

    beforeEach(() => {
        vi.clearAllMocks();
    });

    function renderSIDCInput(sidc: string, props: Partial<SIDCInputProps> = {}) {
        return createTestHost({ plugin: symbologyPlugin }).render(
            <SidcProvider defaultValue={SIDC.fromString(sidc)} onChange={mockSetSidc}>
                <SIDCInput {...props} />
            </SidcProvider>,
        );
    }

    function replaceValue(value: string) {
        const input = screen.getByRole("textbox");
        fireEvent.focus(input);
        fireEvent.change(input, { target: { value: "" } });
        fireEvent.change(input, { target: { value } });
        return input as HTMLInputElement;
    }

    describe("Basic functionality", () => {
        it("renders with initial SIDC value", () => {
            renderSIDCInput(RENDERABLE_SYMBOL_SIDC);

            expect((screen.getByRole("textbox") as HTMLInputElement).value).toBe(
                RENDERABLE_SYMBOL_SIDC,
            );
        });

        it("allows only numeric input up to 20 characters", async () => {
            renderSIDCInput(RENDERABLE_SYMBOL_SIDC);

            const input = replaceValue("abc123def456");

            expect(input.value).toBe("123456");
        });

        it("pads input with zeros on blur for valid SIDC", async () => {
            renderSIDCInput(RENDERABLE_SYMBOL_SIDC);

            replaceValue("10031");
            fireEvent.blur(screen.getByRole("textbox"));

            await waitFor(() => {
                expect((screen.getByRole("textbox") as HTMLInputElement).value).toBe(
                    RENDERABLE_SYMBOL_SIDC,
                );
            });
        });

        it("reverts to last valid SIDC on Escape key", async () => {
            renderSIDCInput(RENDERABLE_SYMBOL_SIDC);

            replaceValue("99999");
            fireEvent.keyDown(screen.getByRole("textbox"), { key: "Escape" });

            expect((screen.getByRole("textbox") as HTMLInputElement).value).toBe(
                RENDERABLE_SYMBOL_SIDC,
            );
        });

        it("validates and applies SIDC on Enter key", async () => {
            renderSIDCInput(RENDERABLE_SYMBOL_SIDC);

            replaceValue("10032");
            fireEvent.keyDown(screen.getByRole("textbox"), { key: "Enter" });

            await waitFor(() => {
                expect((screen.getByRole("textbox") as HTMLInputElement).value).toBe(
                    "10032000000000000000",
                );
            });
        });
    });

    describe("Graphic SIDC in Symbol Editor", () => {
        it("rejects graphics without suggesting an unavailable editor", async () => {
            renderSIDCInput(RENDERABLE_SYMBOL_SIDC);

            replaceValue(GRAPHIC_SIDC);
            fireEvent.blur(screen.getByRole("textbox"));

            expect(await screen.findByText("SIDC not supported")).not.toBeNull();
        });

        it("does not offer a clickable switch to the other editor", async () => {
            renderSIDCInput(RENDERABLE_SYMBOL_SIDC);

            replaceValue(GRAPHIC_SIDC);
            fireEvent.blur(screen.getByRole("textbox"));
            await screen.findByText("SIDC not supported");

            expect(screen.queryByRole("button", { name: "SIDC not supported" })).toBeNull();
            expect(screen.queryByRole("link", { name: "SIDC not supported" })).toBeNull();
        });
    });

    describe("Validation errors", () => {
        it("shows unsupported for an unknown SIDC", async () => {
            renderSIDCInput(RENDERABLE_SYMBOL_SIDC);

            replaceValue("00000000000000000000");
            fireEvent.blur(screen.getByRole("textbox"));

            expect(await screen.findByText("SIDC not supported")).not.toBeNull();
        });

        it("preserves the local draft while focused", async () => {
            renderSIDCInput(RENDERABLE_SYMBOL_SIDC);

            const input = replaceValue("99999");

            expect(input.value).toBe("99999");
            expect(screen.queryByText("SIDC not supported")).toBeNull();
        });

        it("clears an existing error once the user starts typing again", async () => {
            renderSIDCInput(RENDERABLE_SYMBOL_SIDC);

            replaceValue("00000000000000000000");
            fireEvent.blur(screen.getByRole("textbox"));
            expect(await screen.findByText("SIDC not supported")).not.toBeNull();

            const input = screen.getByRole("textbox");
            await act(async () => {
                fireEvent.focus(input);
                fireEvent.keyDown(input, { key: "1" });
            });

            expect(screen.queryByText("SIDC not supported")).toBeNull();
        });
    });

    describe("Focus behavior", () => {
        it("preserves the local draft while focused even when the external SIDC changes", async () => {
            const { rerender } = renderSIDCInput(RENDERABLE_SYMBOL_SIDC);

            replaceValue("10032");

            rerender(
                <SidcProvider
                    defaultValue={SIDC.fromString(ALT_SYMBOL_SIDC)}
                    onChange={mockSetSidc}
                >
                    <SIDCInput />
                </SidcProvider>,
            );

            expect((screen.getByRole("textbox") as HTMLInputElement).value).toBe("10032");
        });

        it("adopts external SIDC changes when not focused", async () => {
            const { rerender } = renderSIDCInput(RENDERABLE_SYMBOL_SIDC);

            rerender(
                <SidcProvider
                    defaultValue={SIDC.fromString(ALT_SYMBOL_SIDC)}
                    onChange={mockSetSidc}
                >
                    <SIDCInput />
                </SidcProvider>,
            );

            await waitFor(() => {
                expect((screen.getByRole("textbox") as HTMLInputElement).value).toBe(
                    ALT_SYMBOL_SIDC,
                );
            });
        });
    });
});
