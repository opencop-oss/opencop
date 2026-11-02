// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { I18nextProvider } from "react-i18next";
import { i18n } from "~/i18n/config";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import type { ColumnFiltersState } from "@tanstack/react-table";
import { ColumnFilterBuilder } from "./ColumnFilterBuilder";
import type { FilterableColumn } from "./ColumnFilterBuilder";
import type { ActiveColumnFilterValue } from "./column-filter.model";

// ─── Fixtures ─────────────────────────────────────────────────────────────────

const COLS: FilterableColumn[] = [
    { id: "name", filterDef: { label: "Name", type: "string" } },
    {
        id: "status",
        filterDef: {
            label: "Status",
            type: "enum",
            options: [
                { label: "Active", value: "ACTIVE" },
                { label: "Inactive", value: "INACTIVE" },
            ],
        },
    },
];

// ─── Render helpers ───────────────────────────────────────────────────────────

function renderFilterBuilder(initialValue: ColumnFiltersState = []) {
    const onChange = vi.fn();

    function Wrapper() {
        const [value, setValue] = useState<ColumnFiltersState>(initialValue);
        return (
            <I18nextProvider i18n={i18n}>
                <Provider>
                    <ColumnFilterBuilder
                        filterableColumns={COLS}
                        value={value}
                        onChange={(v) => {
                            setValue(v);
                            onChange(v);
                        }}
                    />
                </Provider>
            </I18nextProvider>
        );
    }

    render(<Wrapper />);
    return { onChange };
}

function Provider({ children }: { children: React.ReactNode }) {
    return <OcThemeProvider>{children}</OcThemeProvider>;
}

// ─── Interaction helpers ──────────────────────────────────────────────────────

/** Returns all visible OcSelect combobox triggers inside the popover. */
function selectTriggers() {
    return Array.from(
        document.querySelectorAll<HTMLElement>('[role="combobox"][data-part="trigger"]'),
    );
}

/** Returns all visible options in any open OcSelect dropdown. */
function visibleOptions() {
    return Array.from(document.querySelectorAll<HTMLElement>('[role="option"]'));
}

async function openPopover() {
    await act(async () => {
        fireEvent.click(screen.getByRole("button", { name: /add filter/i }));
    });
}

async function clickSelectTrigger(index: number) {
    await act(async () => {
        fireEvent.click(selectTriggers()[index]);
    });
}

async function clickOption(text: string) {
    const option = visibleOptions().find((o) => o.textContent === text);
    if (!option)
        throw new Error(
            `Option "${text}" not found. Available: ${visibleOptions().map((o) => o.textContent)}`,
        );
    await act(async () => {
        fireEvent.click(option);
    });
}

// ─── Tests ────────────────────────────────────────────────────────────────────

beforeEach(async () => {
    await i18n.changeLanguage("en");
});

describe("ColumnFilterBuilder — happy path: string column, contains operator", () => {
    it("open → column → operator → value → Apply → chip renders", async () => {
        renderFilterBuilder();

        await openPopover();

        // Step 1 — select column "Name"
        await clickSelectTrigger(0);
        await clickOption("Name");

        // Step 2 — select operator "Contains" (default is "Equals"; pick "Contains")
        await clickSelectTrigger(1);
        await clickOption("Contains");

        // Step 3 — enter a value
        const valueInput = screen.getByPlaceholderText("Enter value...");
        await act(async () => {
            fireEvent.change(valueInput, { target: { value: "atlas" } });
        });

        // Apply should now be enabled
        const applyBtn = screen.getByRole("button", { name: /apply/i }) as HTMLButtonElement;
        expect(applyBtn.disabled).toBe(false);

        // Step 4 — apply
        await act(async () => {
            fireEvent.click(applyBtn);
        });

        // Chip should render with label "Name: Contains atlas"
        expect(screen.getByText("Name: Contains atlas")).toBeDefined();
    });
});

describe("ColumnFilterBuilder — chip removal", () => {
    it("clicking chip close removes the chip and calls onChange with empty state", async () => {
        const { onChange } = renderFilterBuilder([
            {
                id: "name",
                value: [
                    { operator: "contains", value: "atlas", _key: "k1" } as ActiveColumnFilterValue,
                ],
            },
        ]);

        // Chip should be visible
        expect(screen.getByText("Name: Contains atlas")).toBeDefined();

        // Close trigger rendered by ChakraTag.CloseTrigger (no data-part, uses class name)
        const closeTrigger = document.querySelector<HTMLElement>(".chakra-tag__closeTrigger");
        expect(closeTrigger).not.toBe(null);

        await act(async () => {
            fireEvent.click(closeTrigger!);
        });

        expect(screen.queryByText("Name: Contains atlas")).toBeNull();
        expect(onChange).toHaveBeenCalledWith([]);
    });
});

