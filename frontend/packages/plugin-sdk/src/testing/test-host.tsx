// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    createElement,
    isValidElement,
    useMemo,
    useSyncExternalStore,
    type ComponentType,
    type ReactElement,
    type ReactNode,
} from "react";
import {
    act,
    render as rtlRender,
    renderHook as rtlRenderHook,
    type RenderHookOptions,
    type RenderHookResult,
    type RenderOptions,
    type RenderResult,
} from "@testing-library/react";
import i18next, { type i18n as I18n } from "i18next";
import { I18nextProvider } from "react-i18next";
import { noop, sortBy } from "lodash";
import { onTestFinished } from "vitest";
import { getCurrentTest } from "vitest/suite";
import { ToolSessionScope, type ToolDescriptor, type ToolSession } from "@opencop/tools";
import {
    addHostFormatters,
    DisplayPreferencesProvider,
    FilterFieldsProvider,
    OcThemeProvider,
    type DisplayPreferences,
    type FilterFields,
} from "@opencop/ui";
import type {
    ContributionContext,
    HostCapabilities,
    TranslationContribution,
} from "@opencop/contributions";
import {
    createContainment,
    describeOrigin,
    nativeOwner,
    pluginOwner,
    toolOrigin,
    type Containment,
    type ContainmentLog,
    type ContributionOwner,
    type OwnerKind,
    type ToolPhase,
} from "@opencop/contributions/containment";
import {
    contributionContextOn,
    createContextMenuRegistry,
    createDetailsRegistry,
    createElementMapperRegistry,
    createFilterFieldRegistry,
    createNativeInstaller,
    createPickResponseRegistry,
    createToolRegistry,
    type HostRegistries,
} from "@opencop/contributions/host";
import { toI18nResources, type Problem } from "@opencop/contributions/validation";

/**
 * A test value for every {@link HostCapabilities} member. Keyed by the declaration, so
 * a capability added there without a test value here does not compile.
 */
const TEST_CAPABILITIES: { readonly [K in keyof HostCapabilities]: () => HostCapabilities[K] } = {
    // The host's own defaults for an operator who has set nothing.
    displayPreferences: (): DisplayPreferences => ({
        measurementSystem: "IMPERIAL",
        coordFormat: "DD",
        setCoordFormat: noop,
        angleUnit: "both",
        setAngleUnit: noop,
        angleOrientation: "ccw",
    }),
    // Replaced, at render, by the fields the plugin registered.
    filterFields: (): FilterFields => ({ fields: [] }),
    // The session an editor sees while its tool is not the one running.
    toolSession: (): ToolSession => ({
        draft: null,
        params: {},
        phase: "idle",
        setParams: noop,
        invalidate: noop,
        activate: noop,
        cancel: noop,
    }),
};

/** Per-render overrides: any capability, in part. */
export type CapabilityOverrides = {
    readonly [K in keyof HostCapabilities]?: Partial<HostCapabilities[K]>;
};

export interface TestHostOptions {
    /** The owner id the contributions are attributed to, as the host would (`opencop.measure`). */
    readonly id: string;
    /**
     * Whether the host installs this owner as a native subject or loads it as a plugin;
     * attribution differs (`native:<id>:…` vs `<id>:…`). Defaults to `native`.
     */
    readonly ownerKind?: OwnerKind;
    /** The plugin's install, exactly as the host calls it. A native subject passes its own. */
    install(ctx: ContributionContext): void | (() => void);
    /** Bundles the test host's i18n instance starts with — the strings the plugin reads. */
    readonly translations?: readonly TranslationContribution[];
    /** Capability values for every render, over the test values. */
    readonly capabilities?: CapabilityOverrides;
    /**
     * The innermost wrapper around everything the test host renders, inside the
     * capabilities: where a context every rendered contribution reads is provided.
     */
    readonly wrap?: (children: ReactNode) => ReactElement;
}

/** What a test host hands the code that installs into it. */
export interface TestHostEnvironment {
    /** The registrations an owner makes, into the test host's own registries. */
    contextFor(owner: ContributionOwner): ContributionContext;
    readonly i18n: I18n;
    /** The log the host's installers write to: a warning fails the test, as a leak. */
    readonly log: ContainmentLog;
    /** Record a failure the test must take, as a contained throw is. */
    fail(origin: string, error: unknown): void;
}

