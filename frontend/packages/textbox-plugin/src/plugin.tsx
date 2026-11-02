// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { definePlugin, SDK_API_VERSION, type PluginModule } from "@opencop/contributions/plugin";
import { installTextBox } from "./ui/install-textbox.tsx";
import en from "./locales/en.json";
import fr from "./locales/fr.json";
import tw from "./locales/tw.json";

/**
 * A rectangle carrying operator-authored text. Owns the `textbox` element type;
 * only the wire decode is shared, as there is no mapper contribution kind.
 */
const textBoxPlugin: PluginModule = definePlugin({
    manifest: {
        id: "opencop.textbox",
        name: "Textbox",
        version: "0.1.0",
        apiVersion: SDK_API_VERSION,
        description: "A rectangle that holds operator-authored text, grown to fit it.",
        author: "OpenCOP",
    },

    install(ctx) {
        ctx.registerTranslations({ namespace: "textbox", resources: { en, fr, tw } });
        installTextBox(ctx);
    },
});

export default textBoxPlugin;
