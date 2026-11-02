// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, userEvent, waitFor } from "~/test";
import { withPlugins } from "~/test/plugins";
import { useForm } from "react-hook-form";
import { i18n } from "~/i18n/config";
import {
    FilterRulesBuilder,
    type FilterRulesBuilderHandle,
    type FilterRulesFormValues,
} from "./FilterRulesBuilder";
import {
    defaultPredicateDraft,
    type FilterDraft,
    type Predicate,
    type SubstringInPredicateDraft,
} from "./predicate.model";
import type { FilterField } from "./filter-field.model";

// The SIDC fields come from the symbology plugin, installed the way the app's loader does.
withPlugins(["opencop.symbology"]);

const SIDC = "opencop.symbology:sidc";
const IDENTITY = "opencop.symbology:standardIdentity";
const SYMBOL_SET = "opencop.symbology:symbolSet";

/** A row on a SIDC slice, as the slice's field starts one. */
function sliceRow(
    id: string,
    field: string,
    [start, end]: [number, number],
    values: string[],
): SubstringInPredicateDraft {
    return {
        id,
        field,
        operator: "SUBSTRING_IN",
        value: { value: values, start: String(start), end: String(end) },
    };
}

/** What a map filter offers: no Kind, no custom attributes. */
const offerAllButKind = (field: FilterField) => field.id !== "kind";

// A caller's form is wider than the builder's `{ filter }` slice; the extra field
// proves the builder accepts one.
type HostFormValues = FilterRulesFormValues & { name: string };

const BASE_VALUES: HostFormValues = {
    name: "Test",
    filter: { combinator: "ALL", predicates: [] },
};

// Given a `control`, the builder owns no draft state or default-value seeding:
// the caller's form does. This wrapper stands in for that caller so these tests
// can drive it with just the filter slice they care about.
function TestHost({ defaultFilter }: { defaultFilter: FilterDraft }) {
    const { control } = useForm<HostFormValues>({
        defaultValues: { ...BASE_VALUES, filter: defaultFilter },
    });
    return <FilterRulesBuilder control={control} />;
}

function renderBuilder(defaultFilter: FilterDraft) {
    return render(<TestHost defaultFilter={defaultFilter} />);
}

// The single seeded row a caller's form typically starts a new filter with.
const DEFAULT_ROW: FilterDraft = {
    combinator: "ALL",
    predicates: [defaultPredicateDraft({ field: "displayName", operator: "EQUALS" })],
};

const COMPLETE_FILTER: FilterDraft = {
    combinator: "ALL",
    predicates: [
        {
            id: "p1",
            field: "displayName",
            operator: "EQUALS",
            value: "Test Name",
            negate: true,
        },
    ],
};

const TWO_COMPLETE: FilterDraft = {
    combinator: "ALL",
    predicates: [
        { id: "p1", field: "displayName", operator: "EQUALS", value: "Alpha" },
        { id: "p2", field: SIDC, operator: "EXISTS", value: undefined },
    ],
};

