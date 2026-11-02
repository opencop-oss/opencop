// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// Focused tests for scripts/junit-wrap.mjs. Run: node --test scripts/junit-wrap.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const WRAP = fileURLToPath(new URL('./junit-wrap.mjs', import.meta.url));
const dir = mkdtempSync(join(tmpdir(), 'junit-wrap-'));
let n = 0;

function run(extraArgs, cmdArgs) {
    const outFile = join(dir, `r${n++}.xml`);
    const res = spawnSync('node', [WRAP, '--name', 'case', '--out', outFile, ...extraArgs, '--', ...cmdArgs], {
        encoding: 'utf8',
        maxBuffer: 64 * 1024 * 1024,
    });
    let xml = '';
    try {
        xml = readFileSync(outFile, 'utf8');
    } catch {
        /* file may be absent on argument errors */
    }
    return { status: res.status, xml };
}

test('success: exit 0, no <failure>', () => {
    const r = run([], ['node', '-e', 'console.log("ok")']);
    assert.equal(r.status, 0);
    assert.match(r.xml, /failures="0"/);
    assert.doesNotMatch(r.xml, /<failure/);
});

test('blocking failure: propagates the exit code and records the failure', () => {
    const r = run([], ['node', '-e', 'process.exit(5)']);
    assert.equal(r.status, 5);
    assert.match(r.xml, /failures="1"/);
    assert.match(r.xml, /exit 5/);
});

test('advisory (--nonblocking) failure: exit 0 but failure still recorded', () => {
    const r = run(['--nonblocking'], ['node', '-e', 'process.exit(9)']);
    assert.equal(r.status, 0);
    assert.match(r.xml, /failures="1"/);
});

test('spawn error: reports "failed to start" and is non-zero', () => {
    const r = run([], ['this-command-does-not-exist-xyzzy']);
    assert.notEqual(r.status, 0);
    assert.match(r.xml, /failed to start/);
});

test('output above 1 MiB is not killed (async spawn, not spawnSync)', () => {
    const r = run([], ['node', '-e', 'process.stdout.write("x".repeat(2 * 1024 * 1024)); process.exit(0)']);
    assert.equal(r.status, 0);
    assert.match(r.xml, /failures="0"/);
});

test('ANSI/control bytes produce well-formed XML (no forbidden control chars)', () => {
    const r = run([], ['node', '-e', 'process.stdout.write("\\x1b[31mred\\x1b[0m\\x07\\x00bell"); process.exit(3)']);
    assert.equal(r.status, 3);
    assert.doesNotMatch(r.xml, /[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/);
    assert.match(r.xml, /<failure/);
});

test('signal termination is reported', () => {
    const r = run([], ['node', '-e', 'process.kill(process.pid, "SIGKILL")']);
    assert.notEqual(r.status, 0);
    assert.match(r.xml, /terminated by signal/);
});

test('exits 2 when required args are missing', () => {
    const res = spawnSync('node', [WRAP, '--name', 'x'], { encoding: 'utf8' });
    assert.equal(res.status, 2);
});

test.after(() => rmSync(dir, { recursive: true, force: true }));
