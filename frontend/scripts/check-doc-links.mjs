#!/usr/bin/env node
// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// Fails when a relative inline Markdown link in a tracked Markdown file (`.md`, in
// any case) points at a path that is not in the tracked tree.
//
// The open-source source export applies this rule to the published docs and
// refuses to build on a broken link. Running it here catches the break at MR
// time, while the file move that caused it is still in review. Keep the two in
// step:
//
//   - Only inline links count: `[text](target)` and `![alt](target)`. Reference
//     links (`[text][ref]`, `[ref]: target`) and HTML `<a>`/`<img>` are not
//     checked. Code spans and fenced blocks are not exempt.
//   - Skipped targets: empty, `#anchor`, any `scheme:` (https:, mailto:, ...),
//     and protocol-relative `//host`.
//   - The target is the first whitespace-separated token, which drops a title
//     such as `"Title"`, or the text inside `<...>` for the angle-bracket form.
//   - A `#fragment` or `?query` suffix is removed, then %-escapes are decoded.
//   - The target resolves against the linking document's directory and must be
//     a tracked file or a directory containing one. Untracked files don't count,
//     so a link that only works on your machine fails here.
//   - A link that resolves outside the repository, including an `/absolute`
//     path, is an error.
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, join, posix, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

const INLINE_LINK = /!?\[[^\]]*\]\(([^)\n]+)\)/dg;
const SCHEME = /^[A-Za-z][A-Za-z0-9+.-]*:/;

/**
 * Decodes %-escapes. Each run of escapes is read as UTF-8 (invalid bytes become
 * U+FFFD), and a malformed escape such as `%zz` stays as written instead of
 * throwing the way `decodeURIComponent` does.
 *
 * @param {string} s
 * @returns {string}
 */
function decodePercentEscapes(s) {
    return s.replace(/(?:%[0-9A-Fa-f]{2})+/g, (run) =>
        new TextDecoder().decode(
            Uint8Array.from(run.slice(1).split("%"), (hex) => parseInt(hex, 16)),
        ),
    );
}

/** @param {string} text @param {number} index @returns {number} 1-based line of `index` */
function lineAt(text, index) {
    let line = 1;
    for (let i = text.indexOf("\n"); i !== -1 && i < index; i = text.indexOf("\n", i + 1)) line++;
    return line;
}

/**
 * @typedef {object} RelativeLink
 * @property {number} line 1-based line where the target starts
 * @property {string} link the target as written
 * @property {string} target the path to resolve: fragment and query removed, %-escapes decoded
 */

/**
 * Relative inline link targets in one Markdown document, in source order.
 *
 * @param {string} contents
 * @returns {RelativeLink[]}
 */
export function extractRelativeLinks(contents) {
    const links = [];
    for (const match of contents.matchAll(INLINE_LINK)) {
        const raw = match[1].trim();
        const link =
            raw.startsWith("<") && raw.includes(">")
                ? raw.slice(1, raw.indexOf(">"))
                : raw.split(/\s+/, 1)[0];
        if (!link || link.startsWith("#") || SCHEME.test(link) || link.startsWith("//")) continue;
        links.push({
            line: lineAt(contents, match.indices[1][0]),
            link,
            target: decodePercentEscapes(link.split("#", 1)[0].split("?", 1)[0]),
        });
    }
    return links;
}

/**
 * The repository-relative path `target` names when linked from `doc`, or null
 * when it is absolute or leaves the repository. The repository root itself is ".".
 *
 * @param {string} doc repository-relative path of the linking document
 * @param {string} target link target from {@link extractRelativeLinks}
 * @returns {string | null}
 */
export function resolveLink(doc, target) {
    if (target.startsWith("/")) return null;
    const resolved = posix.normalize(posix.join(posix.dirname(doc), target)).replace(/\/+$/, "");
    if (resolved === ".." || resolved.startsWith("../")) return null;
    return resolved;
}