describe("FilterRulesBuilder — collapsed rows", () => {
    afterEach(async () => {
        await act(async () => {
            await i18n.changeLanguage("en");
        });
    });

    it("shows a completed condition collapsed as a single summary line with only a trash button", () => {
        renderBuilder(COMPLETE_FILTER);

        const summaryRow = screen.getByRole("button", { name: "Expand condition" });
        expect(summaryRow).toHaveAccessibleDescription("Display Name is not equal to Test Name");
        expect(screen.getByText("Display Name").tagName).toBe("STRONG");
        expect(screen.getByText("not").tagName).toBe("EM");
        expect(screen.getByText("Test Name").tagName).toBe("CODE");
        expect(screen.getByRole("button", { name: "Remove condition" })).toBeInTheDocument();
        expect(screen.queryByPlaceholderText("Enter a value")).not.toBeInTheDocument();
        expect(screen.queryByRole("checkbox", { name: "Negate" })).not.toBeInTheDocument();
        // The hover hint always exists in the DOM (CSS handles the reveal) —
        // jsdom doesn't compute real :hover state, so this only checks it's there.
        expect(screen.getByText("Click to edit")).toBeInTheDocument();
    });

    it("does not nest the trash button's interactive element inside the expandable row's role=button region", () => {
        renderBuilder(COMPLETE_FILTER);

        const summaryRow = screen.getByRole("button", { name: "Expand condition" });
        const trashButton = screen.getByRole("button", { name: "Remove condition" });

        // A focusable custom role="button" region must be a leaf — nesting a
        // real <button> inside it produces unpredictable screen-reader
        // announcement and tab order. The two must be siblings, not ancestor/descendant.
        expect(summaryRow).not.toContainElement(trashButton);
        expect(trashButton).not.toContainElement(summaryRow);
    });

    it("expands to the full editor when the summary row is clicked, and collapses again once focus leaves it", async () => {
        const user = userEvent.setup();
        renderBuilder(COMPLETE_FILTER);

        await user.click(screen.getByRole("button", { name: "Expand condition" }));

        expect(screen.getByRole("checkbox", { name: "Negate" })).toBeInTheDocument();
        expect(screen.getByRole("checkbox", { name: "Negate" })).toBeChecked();
        expect(screen.getByPlaceholderText("Enter a value")).toBeInTheDocument();

        // Moving focus to something outside the row (Google-Forms-style
        // auto-collapse) — no explicit "collapse" control needed.
        await user.click(screen.getByRole("radio", { name: "All" }));

        expect(
            screen.getByRole("button", { name: "Expand condition" }),
        ).toHaveAccessibleDescription("Display Name is not equal to Test Name");
        expect(screen.queryByRole("checkbox", { name: "Negate" })).not.toBeInTheDocument();
    });

    it.each([{ key: "{Enter}" }, { key: "{ }" }])(
        "expands the summary row via keyboard activation ($key)",
        async ({ key }) => {
            const user = userEvent.setup();
            renderBuilder(COMPLETE_FILTER);

            screen.getByRole("button", { name: "Expand condition" }).focus();
            await user.keyboard(key);

            expect(screen.getByRole("checkbox", { name: "Negate" })).toBeInTheDocument();
            expect(screen.getByPlaceholderText("Enter a value")).toBeInTheDocument();
        },
    );

    it("removes the condition from its collapsed row without expanding it", async () => {
        const user = userEvent.setup();
        renderBuilder(COMPLETE_FILTER);

        await user.click(screen.getByRole("button", { name: "Remove condition" }));

        expect(screen.queryByRole("button", { name: "Expand condition" })).not.toBeInTheDocument();
    });

    it("keeps a newly added condition expanded so it can be filled in", async () => {
        const user = userEvent.setup();
        renderBuilder({ combinator: "ALL", predicates: [] });

        await user.click(screen.getByRole("button", { name: "Add condition" }));

        expect(screen.getByPlaceholderText("Enter a value")).toBeInTheDocument();
    });

    it("scrolls the newly added condition into view", async () => {
        const user = userEvent.setup();
        const scrollIntoView = vi
            .spyOn(Element.prototype, "scrollIntoView")
            .mockImplementation(() => {});

        renderBuilder(TWO_COMPLETE);
        await user.click(screen.getByRole("button", { name: "Add condition" }));

        expect(scrollIntoView).toHaveBeenCalledWith({ block: "nearest" });

        scrollIntoView.mockRestore();
    });

    it("starts with a single, expanded rule by default when no defaultValue is given", () => {
        renderBuilder(DEFAULT_ROW);

        expect(screen.getByPlaceholderText("Enter a value")).toBeInTheDocument();
        expect(screen.getAllByRole("button", { name: "Remove condition" })).toHaveLength(1);
    });

    it("keeps the default row open while typing its value, instead of collapsing on the first character", async () => {
        const user = userEvent.setup();
        renderBuilder(DEFAULT_ROW);

        // Typing a first character makes the predicate summarizable — the row
        // must stay open (it's tracked as expanded) rather than collapsing out
        // from under the input it's being typed into.
        await user.type(screen.getByPlaceholderText("Enter a value"), "a");

        expect(screen.getByPlaceholderText("Enter a value")).toHaveValue("a");
    });

    it("keeps an earlier incomplete row open while typing, even after a later row was added", async () => {
        const user = userEvent.setup();
        renderBuilder(DEFAULT_ROW);

        // Adding a condition moves the tracked "expanded" row to the new,
        // second row — the first row was only still showing as open via its
        // own "incomplete rows force open" fallback, not because it was the
        // tracked expandedId. Typing its first character makes it
        // summarizable, removing that fallback; if the row typed into isn't
        // reclaimed as expandedId at that moment, it collapses out from under
        // the input.
        await user.click(screen.getByRole("button", { name: "Add condition" }));
        const valueInputs = screen.getAllByPlaceholderText("Enter a value");
        expect(valueInputs).toHaveLength(2);

        await user.type(valueInputs[0], "a");

        const afterTyping = screen.getAllByPlaceholderText("Enter a value");
        expect(afterTyping).toHaveLength(2);
        expect(afterTyping[0]).toHaveValue("a");
    });

    it("only keeps one row open at a time", async () => {
        const user = userEvent.setup();
        renderBuilder(TWO_COMPLETE);

        const [firstRow] = screen.getAllByRole("button", { name: "Expand condition" });
        await user.click(firstRow);
        expect(screen.getByPlaceholderText("Enter a value")).toBeInTheDocument();
        expect(
            screen.getByRole("button", { name: "Expand condition" }),
        ).toHaveAccessibleDescription("SIDC exists");

        await user.click(screen.getByRole("button", { name: "Expand condition" }));

        // The first row collapsed back to its summary; the second is now open.
        expect(
            screen.getByRole("button", { name: "Expand condition" }),
        ).toHaveAccessibleDescription("Display Name is equal to Alpha");
        expect(screen.queryByPlaceholderText("Enter a value")).not.toBeInTheDocument();
        expect(screen.getByPlaceholderText("Select a field")).toBeInTheDocument();
    });

    it.each([
        {
            locale: "fr",
            description: "Nom d'affichage n'est pas égal à Test Name",
            field: "Nom d'affichage",
            negation: "n'est pas",
        },
        {
            locale: "tw",
            description: "顯示名稱 不等於 Test Name",
            field: "顯示名稱",
            negation: "不等於",
        },
    ])(
        "keeps semantic emphasis in the $locale preview's translated word order",
        async ({ locale, description, field, negation }) => {
            await act(async () => {
                await i18n.changeLanguage(locale);
            });
            renderBuilder(COMPLETE_FILTER);

            expect(
                screen.getByRole("button", { name: i18n.t("filter.expandCondition") }),
            ).toHaveAccessibleDescription(description);
            expect(screen.getByText(field).tagName).toBe("STRONG");
            expect(screen.getByText(negation).tagName).toBe("EM");
            expect(screen.getByText("Test Name").tagName).toBe("CODE");
        },
    );

    it("gives every substring range and value token the code treatment", () => {
        const substringFilter: FilterDraft = {
            combinator: "ALL",
            predicates: [
                {
                    id: "substring",
                    field: SIDC,
                    operator: "SUBSTRING_IN",
                    value: { value: ["AIR", "SEA"], start: "3", end: "7" },
                },
            ],
        };

        renderBuilder(substringFilter);

        const summaryRow = screen.getByRole("button", { name: "Expand condition" });
        expect(summaryRow).toHaveAccessibleDescription(
            "SIDC contains one of AIR, SEA between characters 3 and 7",
        );
        expect(Array.from(summaryRow.querySelectorAll("code"), (node) => node.textContent)).toEqual(
            ["AIR, SEA", "3", "7"],
        );
    });

    it("stays open after picking an operator from its dropdown, even one needing no value", async () => {
        const user = userEvent.setup();
        renderBuilder(DEFAULT_ROW);

        // The dropdown renders into a Portal outside the row's own DOM
        // subtree — picking an option must not read as a click outside the
        // row. EXISTS in particular needs no value, so it's summarizable the
        // instant it's picked: any bad "click outside" collapse becomes
        // immediately visible here, unlike with an operator still awaiting a
        // value.
        await user.click(screen.getByPlaceholderText("Select an operation"));
        await user.click(await screen.findByText("Exists"));

        expect(screen.getByPlaceholderText("Select a field")).toBeInTheDocument();
    });
});

