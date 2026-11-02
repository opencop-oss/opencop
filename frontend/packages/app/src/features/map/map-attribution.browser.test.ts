// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { v4 as uuidv4 } from "uuid";
import { afterEach, describe, expect, it } from "vitest";
import { parseAttribution } from "./map-attribution.util";

declare global {
    interface Window {
        __attributionPayloadRan?: boolean;
    }
}

// The jsdom/happy-dom suites cannot show that parsing runs nothing and fetches nothing: those DOMs
// do not run handlers the way a browser does, and happy-dom's DOMParser even fetches stylesheets.
// This runs in Chromium.
describe("parseAttribution in a real browser", () => {
    afterEach(() => {
        delete window.__attributionPayloadRan;
    });

    it("runs no script or handler from the GHSA-jrc7-96c5-q579 payload shapes while parsing", async () => {
        const parts = parseAttribution(
            '<details open onload="1" ontoggle="window.__attributionPayloadRan=true">© Example</details>' +
                '<img src="data:," onerror="window.__attributionPayloadRan=true">' +
                '<svg onload="window.__attributionPayloadRan=true"></svg>' +
                "<script>window.__attributionPayloadRan=true</script>",
        );
        // Give any queued toggle, load, or error event a chance to fire.
        await new Promise((resolve) => setTimeout(resolve, 200));

        expect(window.__attributionPayloadRan).toBeUndefined();
        expect(parts).toEqual([{ kind: "text", text: "© Example" }]);
    });

    it("fetches nothing the parsed markup references", async () => {
        const token = `attribution-fetch-${uuidv4()}`;
        const fetched: string[] = [];
        const observer = new PerformanceObserver((list) => {
            for (const entry of list.getEntries()) {
                if (entry.name.includes(token)) fetched.push(new URL(entry.name).pathname);
            }
        });
        observer.observe({ type: "resource" });
        try {
            parseAttribution(
                `<img src="/${token}/img.png"><img srcset="/${token}/srcset.png 1x">` +
                    `<link rel="stylesheet" href="/${token}/style.css">` +
                    `<style>@import "/${token}/import.css";</style>` +
                    `<div style="background: url(/${token}/background.png)">© Example</div>` +
                    `<video poster="/${token}/poster.png"><source src="/${token}/video.mp4"></video>` +
                    `<iframe src="/${token}/frame.html"></iframe><object data="/${token}/object"></object>`,
            );
            // The control proves the observer sees a fetch the live document makes.
            const control = new Image();
            await new Promise((resolve) => {
                control.onload = control.onerror = resolve;
                control.src = `/${token}/control.png`;
            });
            await new Promise((resolve) => setTimeout(resolve, 200));
        } finally {
            observer.disconnect();
        }

        expect(fetched).toEqual([`/${token}/control.png`]);
    });
});
