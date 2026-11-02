// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { execFileSync } from "node:child_process";
import * as fs from "node:fs";
import { describe, expect, it, vi } from "vitest";
import type { ConfigEnv, UserConfig } from "vite";
import viteConfig from "../vite.config";

function uncommentedLines(source: string): string[] {
    return source.split(/\r?\n/).filter((line) => line.trim() && !line.trimStart().startsWith("#"));
}

function withProxyEnvironment<T>(values: Record<string, string>, callback: () => T): T {
    const keys = [
        "VITE_DEV_PROXY_TARGET",
        "VITE_DEV_PROXY_TARGET_CORE",
        "VITE_DEV_PROXY_TARGET_WS",
    ];
    const previous = Object.fromEntries(keys.map((key) => [key, process.env[key]]));
    Object.assign(process.env, values);
    try {
        return callback();
    } finally {
        for (const key of keys) {
            const priorValue = previous[key];
            if (priorValue === undefined) {
                delete process.env[key];
            } else {
                process.env[key] = priorValue;
            }
        }
    }
}

function developmentConfig(env: Record<string, string>): UserConfig {
    return withProxyEnvironment(env, () => {
        const factory = viteConfig as unknown as (config: ConfigEnv) => UserConfig;
        return factory({
            command: "serve",
            isPreview: false,
            isSsrBuild: false,
            mode: "development",
        });
    });
}