describe("FilterRulesBuilder — standalone map-filter mode", () => {
    it("returns the parsed filter through its imperative handle", () => {
        const ref = vi.fn<(handle: FilterRulesBuilderHandle | null) => void>();
        render(
            <FilterRulesBuilder
                ref={ref}
                defaultValue={COMPLETE_FILTER}
                offerField={offerAllButKind}
                allowCustomFields={false}
            />,
        );

        const handle = ref.mock.calls.at(-1)?.[0];
        let result: ReturnType<FilterRulesBuilderHandle["attempt"]> | undefined;
        act(() => {
            result = handle?.attempt();
        });

        expect(result).toEqual({
            ok: true,
            result: {
                combine: "ALL",
                predicates: [
                    {
                        path: "properties.opencop.displayName",
                        operation: "EQUALS",
                        values: ["Test Name"],
                        negate: true,
                        caseInsensitive: false,
                    },
                ],
            },
        });
    });

    it("uses the restricted bare-field picker", async () => {
        const user = userEvent.setup();
        render(
            <FilterRulesBuilder
                offerField={offerAllButKind}
                allowCustomFields={false}
                defaultValue={{
                    combinator: "ALL",
                    predicates: [defaultPredicateDraft({ operator: "EQUALS" })],
                }}
            />,
        );

        await user.click(screen.getByPlaceholderText("Select a field"));

        expect(await screen.findByText("Display Name")).toBeInTheDocument();
        expect(screen.queryByText("Kind")).not.toBeInTheDocument();
    });
});

