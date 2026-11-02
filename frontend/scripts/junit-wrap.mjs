#!/usr/bin/env node
// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// Runs a command, streams its output live to the job log, and records the outcome
// as a one-testcase JUnit XML file so bundled CI checks surface individually in
// GitLab's merge-request test-summary widget.
//
// Usage:
//   node scripts/junit-wrap.mjs --name <case> --out <file.xml> [--nonblocking] -- <cmd> [args...]
//
// Uses async `spawn` (not `spawnSync`) so a check that prints more than
// spawnSync's 1 MiB default buffer isn't killed and mislabeled. Output streams to
// the log as it arrives; only a bounded tail is retained for the <failure> body.
// Distinguishes numeric exit, signal termination, and spawn error, and strips
// XML-1.0-forbidden control bytes so the report stays well-formed.
//
// Exits with the wrapped command's exit code so `run-p` fails the bundle on a
// blocking check. With --nonblocking it always exits 0 (the failure is still
// recorded in the report) — used for advisory checks that must not gate.
import { spawn } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

const argv = process.argv.slice(2);
const sep = argv.indexOf('--');
const opts = sep === -1 ? argv : argv.slice(0, sep);
const cmd = sep === -1 ? [] : argv.slice(sep + 1);

let name;
let out;
let nonblocking = false;
for (let i = 0; i < opts.length; i++) {
    if (opts[i] === '--name') name = opts[++i];
    else if (opts[i] === '--out') out = opts[++i];
    else if (opts[i] === '--nonblocking') nonblocking = true;
}
if (!name || !out || cmd.length === 0) {
    console.error('junit-wrap: --name, --out, and `-- <cmd>` are required');
    process.exit(2);
}

// Bounded rolling tail (bytes of combined stdout+stderr) kept for the <failure>
// body — the full output is already streamed to the job log.
const TAIL_LIMIT = 64 * 1024;
let tail = '';
const keepTail = (chunk) => {
    tail += chunk;
    if (tail.length > TAIL_LIMIT) tail = tail.slice(tail.length - TAIL_LIMIT);
};

// XML 1.0 permits tab/newline/CR but forbids other C0/C1 control bytes; strip ANSI
// escapes and those control bytes so tool output (colors, spinners) can't produce
// an unparseable report.
const stripAnsi = (s) => s.replace(/\x1B\[[0-9;?]*[ -/]*[@-~]/g, '');
const clean = (s) => stripAnsi(String(s)).replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, '');
const esc = (s) =>
    clean(s).replace(/[<>&"']/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;', "'": '&apos;' })[c]);

const started = Date.now();
let settled = false;

const finish = ({ code, signal, error }) => {
    if (settled) return;
    settled = true;
    const duration = ((Date.now() - started) / 1000).toFixed(3);
    const failed = !(code === 0);

    let message;
    if (error) message = `${name} failed to start: ${error.message}`;
    else if (signal) message = `${name} terminated by signal ${signal}`;
    else message = `${name} failed (exit ${code})`;

    const failureXml = failed
        ? `\n            <failure message="${esc(message)}">${esc(tail)}</failure>\n        `
        : '';
    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<testsuites name="checks" tests="1" failures="${failed ? 1 : 0}" time="${duration}">
    <testsuite name="checks" tests="1" failures="${failed ? 1 : 0}" time="${duration}">
        <testcase classname="checks" name="${esc(name)}" time="${duration}">${failureXml}</testcase>
    </testsuite>
</testsuites>
`;
    mkdirSync(dirname(out), { recursive: true });
    writeFileSync(out, xml);

    // Propagate a real failure (map signal/spawn-error to 1) unless --nonblocking.
    const exitCode = failed ? (typeof code === 'number' ? code : 1) : 0;
    process.exit(nonblocking ? 0 : exitCode);
};

const child = spawn(cmd[0], cmd.slice(1), { stdio: ['inherit', 'pipe', 'pipe'] });
child.stdout.on('data', (c) => { process.stdout.write(c); keepTail(c.toString()); });
child.stderr.on('data', (c) => { process.stderr.write(c); keepTail(c.toString()); });
child.on('error', (error) => finish({ error }));
child.on('close', (code, signal) => finish({ code: code === null ? undefined : code, signal: signal || undefined }));