/** What installed into a test host: how to remove it, and what it was refused for. */
export interface TestHostAttachment {
    /** Remove everything; returns what the disposers threw. */
    dispose(): readonly unknown[];
    problems(): readonly Problem[];
}

/** A test host installed by the caller's own path — the host's native installer, or its plugin manager. */
export interface TestHostCoreOptions extends Omit<TestHostOptions, "install"> {
    attach(environment: TestHostEnvironment): TestHostAttachment;
}

/** A throw the test host contained, attributed as the host attributes it. */
export interface ContainedFailure {
    /** `<owner>:<contribution>:<phase>`, the key the host logs. */
    readonly origin: string;
    readonly error: unknown;
}

/** A registry's read side: every member but the ones that write (`register…`, `clear`). */
export type ReadSide<R> = Omit<R, "clear" | `register${string}`>;

/**
 * What the plugin registered, contained as the host contains it: each of the test host's
 * own registries, read through the same members the host's consumers read. Keyed by
 * {@link HostRegistries}, so a registry added there appears here.
 */
export type TestHostContributions = {
    readonly [K in Exclude<keyof HostRegistries, "containment">]: ReadSide<HostRegistries[K]>;
};

function readSide<R extends object>(registry: R): ReadSide<R> {
    return Object.fromEntries(
        Object.entries(registry).filter(
            ([member]) => member !== "clear" && !member.startsWith("register"),
        ),
    ) as ReadSide<R>;
}

/**
 * What each registry holds, by key, for the uninstall check. Keyed by
 * {@link TestHostContributions}, so a registry with no entry here does not compile.
 */
const HELD: {
    readonly [K in keyof TestHostContributions]: (
        registry: TestHostContributions[K],
    ) => readonly string[];
} = {
    tools: (tools) => tools.getAll().map((tool) => tool.id),
    elementMappers: (mappers) => mappers.getEntries().map(({ mapper }) => mapper.type),
    pickResponses: (responses) => responses.entries().map(([id]) => id),
    details: (details) => {
        const { headers, positions, editForms, infoSections } = details.getSnapshot();
        return [
            ...headers.map(({ contribution }) => `header ${contribution.type}`),
            ...positions.map(({ contribution }) => `position ${contribution.type}`),
            ...editForms.map(({ contribution: c }) => `edit form ${c.type}/${c.id}`),
            ...infoSections.map(({ contribution: c }) => `info section ${c.type}/${c.id}`),
        ];
    },
    filterFields: (fields) => fields.getEntries().map(({ field }) => field.id),
    contextMenu: (menu) => menu.getEntries().map(({ item }) => item.id),
};

function heldBy<K extends keyof TestHostContributions>(
    contributions: TestHostContributions,
    kind: K,
): readonly string[] {
    return HELD[kind](contributions[kind]);
}

/** What `contributions` still holds: one line per registry that holds anything. */
export function heldIn(contributions: TestHostContributions): string[] {
    return (Object.keys(HELD) as (keyof TestHostContributions)[]).flatMap((kind) => {
        const held = heldBy(contributions, kind);
        return held.length > 0 ? [`${kind}: ${held.join(", ")}`] : [];
    });
}

export interface TestHostRenderOptions extends Omit<RenderOptions, "wrapper"> {
    readonly capabilities?: CapabilityOverrides;
}

/**
 * A stand-in host a plugin is installed into, for its own tests: the host's rules and
 * containment, its own registries and i18n instance, and a test value for every host
 * capability. A test reaches the plugin's UI through what it registered, contained as
 * the host contains it.
 */