describe("FilterRulesBuilder — symbol-backed fields", () => {
    function renderBareRow() {
        return render(
            <FilterRulesBuilder
                offerField={offerAllButKind}
                allowCustomFields={false}
                defaultValue={{
                    combinator: "ALL",
                    predicates: [defaultPredicateDraft({ operator: "EQUALS" })],
                }}
            />,
        );
    }

    it("offers Standard Identity and Symbol Set alongside the existing fields", async () => {
        const user = userEvent.setup();
        renderBareRow();

        await user.click(screen.getByPlaceholderText("Select a field"));

        expect(await screen.findByText("SIDC: Standard Identity")).toBeInTheDocument();
        expect(screen.getByText("SIDC: Symbol Set")).toBeInTheDocument();
    });

    it("hides the operator and swaps in the value picklist once one is chosen", async () => {
        const user = userEvent.setup();
        renderBareRow();

        await user.click(screen.getByPlaceholderText("Select a field"));
        await user.click(await screen.findByText("SIDC: Standard Identity"));

        // The field implies the operator, so there's nothing to choose and the value is a list.
        expect(screen.queryByPlaceholderText("Select an operation")).not.toBeInTheDocument();
        expect(screen.getByPlaceholderText("Select a value")).toBeInTheDocument();
        expect(screen.queryByPlaceholderText("Enter a value")).not.toBeInTheDocument();
        // Digits, so there's no capitalization to ignore.
        expect(screen.queryByRole("checkbox", { name: "Ignore capitalization" })).toBeNull();
        // Withheld too — the row expands to an OR, and negating that matches almost everything.
        expect(screen.queryByRole("checkbox", { name: "Negate" })).toBeNull();
    });

    it("compiles a picked identity into the 2525D predicate and its 2525C companion", async () => {
        const user = userEvent.setup();
        const ref = vi.fn<(handle: FilterRulesBuilderHandle | null) => void>();
        render(
            <FilterRulesBuilder
                ref={ref}
                offerField={offerAllButKind}
                allowCustomFields={false}
                defaultValue={{
                    combinator: "ALL",
                    predicates: [defaultPredicateDraft({ operator: "EQUALS" })],
                }}
            />,
        );

        await user.click(screen.getByPlaceholderText("Select a field"));
        await user.click(await screen.findByText("SIDC: Standard Identity"));
        await user.click(screen.getByPlaceholderText("Select a value"));
        await user.click(await screen.findByText("Hostile/Faker"));

        const handle = ref.mock.calls.at(-1)?.[0];
        let result: ReturnType<FilterRulesBuilderHandle["attempt"]> | undefined;
        act(() => {
            result = handle?.attempt();
        });

        expect(result).toEqual({
            ok: true,
            result: {
                // Picking a symbol field auto-switches ALL to ANY.
                combine: "ANY",
                predicates: [
                    {
                        path: "properties.opencop.element.symbol",
                        operation: "SUBSTRING_IN",
                        values: ["6"],
                        offset: 3,
                        length: 1,
                        negate: false,
                        caseInsensitive: false,
                    },
                    {
                        path: "properties.opencop.element.symbol",
                        operation: "SUBSTRING_IN",
                        values: ["H", "K"],
                        offset: 1,
                        length: 1,
                        negate: false,
                        caseInsensitive: false,
                    },
                ],
            },
        });
    });

    it("shows the affiliation frame swatch beside each identity option", async () => {
        const user = userEvent.setup();
        renderBareRow();

        await user.click(screen.getByPlaceholderText("Select a field"));
        await user.click(await screen.findByText("SIDC: Standard Identity"));
        await user.click(screen.getByPlaceholderText("Select a value"));

        const hostile = await screen.findByRole("option", { name: /Hostile\/Faker/ });

        // Decorative — the label carries the name.
        const swatch = hostile.querySelector("svg");
        expect(swatch).not.toBeNull();
        expect(swatch?.closest("[aria-hidden='true']")).not.toBeNull();
    });

    it("offers every identity, Pending included", async () => {
        const user = userEvent.setup();
        renderBareRow();

        await user.click(screen.getByPlaceholderText("Select a field"));
        await user.click(await screen.findByText("SIDC: Standard Identity"));
        await user.click(screen.getByPlaceholderText("Select a value"));

        expect(await screen.findByRole("option", { name: /Pending/ })).toBeInTheDocument();
        expect(screen.getAllByRole("option")).toHaveLength(7);
    });

    it("replaces the previous choice rather than accumulating values", async () => {
        const user = userEvent.setup();
        const ref = vi.fn<(handle: FilterRulesBuilderHandle | null) => void>();
        render(
            <FilterRulesBuilder
                ref={ref}
                offerField={offerAllButKind}
                allowCustomFields={false}
                defaultValue={{
                    combinator: "ALL",
                    predicates: [sliceRow("p1", IDENTITY, [3, 4], ["6"])],
                }}
            />,
        );

        await user.click(await screen.findByRole("button", { name: "Expand condition" }));
        await user.click(screen.getByPlaceholderText("Select a value"));
        await user.click(await screen.findByText("Neutral"));

        const handle = ref.mock.calls.at(-1)?.[0];
        let result: ReturnType<FilterRulesBuilderHandle["attempt"]> | undefined;
        act(() => {
            result = handle?.attempt();
        });

        expect(
            (result as { ok: true; result: { predicates: { values: string[] }[] } }).result
                .predicates[0].values,
        ).toEqual(["4"]);
    });

    it("auto-selects Any once a symbol field is chosen, since the row expands to an OR", async () => {
        const user = userEvent.setup();
        renderBareRow();

        expect(screen.getByRole("radio", { name: "All" })).toBeChecked();

        await user.click(screen.getByPlaceholderText("Select a field"));
        await user.click(await screen.findByText("SIDC: Standard Identity"));

        expect(screen.getByRole("radio", { name: "Any" })).toBeChecked();
    });

    it("warns when a symbol condition sits under the All combinator", async () => {
        const user = userEvent.setup();
        renderBareRow();

        await user.click(screen.getByPlaceholderText("Select a field"));
        await user.click(await screen.findByText("SIDC: Standard Identity"));

        // Picking the field already moved the combinator to Any — no warning yet.
        expect(screen.queryByText(/won't match anything/)).toBeNull();

        // Switching back to All surfaces the warning instead of silently re-locking Any.
        await user.click(screen.getByRole("radio", { name: "All" }));
        expect(await screen.findByText(/won't match anything/)).toBeInTheDocument();

        await user.click(screen.getByRole("radio", { name: "Any" }));
        expect(screen.queryByText(/won't match anything/)).toBeNull();
    });

    it("notes the legacy battle-dimension collapse for a Symbol Set condition, regardless of combinator", async () => {
        const user = userEvent.setup();
        renderBareRow();

        expect(screen.queryByText(/also match other symbol sets/)).toBeNull();

        await user.click(screen.getByPlaceholderText("Select a field"));
        await user.click(await screen.findByText("SIDC: Symbol Set"));

        // True under the auto-picked Any, and stays true if forced back to All —
        // this is a standing caveat about the field, not tied to the inert-under-All case.
        expect(await screen.findByText(/also match other symbol sets/)).toBeInTheDocument();
        await user.click(screen.getByRole("radio", { name: "All" }));
        expect(screen.getByText(/also match other symbol sets/)).toBeInTheDocument();
    });

    it("does not show the Symbol Set caveat for a Standard Identity condition", async () => {
        const user = userEvent.setup();
        renderBareRow();

        await user.click(screen.getByPlaceholderText("Select a field"));
        await user.click(await screen.findByText("SIDC: Standard Identity"));

        expect(screen.queryByText(/also match other symbol sets/)).toBeNull();
    });

    it("summarizes a collapsed symbol row by name instead of by character range", async () => {
        const user = userEvent.setup();
        render(
            <FilterRulesBuilder
                offerField={offerAllButKind}
                allowCustomFields={false}
                defaultValue={{
                    combinator: "ALL",
                    predicates: [sliceRow("p1", SYMBOL_SET, [4, 6], ["10"])],
                }}
            />,
        );

        const summaryRow = await screen.findByRole("button", { name: "Expand condition" });

        expect(summaryRow).toHaveTextContent("Symbol Set");
        expect(summaryRow).toHaveTextContent("Land Units");
        expect(summaryRow).not.toHaveTextContent("characters");

        await user.click(summaryRow);
        expect(screen.getByPlaceholderText("Select a value")).toBeInTheDocument();
    });
});

describe("FilterRulesBuilder — uncommitted tag text", () => {
    // Typed-but-uncommitted text lives in the tags input, not in form state, so
    // submitting used to report "a value is required" over a filled-looking
    // field. Blur must commit it.
    it("registers a typed value without pressing Enter", async () => {
        const user = userEvent.setup();
        renderBuilder({
            combinator: "ALL",
            predicates: [
                {
                    id: "substring",
                    field: SIDC,
                    operator: "SUBSTRING_IN",
                    value: { value: [], start: "1", end: "2" },
                },
            ],
        });

        const input = screen.getByPlaceholderText("Add a value");
        await user.type(input, "h");
        await user.tab();

        await waitFor(() => expect(screen.getByText("h")).toBeInTheDocument());
    });
});

describe("FilterRulesBuilder — a condition whose field is unavailable", () => {
    // Saved by a field nobody provides here: a fallback row.
    const ORPHAN: Predicate = {
        path: "properties.opencop.attributes.hull",
        operation: "EQUALS",
        values: ["H1"],
        negate: true,
        caseInsensitive: false,
    };
    const WITH_FALLBACK: FilterDraft = {
        combinator: "ANY",
        predicates: [
            { id: "f1", raw: ORPHAN },
            { id: "p1", field: "displayName", operator: "EQUALS", value: "Alpha" },
        ],
    };

    function renderWithFallback() {
        const ref = vi.fn<(handle: FilterRulesBuilderHandle | null) => void>();
        render(<FilterRulesBuilder ref={ref} defaultValue={WITH_FALLBACK} />);
        return () => ref.mock.calls.at(-1)?.[0];
    }

    it("reads by its path, says why it can't be edited, and offers only delete", () => {
        renderWithFallback();

        expect(screen.getByText("properties.opencop.attributes.hull")).toBeInTheDocument();
        expect(screen.getByText(/its field isn't available/)).toBeInTheDocument();
        // One delete per row; only the editable row can be expanded.
        expect(screen.getAllByRole("button", { name: "Remove condition" })).toHaveLength(2);
        expect(screen.getAllByRole("button", { name: "Expand condition" })).toHaveLength(1);
    });

    it("locks the combinator it was saved with, until the condition is removed", async () => {
        const user = userEvent.setup();
        renderWithFallback();

        expect(screen.getByRole("radio", { name: "All" })).toBeDisabled();
        expect(screen.getByRole("radiogroup")).toHaveAccessibleDescription(
            /locked while a condition's field is unavailable/,
        );

        await user.click(screen.getAllByRole("button", { name: "Remove condition" })[0]!);

        expect(screen.getByRole("radio", { name: "All" })).toBeEnabled();
    });

    it("gives one message, not two that disagree, when a locked All meets a field that needs Any", async () => {
        const user = userEvent.setup();
        render(<FilterRulesBuilder defaultValue={{ ...WITH_FALLBACK, combinator: "ALL" }} />);
        expect(screen.getByRole("radio", { name: "All" })).toBeChecked();

        await user.click(screen.getByRole("button", { name: "Add condition" }));
        const fieldInputs = screen.getAllByPlaceholderText("Select a field");
        await user.click(fieldInputs.at(-1)!);
        await user.click(await screen.findByText("SIDC: Standard Identity"));

        // The combinator stayed locked on All, so the field really does need Any here.
        expect(screen.getByRole("radio", { name: "All" })).toBeChecked();
        expect(screen.getByRole("alert")).toHaveTextContent(
            /remove the unavailable condition to unlock Any/,
        );
        expect(screen.queryByText(/— switch to Any/)).toBeNull();
        expect(screen.queryByText(/Remove that condition to change it/)).toBeNull();
        expect(screen.getByRole("radiogroup")).toHaveAccessibleDescription(
            /remove the unavailable condition to unlock Any/,
        );
    });

    it("saves it back verbatim and in place", () => {
        const handle = renderWithFallback();
        let result: ReturnType<FilterRulesBuilderHandle["attempt"]> | undefined;
        act(() => {
            result = handle()?.attempt();
        });

        expect(result).toEqual({
            ok: true,
            result: {
                combine: "ANY",
                predicates: [
                    ORPHAN,
                    {
                        path: "properties.opencop.displayName",
                        operation: "EQUALS",
                        values: ["Alpha"],
                        negate: false,
                        caseInsensitive: false,
                    },
                ],
            },
        });
    });
});
