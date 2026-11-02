// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Element } from "@opencop/elements";
import type { ToolDescriptor } from "@opencop/tools";
import type { ContributedFilterField } from "@opencop/ui";
import type { PickResponse } from "./pick-response.ts";
import type { ElementMapper } from "./element-mapper.ts";
import type { TranslationContribution } from "./translation.ts";
import type { ContextMenuItemContribution } from "./context-menu.ts";
import type {
    DetailsEditFormContribution,
    DetailsHeaderContribution,
    DetailsInfoSectionContribution,
    DetailsPositionContribution,
} from "./details.ts";

/**
 * The registration surface a **native subject** uses to contribute its
 * concerns — a tool, a mapper, a click on the things it draws — into
 * the app-owned registries. The composition root hands each subject its own
 * context; it mirrors the plugin `install(ctx)` shape, so a subject registers
 * the same way whether it ships in-tree or as a runtime plugin.
 *
 * Native contribution is the default path to plugin exposure: a member lands
 * here first, with a native consumer, and graduates to
 * {@link PluginContributionContext} once its shape is trusted for external use.
 * Tools, element mappers and the details header are on both already — one
 * {@link ToolDescriptor} carries the whole tool contract, and a native subject
 * and a plugin declare the identical {@link ElementMapper}.
 * `registerPickResponse` is native-only for now.
 *
 * What is deliberately NOT here: claiming Escape. Claiming makes its caller
 * name a slot in the runtime's closed order — knowledge a contributor should
 * not need and a plugin could never supply — so the native features that claim
 * the key do so from host code.
 */
export interface ContributionContext {
    /** Contribute a toolbar/interaction tool. */
    registerTool(tool: ToolDescriptor): () => void;
    /**
     * Contribute how one element type is drawn in the scene. Refused when another
     * owner already draws the type.
     */
    registerElementMapper<E extends Element = Element, S = undefined>(
        mapper: ElementMapper<E, S>,
    ): () => void;
    /**
     * Contribute what a primary click does on the objects of a store this
     * subject draws, keyed by the store's id — behaviour only, no hit
     * test and no rank. The runtime offers it a click only while nothing
     * owns the pointer and no element sits under it. Returns the disposer.
     */
    registerPickResponse(id: string, response: PickResponse): () => void;
    /** Contribute how the details panel's header presents some kinds of element. */
    registerDetailsHeader(contribution: DetailsHeaderContribution): () => void;
    /** Contribute the position the details panel shows for some kinds of element. */
    registerDetailsPosition(contribution: DetailsPositionContribution): () => void;
    /** Contribute inputs to the details panel's edit form for some kinds of element. */
    registerDetailsEditForm(contribution: DetailsEditFormContribution): () => void;
    /** Contribute a section to the details panel's Information tab for some kinds of element. */
    registerDetailsInfoSection(contribution: DetailsInfoSectionContribution): () => void;
    /**
     * Contribute a field filter rules can be about. The host validates it and
     * contains its members; see {@link PluginContributionContext.registerFilterField}.
     */
    registerFilterField(field: ContributedFilterField): () => void;
    /** Contribute an item to the map's right-click menu. */
    registerContextMenuItem(item: ContextMenuItemContribution): () => void;
}

/**
 * The shape of an identity a plugin owns: `"<pluginId>:<name>"`. The type catches
 * a missing namespace at compile time; the host checks at load that the prefix is
 * the registering plugin's own id, which a type cannot know.
 */
export type PluginScopedId = `${string}:${string}`;

/**
 * The register methods a **runtime plugin** gets: the plugin-facing subset of
 * {@link ContributionContext}, plus translations. The plugin protocol (`/plugin`)
 * composes it with the plugin's host services into `PluginInstallContext`.
 *
 * Each method takes the same contract as its native twin (a test pins this), so a
 * contribution is declared the same way in-tree or as a plugin. Two differences
 * are deliberate: the methods return nothing, because the plugin manager records
 * every registration and removes it on unload; and a plugin's filter-field id
 * must be in its own namespace.
 *
 * Registrations made during `install` are **buffered**, then validated as a set
 * before any of them take effect, so a plugin that throws (or contributes one
 * bad item) registers nothing at all.
 */
export interface PluginContributionContext {
    /**
     * Contribute a toolbar tool. A draw tool's kit carries its own grammar — a
     * built-in one from `@opencop/tools`, one built with a factory there, or one
     * the plugin implements. The host contains the grammar's `bind`, the
     * listeners it binds and its `hints`: a throw costs the tool's draw, never
     * the map.
     */
    registerTool(tool: ToolDescriptor): void;
    /**
     * Contribute how one element type is drawn in the scene — the same
     * {@link ElementMapper} a native subject registers. One mapper per type: a
     * type another owner draws is refused, and the plugin load rolls back. The
     * host contains every member — a throw draws, previews or selects nothing —
     * and hands the selector only {@link MapperState}, never the rest of its state.
     */
    registerElementMapper<E extends Element = Element, S = undefined>(
        mapper: ElementMapper<E, S>,
    ): void;
    /** Contribute an i18next namespace the plugin owns. */
    registerTranslations(translations: TranslationContribution): void;
    /**
     * Contribute how the event details panel's header presents some element
     * kinds: the glyph shown when the element has no thumbnail, and the icon
     * box's frame. One registration per kind across all owners; a kind another
     * owner holds is refused. The glyph is bound to this plugin's context and
     * contained: one that throws shows nothing in its place.
     */
    registerDetailsHeader(contribution: DetailsHeaderContribution): void;
    /**
     * Contribute a field filter rules can be about — what a filter row may address,
     * how its value is edited, how it reads in a summary, and how it is saved. The id is
     * `"<pluginId>:<name>"`; the label and notice keys must resolve (register this
     * plugin's translations first). The path must be one the filter evaluators accept;
     * a field encodes onto the generic predicates, it never adds an evaluator. The host
     * contains each member: see `FilterField` for what a throw from each costs.
     */
    registerFilterField(field: ContributedFilterField & { readonly id: PluginScopedId }): void;
}