describe("ColumnFilterBuilder — NO_VALUE_OPERATORS path (isEmpty)", () => {
    it("isEmpty operator: Apply enabled without entering a value, chip renders correctly", async () => {
        renderFilterBuilder();

        await openPopover();

        // Select column "Name"
        await clickSelectTrigger(0);
        await clickOption("Name");

        // Select "Is Empty" operator — no value input is required
        await clickSelectTrigger(1);
        await clickOption("Is Empty");

        // Value input should be disabled / hidden; Apply should be enabled
        const applyBtn = screen.getByRole("button", { name: /apply/i }) as HTMLButtonElement;
        expect(applyBtn.disabled).toBe(false);

        await act(async () => {
            fireEvent.click(applyBtn);
        });

        expect(screen.getByText("Name: Is Empty")).toBeDefined();
    });
});

describe("ColumnFilterBuilder — enum inList path", () => {
    it("multi-select: both options checked → chip renders all selected labels", async () => {
        renderFilterBuilder();

        await openPopover();

        // Select column "Status"
        await clickSelectTrigger(0);
        await clickOption("Status");

        // Select "In List" operator
        await clickSelectTrigger(1);
        await clickOption("In List");

        // Two checkboxes should appear — check both
        const checkboxes = screen.getAllByRole("checkbox");
        expect(checkboxes.length).toBeGreaterThanOrEqual(2);

        await act(async () => {
            fireEvent.click(checkboxes[0]); // Active
        });
        await act(async () => {
            fireEvent.click(checkboxes[1]); // Inactive
        });

        // Apply should now be enabled
        const applyBtn = screen.getByRole("button", { name: /apply/i }) as HTMLButtonElement;
        expect(applyBtn.disabled).toBe(false);

        await act(async () => {
            fireEvent.click(applyBtn);
        });

        expect(screen.getByText("Status: In List Active, Inactive")).toBeDefined();
    });
});

// ─── Operator narrowing ───────────────────────────────────────────────────────

/** Renders against an arbitrary column set, for exercising `filterDef.operators`. */
function renderWithColumns(columns: FilterableColumn[]) {
    function Wrapper() {
        const [value, setValue] = useState<ColumnFiltersState>([]);
        return (
            <I18nextProvider i18n={i18n}>
                <Provider>
                    <ColumnFilterBuilder
                        filterableColumns={columns}
                        value={value}
                        onChange={setValue}
                    />
                </Provider>
            </I18nextProvider>
        );
    }
    render(<Wrapper />);
}

/** The label of the operator select's current selection. */
function selectedOperatorLabel() {
    return selectTriggers()[1].textContent;
}

/**
 * The operator dropdown's options. The column dropdown stays mounted once opened, so its options
 * are still in the DOM — drop them by label to isolate the operator list.
 */
function operatorOptions(columns: FilterableColumn[]) {
    const columnLabels = new Set(columns.map((c) => c.filterDef.label));
    return visibleOptions()
        .map((o) => o.textContent)
        .filter((label) => label != null && !columnLabels.has(label));
}

describe("ColumnFilterBuilder — operator narrowing via filterDef.operators", () => {
    const NARROWED: FilterableColumn[] = [
        {
            id: "email",
            filterDef: {
                label: "Email",
                type: "string",
                operators: ["contains", "startsWith", "isEmpty"],
            },
        },
    ];

    it("offers only the operators the column declares", async () => {
        renderWithColumns(NARROWED);

        await openPopover();
        await clickSelectTrigger(0);
        await clickOption("Email");
        await clickSelectTrigger(1);

        expect(operatorOptions(NARROWED)).toEqual(["Contains", "Starts With", "Is Empty"]);
    });

    it("offers nothing when a column declares an empty operator list", async () => {
        // Fails closed: an empty whitelist must not fall back to the type's full set, or a
        // server-filtered grid would offer every operator its backend rejects.
        const emptied: FilterableColumn[] = [
            { id: "email", filterDef: { label: "Email", type: "string", operators: [] } },
        ];
        renderWithColumns(emptied);

        await openPopover();
        await clickSelectTrigger(0);
        await clickOption("Email");
        await clickSelectTrigger(1);

        expect(operatorOptions(emptied)).toEqual([]);
    });

    it("preselects the first declared operator rather than the type default", async () => {
        // The string default is "Equals"; this column leads with "Contains".
        renderWithColumns(NARROWED);

        await openPopover();
        await clickSelectTrigger(0);
        await clickOption("Email");

        expect(selectedOperatorLabel()).toContain("Contains");
    });

    it("falls back to the full operator set when the column declares none", async () => {
        // Guards the client-filtered grids, which pass no `operators` at all.
        const unnarrowed: FilterableColumn[] = [
            { id: "name", filterDef: { label: "Name", type: "string" } },
        ];
        renderWithColumns(unnarrowed);

        await openPopover();
        await clickSelectTrigger(0);
        await clickOption("Name");
        await clickSelectTrigger(1);

        expect(operatorOptions(unnarrowed)).toEqual([
            "Contains",
            "Does Not Contain",
            "Equals",
            "Not Equals",
            "Starts With",
            "Is Empty",
            "Is Not Empty",
        ]);
    });
});