describe.sequential("operational configuration sanitization", () => {
    it("preserves the docker wrapper and environment value contracts", () => {
        const dockerBuild = fs.readFileSync("docker-build.sh", "utf8");
        expect(dockerBuild).not.toMatch(
            /opencop-infra|opencop-java|prod(?:uction)?[- ]style|prod compose/i,
        );

        const help = execFileSync("bash", ["docker-build.sh", "--help"], {
            encoding: "utf8",
        });
        expect(help).toContain("backward-compatible default: 0.2.0-SNAPSHOT");
        expect(help).not.toMatch(
            /opencop-infra|opencop-java|prod(?:uction)?[- ]style|prod compose/i,
        );
        for (const name of [
            "OPENCOP_IMAGE_TAG",
            "BUILD_MODE",
            "APP_VERSION",
            "BUILD_NODE_OPTIONS",
            "DOCKER_BUILDX",
            "CI",
        ]) {
            expect(help).toContain(name);
        }
        for (const option of ["--clean", "--no-cache", "--progress=MODE", "--help", "-h"]) {
            expect(help).toContain(option);
        }

        const developmentEnv = fs.readFileSync(".env.development", "utf8");
        expect(uncommentedLines(developmentEnv)).toEqual([
            "VITE_APP_TITLE=OpenCOP",
            "VITE_KEYCLOAK_URL=/auth",
            "VITE_API_BASE_URL=/v1/me",
            "VITE_OPENCOP_CORE_API_URL=/api",
            "VITE_WEBSOCKET_URL=/ws",
            "VITE_DEV_PROXY_TARGET=http://localhost:80",
        ]);
        expect(developmentEnv).not.toMatch(/infra project|same as prod|docker compose/i);

        const localExample = fs.readFileSync(".env.development.local.example", "utf8");
        expect(uncommentedLines(localExample)).toEqual([
            "VITE_DEV_PROXY_TARGET_CORE=http://localhost:8078",
            "VITE_DEV_PROXY_TARGET_WS=http://localhost:8087",
        ]);
        expect(localExample).not.toMatch(/docker compose|azurite|not-Docker|toggle/i);
    });

    it("uses a neutral Vite target and preserves both proxy modes", () => {
        const source = fs.readFileSync("vite.config.ts", "utf8");
        expect(source).toContain("const defaultTarget = env.VITE_DEV_PROXY_TARGET;");
        expect(source).not.toMatch(/Traefik|Java dev|doc\.traefik\.io/i);

        const sharedConfig = developmentConfig({
            VITE_DEV_PROXY_TARGET: "http://shared.example.test",
            VITE_DEV_PROXY_TARGET_CORE: "",
            VITE_DEV_PROXY_TARGET_WS: "",
        });
        expect(sharedConfig.server?.proxy?.["/api"]).toMatchObject({
            target: "http://shared.example.test",
        });
        expect(sharedConfig.server?.proxy?.["/ws"]).toMatchObject({
            target: "http://shared.example.test",
            ws: true,
        });
        expect(sharedConfig.server?.proxy?.["/auth"]).toMatchObject({
            changeOrigin: true,
            target: "http://shared.example.test",
        });
        expect(sharedConfig.server?.proxy?.["/api"]).not.toHaveProperty("rewrite");
        expect(sharedConfig.server?.proxy?.["/api"]).not.toHaveProperty("configure");

        const overrideConfig = developmentConfig({
            VITE_DEV_PROXY_TARGET: "http://shared.example.test",
            VITE_DEV_PROXY_TARGET_CORE: "http://core.example.test",
            VITE_DEV_PROXY_TARGET_WS: "http://ws.example.test",
        });
        const apiProxy = overrideConfig.server?.proxy?.["/api"];
        expect(apiProxy).toMatchObject({ target: "http://core.example.test" });
        expect(typeof (apiProxy as { rewrite?: unknown }).rewrite).toBe("function");
        expect((apiProxy as { rewrite: (path: string) => string }).rewrite("/api/items")).toBe(
            "/items",
        );
        expect(overrideConfig.server?.proxy?.["/ws"]).toMatchObject({
            target: "http://ws.example.test",
            ws: true,
        });
        expect(overrideConfig.server?.proxy?.["/auth"]).toMatchObject({
            changeOrigin: true,
            target: "http://shared.example.test",
        });

        let proxyRequestHandler:
            | ((request: { setHeader: (name: string, value: string) => void }) => void)
            | undefined;
        const configure = (
            apiProxy as {
                configure: (proxy: {
                    on: (event: string, handler: typeof proxyRequestHandler) => void;
                }) => void;
            }
        ).configure;
        configure({
            on: (event, handler) => {
                expect(event).toBe("proxyReq");
                proxyRequestHandler = handler;
            },
        });
        const setHeader = vi.fn();
        expect(proxyRequestHandler).toBeTypeOf("function");
        proxyRequestHandler?.({ setHeader });
        expect(setHeader).toHaveBeenCalledWith("X-Forwarded-Prefix", "/api");
    });

    it("keeps Docker and Caddy configuration provider-neutral and runnable", () => {
        const dockerfile = fs.readFileSync("docker/Dockerfile", "utf8");
        const dockerInstructions = uncommentedLines(dockerfile).map((line) => line.trim());
        expect(dockerfile).not.toMatch(
            /ci\/e2e-base|\.gitlab-ci|latest LTS|Cloudflare|prod\/demo|Demo \/ production-style/i,
        );
        for (const instruction of [
            "FROM node:24.22.1-alpine AS build",
            "RUN npm ci",
            "RUN npm exec vite -- build --mode ${BUILD_MODE}",
            "FROM caddy:2.11-alpine",
            "USER nobody",
            'CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile"]',
        ]) {
            expect(dockerInstructions).toContain(instruction);
        }

        const caddyfile = fs.readFileSync("docker/Caddyfile", "utf8");
        const caddyDirectives = uncommentedLines(caddyfile).map((line) => line.trim());
        expect(caddyfile).toContain("# TLS is terminated by an upstream proxy when TLS is used");
        expect(caddyfile).not.toContain("Cloudflare");
        for (const directive of [
            "auto_https off",
            "admin off",
            "persist_config off",
            "root * /usr/share/caddy",
            "rewrite @spa /index.html",
            "file_server",
        ]) {
            expect(caddyDirectives).toContain(directive);
        }
    });

    it("documents the active overrides and active npm audit exceptions", () => {
        const lockPolicyAllowlist = fs.readFileSync(
            "scripts/check-npm-lock-policy.allowlist.json",
            "utf8",
        );
        expect(lockPolicyAllowlist).toBe("[]\n");
        expect(JSON.parse(lockPolicyAllowlist)).toEqual([]);

        // image-size (GHSA-w3rx-r6r6-pgpr): the fixed 2.x breaks texture-compressor's
        // 0.7 API, and the vulnerable code is unreachable in the browser bundle.
        // maplibre-gl (GHSA-jrc7-96c5-q579): the only fix is a 5.x → 6.x major
        // bump, and DOM.sanitize is reachable only from AttributionControl, which
        // every map construction disables.
        // See the "Active npm audit Allowlist Entries" section of the guide.
        const auditAllowlist = JSON.parse(
            fs.readFileSync("scripts/check-npm-audit-allowlist.json", "utf8"),
        );
        expect(
            auditAllowlist
                .map((entry: { package: string; id: string }) => `${entry.package}:${entry.id}`)
                .sort(),
        ).toEqual([
            "image-size:GHSA-w3rx-r6r6-pgpr",
            "maplibre-gl:GHSA-jrc7-96c5-q579",
        ]);

        const guide = fs.readFileSync("DEPENDENCY-OVERRIDES.md", "utf8");
        expect(guide).toContain("No lockfile-policy exceptions are active.");
        expect(guide).not.toMatch(
            /### react-router|## Accepted Advisories|opencop\/opencop-web[#|!]/,
        );

        const sectionAfter = (heading: string) => guide.split(heading, 2)[1]?.split(/\n## /, 1)[0];

        const activeOverrides = sectionAfter("## Active Overrides");
        expect(activeOverrides?.match(/^### .+$/gm)).toEqual([
            "### handlebars → 4.7.10",
            "### fflate → 0.8.3",
            "### @chakra-ui/cli › zod → $zod",
        ]);

        const auditExceptions = sectionAfter("## Active npm audit Allowlist Entries");
        expect(auditExceptions?.match(/^### .+$/gm)).toEqual([
            "### image-size — GHSA-w3rx-r6r6-pgpr",
            "### maplibre-gl — GHSA-jrc7-96c5-q579",
        ]);
    });
});
