// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { definePlugin, SDK_API_VERSION, type PluginModule } from "@opencop/contributions/plugin";
import { installSymbology } from "./ui/install-symbology.tsx";
import { SYMBOL_FILTER_FIELDS } from "./filter/symbol-fields.util.ts";
import { bindPluginStorage } from "./plugin-storage.ts";
import { migrateLegacyStorage } from "./history-migration.ts";
import { useSymbolHistoryStore } from "./ui/symbol-history.store.ts";
import en from "./locales/en.json";
import fr from "./locales/fr.json";
import tw from "./locales/tw.json";

/**
 * The symbology plugin — MIL-STD point symbols, running entirely
 * on the runtime plugin rails.
 *
 * `install` delegates to `installSymbology`, the same function the app's composition
 * root used to call. It was written against a structural context naming only what it
 * needs (`registerTool`), which `PluginInstallContext` satisfies
 * — so the extraction was a move, not a port.
 *
 * The i18next namespace comes with it: ~92-116KB of catalog labels per language that
 * used to sit in the host's `translation` bundle, now owned and torn down by the
 * plugin that produces them.
 *
 * It imports only `@opencop/*` packages — never host source (`~`) —
 * enforced by lint. That is what makes it *excludable*; it is not yet excluded.
 * The app's static plugin table still imports this module into the single
 * host build, so it ships in `dist/` whether or not an enablement entry names it.
 * An absent enablement stops it installing, not shipping.
 */
const symbologyPlugin: PluginModule = definePlugin({
    manifest: {
        id: "opencop.symbology",
        name: "Symbology",
        version: "0.1.0",
        apiVersion: SDK_API_VERSION,
        description: "MIL-STD point symbols rendered through the runtime plugin rails.",
        author: "OpenCOP",
    },

    install(ctx) {
        ctx.registerTranslations({ namespace: "symbology", resources: { en, fr, tw } });
        installSymbology(ctx);
        for (const field of SYMBOL_FILTER_FIELDS) ctx.registerFilterField(field);

        // Persistence: bind the host-provided storage, then hydrate the persisted
        // stores it backs — they are module-level, so they were created (with
        // hydration skipped) before this context existed. Both steps run
        // synchronously: the storage adapter is synchronous, so rehydrate()
        // completes before the migration below reads the stores.
        bindPluginStorage(ctx.storage);
        void useSymbolHistoryStore.persist.rehydrate();
        // One-release adoption of pre-plugin-storage data; see history-migration.ts.
        migrateLegacyStorage(ctx.storage);
    },
});

export default symbologyPlugin;
