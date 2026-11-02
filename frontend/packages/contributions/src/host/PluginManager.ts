// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Logger from "js-logger";
import type { PluginContext, PluginManifest, PluginModule } from "../plugin/index.ts";
import {
    STOPPING_MANIFEST_RULES,
    validateManifest,
    type ManifestProblem,
    type Problem,
} from "../validation/index.ts";
import {
    createContributionRecorder,
    formatProblem,
    validatePluginContributions,
    type Contributions,
} from "../validation/index.ts";
import { contributionRegistrars, type RegistrarHost } from "./contribution-registrars.ts";
import { createJournal, type Journal } from "./journal.ts";
import { ContributionRefusal } from "./refusal.ts";

const logger = Logger.get("plugin-host");

export type PluginStatus = "active" | "failed" | "version-mismatch";

/** Displayable record for the admin view. */
export interface PluginRecord {
    readonly manifest: PluginManifest;
    readonly status: PluginStatus;
    /** Present when `status` is `failed` / `version-mismatch`. */
    readonly error?: string;
    /**
     * Every problem the manifest and the contributions raised, warnings included —
     * empty for a plugin that loaded clean. `error` is the operator's summary of the
     * errors among them.
     */
    readonly problems: readonly (Problem | ManifestProblem)[];
}

/**
 * What the app supplies the manager, split by who consumes it.
 *
 * `createContext` builds the whole {@link PluginContext} for one plugin — the
 * manager calls it and passes the result through, so **the manager never names an
 * individual capability**. Adding one is two edits (declare it on `PluginContext`,
 * supply it here), not five across three packages; and `PluginContext` stops
 * having a second, hand-synced spelling in this file.
 *
 * The register verbs are the other half and stay separate: they are the loader's
 * business, not the plugin's, and are already declared as {@link RegistrarHost}.
 */
export interface PluginHostServices extends RegistrarHost {
    /**
     * Build the context handed to one plugin: its scoped element view, its
     * namespaced storage, and the host hooks it may call. Called once per plugin
     * load, with the plugin's own id so the app can scope what needs scoping.
     */
    createContext(pluginId: string): PluginContext;
    /**
     * The owner ids the app's native plugins hold. A plugin may not take one, so
     * an owner id in a log line or a registry entry names exactly one owner.
     */
    readonly reservedOwnerIds: readonly string[];
    /**
     * Whether a translation key resolves in the host's i18n instance, for the rules
     * that check a contribution's keys. The plugin's own bundles are counted on top.
     */
    translationExists(key: string): boolean;
    /**
     * Every record, after each load and unload — for an admin view to mirror. The
     * manager keeps no state outside the instance, so one constructed in a test shares
     * nothing with the app's.
     */
    onRecordsChange?(records: readonly PluginRecord[]): void;
}

interface ManagedPlugin {
    manifest: PluginManifest;
    status: PluginStatus;
    error?: string;
    problems: readonly (Problem | ManifestProblem)[];
    /** Undoes everything this plugin registered. */
    journal: Journal;
    module?: PluginModule;
}

/** A problem as the operator reads it: a manifest's by its message, a contribution's by kind and message. */
function summarize(problem: Problem | ManifestProblem): string {
    return problem.kind === "manifest" ? problem.message : formatProblem(problem);
}

function isPluginModule(value: unknown): value is PluginModule {
    if (typeof value !== "object" || value === null) return false;
    const module = value as Record<string, unknown>;
    // `typeof null === "object"`, so null-check both — otherwise a `manifest: null`
    // slips through here and throws later in validateManifest (`manifest.id`).
    return (
        typeof module.manifest === "object" &&
        module.manifest !== null &&
        typeof module.install === "function"
    );
}

/**
 * The single install path for plugins. Loads, validates (atomically), registers
 * contributions, drives the lifecycle, and isolates runtime failures so a bad
 * plugin degrades rather than white-screening the app.
 */
export class PluginManager {
    private readonly plugins = new Map<string, ManagedPlugin>();
    private readonly services: PluginHostServices;

    constructor(services: PluginHostServices) {
        this.services = services;
    }