export interface TestHost {
    /** Every rule a registration broke. A refused registration is not in {@link contributions}. */
    readonly problems: readonly Problem[];
    readonly contributions: TestHostContributions;
    /** The capability values a render gets: the test values, with the options' overrides. */
    readonly capabilities: HostCapabilities;
    /**
     * Every throw contained so far. A test that expects one reads it with
     * {@link takeFailures}; any left when the test finishes fail it.
     */
    readonly failures: readonly ContainedFailure[];
    /** The failures so far, cleared — so a test that expects a throw can assert it. */
    takeFailures(): ContainedFailure[];
    readonly i18n: I18n;
    /** Switch the test host's own language; renders that read strings re-render. */
    setLocale(language: string): Promise<void>;
    /**
     * Change capabilities under everything already rendered, as the host does while a
     * contribution is mounted, and bump the tool session's revision — the host's signal
     * that the draft changed in place. With no overrides it is only that signal.
     */
    update(overrides?: CapabilityOverrides): void;
    /** Render `ui` (an element, or a registered component) under every host capability. */
    render(ui: ReactElement | ComponentType, options?: TestHostRenderOptions): RenderResult;
    renderHook<Result, Props>(
        callback: (props: Props) => Result,
        options?: Omit<RenderHookOptions<Props>, "wrapper"> & {
            readonly capabilities?: CapabilityOverrides;
        },
    ): RenderHookResult<Result, Props>;
    /**
     * Run the plugin's own disposer, remove everything it registered, and assert every
     * registry is empty. Any registration after this is a failure: something the plugin
     * started outlived its uninstall. A test that never calls it is torn down when it
     * finishes — a disposer that throws then still fails the test — without the check
     * that every registry is empty.
     */
    uninstall(): void;
}

/**
 * The end-of-test verdict on a test host's contained throws: nothing when the test took
 * them all, otherwise the error that fails it, naming each by its origin.
 */
export function unclaimedFailures(
    id: string,
    failures: readonly ContainedFailure[],
): AggregateError | undefined {
    if (failures.length === 0) return undefined;
    return new AggregateError(
        failures.map(({ error }) => error),
        `${id}: contained ${failures.length === 1 ? "a throw" : "throws"} the test did not take: ${failures
            .map(({ origin }) => origin)
            .join(", ")}`,
    );
}

/**
 * A tool with its components contained under the origins the host's render sites use —
 * the toolbar's icon, the popover's content, the editor outlet — for a test that renders
 * them directly rather than through the host's toolbar.
 */
function containToolComponents(
    containment: Containment,
    owner: ContributionOwner,
    tool: ToolDescriptor,
): ToolDescriptor {
    const contain = <P extends object>(phase: ToolPhase, Component: ComponentType<P>) =>
        containment.containComponent(toolOrigin(owner, tool.id, phase), Component);
    return {
        ...tool,
        icon: contain("icon", tool.icon),
        ...(tool.content ? { content: contain("content", tool.content) } : null),
        ...(tool.editor ? { editor: contain("editor", tool.editor) } : null),
    };
}

function overlay<T extends object>(value: T, override: Partial<T> | undefined): T {
    return override ? { ...value, ...override } : value;
}

/**
 * Install a native subject into a fresh test host, through the host's own native
 * installer. Call it inside a test or a `beforeEach`: a test host belongs to one test,
 * and when that test finishes any failure it contained and the test did not take fails
 * the test.
 */
export function createNativeTestHost(options: TestHostOptions): TestHost {
    const { id, install, ownerKind = "native" } = options;
    const owner = ownerKind === "plugin" ? pluginOwner(id) : nativeOwner(id);
    return createTestHostCore({
        ...options,
        attach: ({ contextFor, log, fail }) => {
            const installer = createNativeInstaller({ log });
            const dispose = installer.install([{ id, install }], () => contextFor(owner));
            for (const record of installer.getRecords())
                if (record.status === "failed" && !record.problems)
                    fail(`${id}:install`, new Error(record.error));
            return {
                dispose,
                problems: () => installer.getRecords().flatMap((record) => record.problems ?? []),
            };
        },
    });
}

/**
 * A fresh test host, installed by `attach`: the host's registries, rules and
 * containment on the test host's own instances. {@link createNativeTestHost} attaches a
 * native subject through the native installer; the plugin kit attaches a plugin through
 * the plugin manager.
 */
