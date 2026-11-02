// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createRoot } from "react-dom/client";
import "./i18n/config";
import "./index.css";
import "@fontsource-variable/inter/index.css";
import "@fontsource-variable/inter/wght-italic.css";
import "@fontsource-variable/inter/wght.css";
import "@fontsource-variable/jetbrains-mono/index.css";
import "@fontsource-variable/jetbrains-mono/wght-italic.css";
import "@fontsource-variable/jetbrains-mono/wght.css";
import Logger from "js-logger";
import { App } from "./App";

declare global {
    interface Window {
        __logger: typeof Logger & {
            default(): void;
            off(): void;
        };
    }
}

const applyLoggerDefaults = Logger.useDefaults.bind(Logger);

window.__logger = {
    ...Logger,
    default: applyLoggerDefaults,
    off: () => Logger.setLevel(Logger.OFF),
};

// Install the console handler in every environment so logging is centralized
// through js-logger rather than raw console calls. In dev we log everything
// (DEBUG); in production we keep the logger active at INFO so permanent
// observability (e.g. the `[timing]` lines) still reaches the console while
// debug-level noise stays out. Adjust a single category at runtime with
// `window.__logger.get("<category>").setLevel(...)`.
applyLoggerDefaults({ defaultLevel: import.meta.env.DEV ? Logger.DEBUG : Logger.INFO });

// No <StrictMode> yet: the map provider stack is not remount-safe (a double
// mount leaves two ElementStore/pipeline instances alive).
createRoot(document.getElementById("root")!).render(<App />);
