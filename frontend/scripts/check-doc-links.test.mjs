// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// Tests for scripts/check-doc-links.mjs.
// Run: node --test scripts/check-doc-links.test.mjs
//
// Repository cases build a throwaway git repo, because "tracked" is part of the
// rule: a file on disk that git does not know about is not a valid target.
import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { checkDocLinks, extractRelativeLinks, resolveLink } from "./check-doc-links.mjs";

const CHECK = fileURLToPath(new URL("./check-doc-links.mjs", import.meta.url));

// Keep the caller's git state out of the fixtures. A variable such as
// GIT_INDEX_FILE, which git sets inside hooks, would send fixture writes to another
// repository's index, and personal config (global excludes, hooks) would change
// what gets added.
for (const name of Object.keys(process.env)) {
    if (name.startsWith("GIT_")) delete process.env[name];
}
process.env.GIT_CONFIG_GLOBAL = "/dev/null";
process.env.GIT_CONFIG_NOSYSTEM = "1";

function writeFiles(root, files) {
    for (const [path, body] of Object.entries(files)) {
        mkdirSync(dirname(join(root, path)), { recursive: true });
        writeFileSync(join(root, path), body, "utf8");
    }
}

/** A throwaway git repo with `tracked` added to the index and `untracked` on disk only. */
function fixtureRepo(tracked, untracked = {}) {
    const root = mkdtempSync(join(tmpdir(), "doc-links-"));
    writeFiles(root, tracked);
    execFileSync("git", ["init", "-q", root]);
    execFileSync("git", ["-C", root, "add", "--", ...Object.keys(tracked)]);
    writeFiles(root, untracked);
    return root;
}

function runCli(root) {
    // The ceiling stops git from adopting an enclosing repo when `root` is not one.
    const env = { ...process.env, GIT_CEILING_DIRECTORIES: dirname(root) };
    return spawnSync(process.execPath, [CHECK, `--root=${root}`], { encoding: "utf8", env });
}

/** `{ link, target }` pairs only, for parser cases where the line number is not the point. */
function targets(contents) {
    return extractRelativeLinks(contents).map(({ link, target }) => ({ link, target }));
}

test("a link to a moved file fails with the file, line, link, and resolved path", () => {
    const root = fixtureRepo({
        "docs/architecture/plugins.md":
            "# Plugins\n\nThe [icon](../../packages/ui/src/display/Icon.tsx) moved.\n",
        "packages/ui/src/surfaces/Icon.tsx": "export {};\n",
    });
    try {
        const r = runCli(root);
        assert.equal(r.status, 1);
        assert.match(r.stderr, /FAIL — 1 broken relative Markdown link/);
        assert.ok(
            r.stderr.includes(
                "docs/architecture/plugins.md:3: ../../packages/ui/src/display/Icon.tsx → packages/ui/src/display/Icon.tsx",
            ),
            r.stderr,
        );
    } finally {
        rmSync(root, { recursive: true, force: true });
    }
});

test("a directory target passes only when it contains a tracked file", () => {
    const root = fixtureRepo(
        {
            "docs/README.md":
                "[ui](../packages/ui/) [src](../packages/ui/src) [root](../) [scratch](../scratch/)\n",
            "packages/ui/src/index.ts": "export {};\n",
        },
        { "scratch/notes.txt": "untracked\n" },
    );
    try {
        const { links, broken } = checkDocLinks(root);
        assert.equal(links, 4);
        assert.deepEqual(broken, [
            { doc: "docs/README.md", line: 1, link: "../scratch/", resolved: "scratch" },
        ]);
    } finally {
        rmSync(root, { recursive: true, force: true });
    }
});

test("a file on disk that git does not track is not a valid target", () => {
    const root = fixtureRepo(
        { "README.md": "[draft](docs/draft.md)\n" },
        { "docs/draft.md": "# Draft\n" },
    );
    try {
        assert.deepEqual(checkDocLinks(root).broken, [
            { doc: "README.md", line: 1, link: "docs/draft.md", resolved: "docs/draft.md" },
        ]);
    } finally {
        rmSync(root, { recursive: true, force: true });
    }
});

test("a tracked file deleted locally is neither scanned nor a valid target", () => {
    const root = fixtureRepo({ "README.md": "[old](old.md)\n", "old.md": "[gone](missing.md)\n" });
    try {
        rmSync(join(root, "old.md"));
        assert.deepEqual(checkDocLinks(root).broken, [
            { doc: "README.md", line: 1, link: "old.md", resolved: "old.md" },
        ]);
    } finally {
        rmSync(root, { recursive: true, force: true });
    }
});

test("Markdown files are matched by extension in any case", () => {
    const root = fixtureRepo({ "NOTES.MD": "[x](missing.md)\n" });
    try {
        assert.deepEqual(checkDocLinks(root).broken, [
            { doc: "NOTES.MD", line: 1, link: "missing.md", resolved: "missing.md" },
        ]);
    } finally {
        rmSync(root, { recursive: true, force: true });
    }
});