// ─── between: both endpoints required ─────────────────────────────────────────

describe("ColumnFilterBuilder — between requires both endpoints", () => {
    const DATE_COLS: FilterableColumn[] = [
        {
            id: "createdAt",
            filterDef: { label: "Created", type: "date", operators: ["before", "between"] },
        },
    ];

    /** The two `type=date` inputs the between branch renders, in [from, to] order. */
    function dateInputs() {
        return Array.from(document.querySelectorAll<HTMLInputElement>('input[type="date"]'));
    }

    async function openBetweenWizard() {
        renderWithColumns(DATE_COLS);
        await openPopover();
        await clickSelectTrigger(0);
        await clickOption("Created");
        await clickSelectTrigger(1);
        await clickOption("Between");
    }

    it("keeps Apply disabled with only the From endpoint filled", async () => {
        await openBetweenWizard();

        await act(async () => {
            fireEvent.change(dateInputs()[0], { target: { value: "2026-01-01" } });
        });

        // Applying here would produce a chip the serializer drops, leaving an applied-looking
        // filter over unfiltered rows.
        const applyBtn = screen.getByRole("button", { name: /apply/i }) as HTMLButtonElement;
        expect(applyBtn.disabled).toBe(true);
    });

    it("enables Apply once both endpoints are filled", async () => {
        await openBetweenWizard();

        await act(async () => {
            fireEvent.change(dateInputs()[0], { target: { value: "2026-01-01" } });
        });
        await act(async () => {
            fireEvent.change(dateInputs()[1], { target: { value: "2026-03-01" } });
        });

        const applyBtn = screen.getByRole("button", { name: /apply/i }) as HTMLButtonElement;
        expect(applyBtn.disabled).toBe(false);
    });
});

// ─── blank values and the criteria cap ────────────────────────────────────────

describe("ColumnFilterBuilder — a chip must produce a real criterion", () => {
    it("keeps Apply disabled for a whitespace-only value", async () => {
        renderFilterBuilder();

        await openPopover();
        await clickSelectTrigger(0);
        await clickOption("Name");

        // The string branch renders a bare Chakra Input with no explicit type attribute.
        const input = Array.from(document.querySelectorAll<HTMLInputElement>("input")).find(
            (el) => !el.type || el.type === "text",
        );
        if (!input) throw new Error("value input not found");
        await act(async () => {
            fireEvent.change(input, { target: { value: "   " } });
        });

        // The serializer trims to empty and drops the criterion, so applying this would render a
        // chip over unfiltered rows.
        const applyBtn = screen.getByRole("button", { name: /apply/i }) as HTMLButtonElement;
        expect(applyBtn.disabled).toBe(true);
    });
});

describe("ColumnFilterBuilder — maxFilters cap", () => {
    /** Two chips already applied on the Name column. */
    const TWO_APPLIED: ColumnFiltersState = [
        {
            id: "name",
            value: [
                { operator: "contains", value: "a", _key: "k1" },
                { operator: "contains", value: "b", _key: "k2" },
            ] as ActiveColumnFilterValue[],
        },
    ];

    function renderCapped(maxFilters: number, initial: ColumnFiltersState) {
        function Wrapper() {
            const [value, setValue] = useState<ColumnFiltersState>(initial);
            return (
                <I18nextProvider i18n={i18n}>
                    <Provider>
                        <ColumnFilterBuilder
                            filterableColumns={COLS}
                            value={value}
                            onChange={setValue}
                            maxFilters={maxFilters}
                        />
                    </Provider>
                </I18nextProvider>
            );
        }
        render(<Wrapper />);
    }

    it("disables Add Filter once the applied chips reach the cap", () => {
        renderCapped(2, TWO_APPLIED);

        // The backend rejects an over-cap request with a 400 that fails the whole page.
        const addBtn = screen.getByRole("button", { name: /add filter/i }) as HTMLButtonElement;
        expect(addBtn.disabled).toBe(true);
    });

    it("counts chips rather than columns, so it stays enabled below the cap", () => {
        renderCapped(3, TWO_APPLIED);

        const addBtn = screen.getByRole("button", { name: /add filter/i }) as HTMLButtonElement;
        expect(addBtn.disabled).toBe(false);
    });
});