    /**
     * Install → validate → register a plugin module. All-or-nothing: `install`
     * only records what the plugin contributes, so a throw or any validation miss
     * leaves nothing registered and marks the plugin failed.
     */
    load(module: unknown): PluginRecord {
        if (!isPluginModule(module)) {
            return this.fail(
                { id: "<unknown>", name: "<unknown>", version: "0", apiVersion: 0 },
                "failed",
                "invalid plugin: missing manifest/install",
            );
        }

        const { manifest } = module;
        const manifestProblems = validateManifest(manifest, this.services.reservedOwnerIds);
        const stopping = manifestProblems.filter(({ rule }) => STOPPING_MANIFEST_RULES.has(rule));
        if (stopping.length > 0) {
            const mismatch = stopping.every(({ rule }) => rule === "manifest.api-version-mismatch");
            return this.fail(
                manifest,
                mismatch ? "version-mismatch" : "failed",
                manifestProblems.map(({ message }) => message).join("; "),
                manifestProblems,
            );
        }

        const context = this.services.createContext(manifest.id);

        // Run `install` against a recorder rather than the live registries, so the
        // plugin's registrations are buffered. This keeps an imperative install
        // API atomic: a plugin that throws part-way through has registered
        // nothing, so there is no partial state to unwind.
        const recorder = createContributionRecorder(context);
        try {
            module.install(recorder.ctx);
        } catch (err) {
            return this.fail(manifest, "failed", `plugin threw during install: ${String(err)}`);
        }

        const problems = [
            ...manifestProblems,
            ...this.validateContributions(recorder.contributes, manifest.id),
        ];
        const errors = problems.filter(({ severity }) => severity === "error");
        if (errors.length > 0)
            return this.fail(manifest, "failed", errors.map(summarize).join("; "), problems);

        // Idempotent load: a repeat load of the same id cleanly replaces the prior
        // instance (unregistering its contributions) instead of silently stacking a
        // duplicate. Required for hot-reload / re-entitlement, and it keeps a
        // late-resolving load from a superseded run from double-registering.
        //
        // Deferred to here so the manifest, the version gate, `install` and every
        // registrar's validation have all passed first — a plugin that fails any of
        // those never disturbs the running instance.
        //
        // **It is not a full preservation guarantee, and must not be described as
        // one.** "Validated" is not "committed": the `registerContributions` call
        // below is still throw-prone, and a sink that throws there leaves the
        // incumbent already unloaded and the replacement rolled back — so the
        // plugin is gone entirely. Closing that needs the replacement staged until
        // all its contributions commit, with the incumbent restored otherwise,
        // which is tracked as its own piece of work.
        if (this.plugins.has(manifest.id)) this.unload(manifest.id);

        const managed: ManagedPlugin = {
            manifest,
            status: "active",
            problems,
            journal: createJournal(),
            module,
        };

        // Committing the validated set is the last throw-prone step. Isolate any
        // failure: roll back whatever registered and mark this plugin failed, so
        // one bad plugin degrades to a `failed` record rather than aborting the
        // load loop or leaving partial state behind.
        try {
            this.registerContributions(managed, recorder.contributes, context);
        } catch (err) {
            this.rollback(managed);
            return this.fail(
                manifest,
                "failed",
                `plugin threw during load: ${String(err)}`,
                err instanceof ContributionRefusal ? err.problems : [],
            );
        }

        this.plugins.set(manifest.id, managed);
        this.syncRecords();
        return this.toRecord(managed);
    }

    /**
     * Uninstall the plugin + unregister everything it contributed. Returns what threw —
     * its `uninstall`, or an undo — each also logged, so a caller that ignores it loses
     * nothing.
     */
    unload(id: string): readonly unknown[] {
        const managed = this.plugins.get(id);
        if (!managed) return [];
        const errors: unknown[] = [];
        if (managed.module?.uninstall) {
            try {
                managed.module.uninstall();
            } catch (err) {
                logger.error(`uninstall threw for "${id}":`, err);
                errors.push(err);
            }
        }
        errors.push(...this.rollback(managed));
        this.plugins.delete(id);
        this.syncRecords();
        return errors;
    }

    /** Manifest metadata + status for each loaded plugin (admin view). */
    list(): PluginRecord[] {
        return [...this.plugins.values()].map((managed) => this.toRecord(managed));
    }

    // Per-kind rules live in the shared validators and per-kind registration in the
    // registrars, not here — the manager owns lifecycle + atomicity + isolation and
    // stays agnostic of which contribution kinds exist. Every kind is validated
    // before any registers, keeping a load all-or-nothing, and every error is
    // reported so an operator sees them all at once.
    private validateContributions(
        contributes: Contributions,
        pluginId: string,
    ): readonly Problem[] {
        return validatePluginContributions(contributes, pluginId, {
            translationExists: (key) => this.services.translationExists(key),
        });
    }

    private registerContributions(
        managed: ManagedPlugin,
        contributes: Contributions,
        context: PluginContext,
    ): void {
        // `PluginHostServices extends RegistrarHost`, so the services object IS the
        // registrar host — naming each capability again here would mean re-teaching
        // the manager every sink a registrar might use.
        for (const registrar of contributionRegistrars) {
            const undos = registrar.register(
                managed.manifest.id,
                contributes,
                context,
                this.services,
            );
            for (const undo of undos) managed.journal.add(undo);
        }
    }

    /**
     * Undo the plugin's registrations newest first, each contained, so a disposer
     * that throws neither strands the ones after it nor escapes into `unload`'s
     * caller (the loader's reconcile loop).
     */
    private rollback(managed: ManagedPlugin): readonly unknown[] {
        const errors = managed.journal.rollback();
        for (const error of errors) {
            logger.error(`unregister threw for "${managed.manifest.id}":`, error);
        }
        return errors;
    }

    private fail(
        manifest: PluginManifest,
        status: PluginStatus,
        error: string,
        problems: readonly (Problem | ManifestProblem)[] = [],
    ): PluginRecord {
        const managed: ManagedPlugin = {
            manifest,
            status,
            error,
            problems,
            journal: createJournal(),
        };

        // Never clobber an instance that is already installed and working. A
        // failed *reload* (bad new version, entitlement blip, transient fetch)
        // must leave the running one intact — overwriting its record here would
        // orphan its unregister thunks, stranding its contributions in the
        // registries with nothing able to remove them. The caller still gets the
        // failure; the admin view keeps showing the version that actually works.
        // (A previously *failed* record carries no registrations, so replacing it
        // is safe and keeps the reported error current.)
        if (this.plugins.get(manifest.id)?.status !== "active") {
            this.plugins.set(manifest.id, managed);
            this.syncRecords();
        }

        return this.toRecord(managed);
    }

    /** Mirror the current records into the reactive registry the admin view reads. */
    private syncRecords(): void {
        this.services.onRecordsChange?.(this.list());
    }

    private toRecord(managed: ManagedPlugin): PluginRecord {
        return {
            manifest: managed.manifest,
            status: managed.status,
            error: managed.error,
            problems: managed.problems,
        };
    }
}