test("anchor-only, empty, scheme, and protocol-relative targets are skipped", () => {
    const contents = [
        "[top](#overview)",
        "[blank]( )",
        "[angle](<>)",
        "[site](https://example.com/guide.md)",
        "[mail](mailto:team@example.com)",
        "[cdn](//cdn.example.com/app.js)",
        "[local](guide.md#setup)",
    ].join("\n");
    assert.deepEqual(extractRelativeLinks(contents), [
        { line: 7, link: "guide.md#setup", target: "guide.md" },
    ]);
});

test("links inside code spans and fenced blocks still count", () => {
    const contents = [
        "```md",
        "[fenced](missing.md)",
        "```",
        "Inline `[span](other.md)` too.",
    ].join("\n");
    assert.deepEqual(
        extractRelativeLinks(contents).map(({ line, link }) => ({ line, link })),
        [
            { line: 2, link: "missing.md" },
            { line: 4, link: "other.md" },
        ],
    );
});

test("reference-style and HTML links are not checked", () => {
    const contents = [
        "[text][ref] and [ref][]",
        "[ref]: missing.md",
        '<a href="missing.md">a</a> <img src="missing.png">',
    ].join("\n");
    assert.deepEqual(extractRelativeLinks(contents), []);
});

test("a <...> target keeps its spaces", () => {
    assert.deepEqual(targets('[a](<docs/My Guide.md> "Title")'), [
        { link: "docs/My Guide.md", target: "docs/My Guide.md" },
    ]);
});

test("a plain target stops at the first space, dropping a title", () => {
    assert.deepEqual(targets('[b](docs/guide.md "Title")'), [
        { link: "docs/guide.md", target: "docs/guide.md" },
    ]);
});

test("a #fragment or ?query suffix is removed before resolving", () => {
    assert.deepEqual(targets("[d](guide.md?plain=1#L3)"), [
        { link: "guide.md?plain=1#L3", target: "guide.md" },
    ]);
});

test("%-escapes decode as UTF-8, invalid bytes become U+FFFD, and malformed escapes stay", () => {
    const contents = [
        "![a](<img/a%20b.png>)",
        "[b](caf%C3%A9.md)",
        "[c](bad%FF.md)",
        "[d](100%zz.md)",
    ].join("\n");
    assert.deepEqual(
        extractRelativeLinks(contents).map(({ target }) => target),
        ["img/a b.png", "café.md", "bad�.md", "100%zz.md"],
    );
});

test("a clean repo passes, including <...>, anchor, and scheme links", () => {
    const root = fixtureRepo({
        "README.md":
            "[guide](<docs/My Guide.md#intro>) [top](#top) [site](https://example.com/missing.md)\n",
        "docs/My Guide.md": "[home](../README.md)\n",
    });
    try {
        const r = runCli(root);
        assert.equal(r.status, 0, r.stderr);
        assert.match(r.stdout, /OK \(2 relative link\(s\) in 2 tracked Markdown file\(s\)\)/);
    } finally {
        rmSync(root, { recursive: true, force: true });
    }
});

test("a target resolves against the linking document's directory", () => {
    assert.equal(resolveLink("docs/guide.md", "setup.md"), "docs/setup.md");
    assert.equal(resolveLink("docs/guide.md", "../README.md"), "README.md");
    assert.equal(resolveLink("docs/guide.md", ".."), ".");
});

test("a link that leaves the repository is an error", () => {
    assert.equal(resolveLink("docs/guide.md", "../../outside.md"), null);

    const root = fixtureRepo({
        "docs/guide.md": "Line one\n[sibling](../../other-repo/README.md)\n",
    });
    try {
        const r = runCli(root);
        assert.equal(r.status, 1);
        assert.ok(
            r.stderr.includes(
                "docs/guide.md:2: ../../other-repo/README.md → (outside the repository)",
            ),
            r.stderr,
        );
    } finally {
        rmSync(root, { recursive: true, force: true });
    }
});

test("an absolute path is an error, reported as absolute", () => {
    assert.equal(resolveLink("docs/guide.md", "/docs/guide.md"), null);

    const root = fixtureRepo({ "docs/guide.md": "[self](/docs/guide.md)\n" });
    try {
        const r = runCli(root);
        assert.equal(r.status, 1);
        assert.ok(
            r.stderr.includes(
                "docs/guide.md:1: /docs/guide.md → (absolute path; use a relative link)",
            ),
            r.stderr,
        );
    } finally {
        rmSync(root, { recursive: true, force: true });
    }
});

test("a root that is not a git repository is a setup error (exit 2), not a pass", () => {
    const root = mkdtempSync(join(tmpdir(), "doc-links-"));
    try {
        const r = runCli(root);
        assert.equal(r.status, 2);
        assert.match(r.stderr, /doc-links: cannot scan/);
        assert.equal(r.stderr.match(/fatal:/g)?.length, 1, "git's error is reported once");
    } finally {
        rmSync(root, { recursive: true, force: true });
    }
});