/** @param {string} root @param {string[]} args @returns {string[]} */
function lsFiles(root, args) {
    // stderr is piped so a git failure is reported once, inside the thrown error.
    const out = execFileSync("git", ["-C", root, "ls-files", "-z", ...args], {
        encoding: "utf8",
        maxBuffer: 64 * 1024 * 1024,
        stdio: ["ignore", "pipe", "pipe"],
    });
    return out.split("\0").filter(Boolean);
}

/**
 * Tracked paths that are present in the working tree. A tracked file deleted
 * locally is gone from both sides: it is not scanned and is not a valid target.
 *
 * @param {string} root
 * @returns {Set<string>}
 */
function trackedFiles(root) {
    const deleted = new Set(lsFiles(root, ["--deleted"]));
    return new Set(lsFiles(root, []).filter((path) => !deleted.has(path)));
}

/** @param {Iterable<string>} files @returns {Set<string>} every ancestor directory, including "." */
function ancestorDirs(files) {
    const dirs = new Set();
    for (const file of files) {
        for (let dir = posix.dirname(file); !dirs.has(dir); dir = posix.dirname(dir)) dirs.add(dir);
    }
    return dirs;
}

/**
 * @typedef {object} BrokenLink
 * @property {string} doc repository-relative path of the linking document
 * @property {number} line
 * @property {string} link the target as written
 * @property {string | null} resolved repository-relative path it names, or null when it is absolute or leaves the repository
 */

/**
 * Every broken relative inline link in the tracked Markdown under `root`.
 *
 * @param {string} root git working tree to scan
 * @returns {{ docs: number, links: number, broken: BrokenLink[] }}
 */
export function checkDocLinks(root) {
    const files = trackedFiles(root);
    const dirs = ancestorDirs(files);
    let docs = 0;
    let links = 0;
    const broken = [];
    for (const doc of files) {
        if (posix.extname(doc).toLowerCase() !== ".md") continue;
        docs++;
        for (const { line, link, target } of extractRelativeLinks(
            readFileSync(join(root, doc), "utf8"),
        )) {
            links++;
            const resolved = resolveLink(doc, target);
            if (resolved === null || !(files.has(resolved) || dirs.has(resolved))) {
                broken.push({ doc, line, link, resolved });
            }
        }
    }
    return { docs, links, broken };
}

/** @param {string[]} argv @returns {string} */
function parseRoot(argv) {
    let root = REPO_ROOT;
    // `--root=<dir>` scans another git working tree. The tests use it to point the
    // check at fixture repos; CI passes no flags.
    for (const arg of argv) {
        if (arg.startsWith("--root=")) root = resolve(arg.slice("--root=".length));
    }
    return root;
}

/** @param {BrokenLink} brokenLink @returns {string} the path it resolved to, or why it has none */
function describeResolution({ link, resolved }) {
    if (resolved !== null) return resolved;
    return link.startsWith("/")
        ? "(absolute path; use a relative link)"
        : "(outside the repository)";
}

/** @param {string[]} argv @returns {number} process exit code */
export function main(argv) {
    const root = parseRoot(argv);

    let result;
    try {
        result = checkDocLinks(root);
    } catch (error) {
        console.error(`doc-links: cannot scan ${root}: ${error.message}`);
        return 2;
    }

    const { docs, links, broken } = result;
    if (broken.length > 0) {
        console.error(`doc-links: FAIL — ${broken.length} broken relative Markdown link(s):`);
        for (const brokenLink of broken) {
            const { doc, line, link } = brokenLink;
            console.error(`  ${doc}:${line}: ${link} → ${describeResolution(brokenLink)}`);
        }
        return 1;
    }

    console.log(`doc-links: OK (${links} relative link(s) in ${docs} tracked Markdown file(s)).`);
    return 0;
}

// CLI only when invoked directly, so the test can import the pieces above.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    process.exit(main(process.argv.slice(2)));
}