export function createTestHostCore(options: TestHostCoreOptions): TestHost {
    // Not `expect.getState().currentTestName`: it still names the last test inside a later `beforeAll`.
    if (getCurrentTest() === undefined)
        throw new Error(
            "createTestHost: call it inside a test or a beforeEach hook — a test host belongs to one test",
        );
    const { id, attach, translations = [], capabilities: defaults, wrap } = options;
    let failures: ContainedFailure[] = [];
    const containment = createContainment({
        // Recorded, not logged: a contained throw is the test's business.
        log: { error: noop, warn: noop },
        onFailure: (origin, error) => failures.push({ origin: describeOrigin(origin), error }),
    });

    const i18n = i18next.createInstance();
    void i18n.init({
        lng: "en",
        fallbackLng: "en",
        resources: toI18nResources(translations),
        initAsync: false,
        interpolation: { escapeValue: false },
    });
    addHostFormatters(i18n);
    const environment = { translationExists: (key: string) => i18n.exists(key) };
    const registries: HostRegistries = {
        containment,
        tools: createToolRegistry({ containment }),
        elementMappers: createElementMapperRegistry({ containment }),
        pickResponses: createPickResponseRegistry(),
        details: createDetailsRegistry({ containment }),
        filterFields: createFilterFieldRegistry({ containment, environment }),
        contextMenu: createContextMenuRegistry({ containment }),
    };
    const { filterFields } = registries;
    const contributions: TestHostContributions = {
        tools: readSide(registries.tools),
        elementMappers: readSide(registries.elementMappers),
        pickResponses: readSide(registries.pickResponses),
        details: readSide(registries.details),
        filterFields: readSide(registries.filterFields),
        contextMenu: readSide(registries.contextMenu),
    };

    let uninstalled = false;

    /**
     * The context `registrant` registers through: the host's own, on the test host's
     * registries — refusing, as a leak, anything registered once uninstalled. A tool's
     * components are contained here, under the origins the host's render sites use,
     * since a test renders them directly rather than through the host's toolbar.
     */
    const contextFor = (registrant: ContributionOwner): ContributionContext => {
        const host = contributionContextOn(registries, registrant);
        const leak = (what: string) => {
            failures.push({
                origin: `${id}:${what}:registered after uninstall`,
                error: new Error(
                    `${id} registered ${what} after uninstall: something it started outlived its disposer`,
                ),
            });
            return noop;
        };
        return {
            registerTool: (tool) =>
                uninstalled
                    ? leak(`tools/${tool.id}`)
                    : host.registerTool(containToolComponents(containment, registrant, tool)),
            registerElementMapper: (mapper) =>
                uninstalled
                    ? leak(`elementMappers/${mapper.type}`)
                    : host.registerElementMapper(mapper),
            registerPickResponse: (key, response) =>
                uninstalled
                    ? leak(`pickResponses/${key}`)
                    : host.registerPickResponse(key, response),
            registerDetailsHeader: (header) =>
                uninstalled
                    ? leak(`detailsHeaders/${header.type}`)
                    : host.registerDetailsHeader(header),
            registerDetailsPosition: (position) =>
                uninstalled
                    ? leak(`detailsPositions/${position.type}`)
                    : host.registerDetailsPosition(position),
            registerDetailsEditForm: (form) =>
                uninstalled
                    ? leak(`detailsEditForms/${form.type}/${form.id}`)
                    : host.registerDetailsEditForm(form),
            registerDetailsInfoSection: (section) =>
                uninstalled
                    ? leak(`detailsInfoSections/${section.type}/${section.id}`)
                    : host.registerDetailsInfoSection(section),
            registerFilterField: (field) =>
                uninstalled ? leak(`filterFields/${field.id}`) : host.registerFilterField(field),
            registerContextMenuItem: (item) =>
                uninstalled
                    ? leak(`contextMenuItems/${item.id}`)
                    : host.registerContextMenuItem(item),
        };
    };

    const attachment = attach({
        contextFor,
        i18n,
        // A refusal reaches `problems` and a failed undo `uninstall()`; a warning is a leak.
        log: {
            error: noop,
            warn: (message) => failures.push({ origin: message, error: new Error(message) }),
        },
        fail: (origin, error) => failures.push({ origin, error }),
    });

    /** Remove what was attached; returns what threw. */
    function teardown(): unknown[] {
        uninstalled = true;
        return [...attachment.dispose()];
    }

    /** Overrides from {@link TestHost.update}, over every render's own; and the session's revision. */
    let live: CapabilityOverrides = {};
    let revision = 0;
    const listeners = new Set<() => void>();
    const subscribe = (listener: () => void) => {
        listeners.add(listener);
        return () => void listeners.delete(listener);
    };
    const getRevision = () => revision;
    const getUpdates = () => live;

    function capabilitiesWith(
        overrides?: CapabilityOverrides,
        entries = filterFields.getEntries(),
        updates = live,
    ): HostCapabilities {
        const fields = sortBy(
            entries.map(({ field }) => field),
            "order",
        );
        const layer = <K extends keyof HostCapabilities>(key: K, base: HostCapabilities[K]) =>
            overlay(overlay(overlay(base, defaults?.[key]), overrides?.[key]), updates[key]);
        // The setters behave as the host's: what they set reaches everything rendered.
        const preferences: DisplayPreferences = {
            ...TEST_CAPABILITIES.displayPreferences(),
            setCoordFormat: (coordFormat) => host.update({ displayPreferences: { coordFormat } }),
            setAngleUnit: (angleUnit) => host.update({ displayPreferences: { angleUnit } }),
        };
        return {
            displayPreferences: layer("displayPreferences", preferences),
            filterFields: layer("filterFields", { ...TEST_CAPABILITIES.filterFields(), fields }),
            toolSession: layer("toolSession", TEST_CAPABILITIES.toolSession()),
        };
    }

    function wrapperFor(overrides?: CapabilityOverrides) {
        return function TestHostProviders({ children }: { children: ReactNode }) {
            const current = useSyncExternalStore(subscribe, getRevision);
            // A field registered after the first render reaches the provider, as the host's does.
            const fields = useSyncExternalStore(filterFields.subscribe, filterFields.getEntries);
            // Memoized as the host's providers are: a contribution's effect keyed on a
            // capability re-runs only when the capability changes.
            const updates = useSyncExternalStore(subscribe, getUpdates);
            const value = useMemo(
                () => capabilitiesWith(overrides, fields, updates),
                [fields, updates],
            );
            return (
                <OcThemeProvider>
                    <I18nextProvider i18n={i18n}>
                        <DisplayPreferencesProvider value={value.displayPreferences}>
                            <FilterFieldsProvider value={value.filterFields}>
                                <ToolSessionScope session={value.toolSession} revision={current}>
                                    {wrap ? wrap(children) : children}
                                </ToolSessionScope>
                            </FilterFieldsProvider>
                        </DisplayPreferencesProvider>
                    </I18nextProvider>
                </OcThemeProvider>
            );
        };
    }

    const host: TestHost = {
        get problems() {
            return attachment.problems();
        },
        contributions,
        get capabilities() {
            return capabilitiesWith();
        },
        get failures() {
            return failures;
        },
        takeFailures() {
            const taken = failures;
            failures = [];
            return taken;
        },
        i18n,
        update(overrides = {}) {
            const next: Record<string, object> = { ...live };
            for (const [key, value] of Object.entries(overrides))
                next[key] = { ...(live[key as keyof HostCapabilities] ?? {}), ...value };
            live = next as CapabilityOverrides;
            revision += 1;
            act(() => listeners.forEach((listener) => listener()));
        },
        setLocale: async (language) => {
            await i18n.changeLanguage(language);
        },
        render(ui, { capabilities: overrides, ...rest } = {}) {
            const element = isValidElement(ui) ? ui : createElement(ui as ComponentType);
            return rtlRender(element, { ...rest, wrapper: wrapperFor(overrides) });
        },
        renderHook(callback, { capabilities: overrides, ...rest } = {}) {
            return rtlRenderHook(callback, { ...rest, wrapper: wrapperFor(overrides) });
        },
        uninstall() {
            if (uninstalled) return;
            const errors = teardown();
            const left = heldIn(contributions);
            if (left.length > 0)
                errors.push(new Error(`left registered after uninstall — ${left.join("; ")}`));
            if (errors.length > 0)
                throw new AggregateError(errors, `${id} did not uninstall cleanly`);
        },
    };

    onTestFinished(() => {
        // Left installed: torn down here, so nothing it registered reaches the next test.
        const errors = uninstalled ? [] : teardown();
        const unclaimed = unclaimedFailures(id, failures);
        if (unclaimed) errors.push(unclaimed);
        if (errors.length > 0)
            throw new AggregateError(errors, `${id} did not finish the test cleanly`);
    });

    return host;
}
