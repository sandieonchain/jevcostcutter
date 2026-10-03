// Local publication check. Runtime machine identifiers are never printed or persisted.
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { execFileSync } from 'node:child_process';
const root = process.cwd();
const forbidden = [os.userInfo().username, os.hostname(), os.homedir()].filter(x => x.length > 3);
const findings = [];
let inspected = 0;
function inspect(bytes, label) {
  inspected++;
  const value = bytes.toString('utf8');
  if (forbidden.some(x => value.includes(x))) findings.push(`${label}: runtime identity`);
  if (/\/(?:Users|home)\/[A-Za-z0-9_.-]+\//.test(value)) findings.push(`${label}: home path`);
  if (/[A-Z]:\\(?:Users|Documents|Desktop)\\/i.test(value)) findings.push(`${label}: Windows profile path`);
  if (/\b10\.(?:\d{1,3}\.){2}\d{1,3}\b|\b192\.168\.\d{1,3}\.\d{1,3}\b/.test(value)) findings.push(`${label}: private IP canary`);
  if (/\b(?:internal|corp|private)\.[a-z0-9.-]+\b/i.test(value)) findings.push(`${label}: private domain canary`);
  if (/\b[A-Za-z0-9._%+-]+@(?!example\.invalid)[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b/.test(value)) findings.push(`${label}: review email`);
  if (/sk-[A-Za-z0-9_-]{24,}|AKIA[A-Z0-9]{16}|-----BEGIN (?:RSA |OPENSSH )?PRIVATE KEY-----\r?\n[A-Za-z0-9+/]{40}/.test(value)) findings.push(`${label}: possible secret`);
}
function inspectIdentity(bytes, label) {
  inspected++;
  const value = bytes.toString('utf8');
  if (forbidden.some(x => value.includes(x))) findings.push(`${label}: runtime identity`);
  if (/hostname="(?!anonymous")[^"]+"/.test(value)) findings.push(`${label}: non-anonymous test hostname`);
  if (/\/(?:Users|home)\/[A-Za-z0-9_.-]+\//.test(value)) findings.push(`${label}: home path`);
}
function walk(dir) {
 for (const entry of fs.readdirSync(dir,{withFileTypes:true})) {
  if (entry.name === '.git' || entry.name === '.gradle') continue;
  const file=path.join(dir,entry.name), label=path.relative(root,file);
  if (label.includes('/src/test/') || label.includes('/build/classes/java/test/') || label.includes('/build/reports/tests/') || label.includes('/build/test-results/') || label.startsWith('build/reports/dependency-verification/') || label === 'build/reports/problems/problems-report.html') {
   if (entry.isDirectory()) walk(file); else if (entry.isFile()) inspectIdentity(fs.readFileSync(file),label);
   continue;
  }
  if (entry.isDirectory()) walk(file);
  else if (entry.isFile()) {
   if (/\.(zip|tar|jar)$/.test(entry.name)) {
    // First-party JARs are decompressed; third-party licensing/native metadata is not authored by this project.
    if (entry.name.startsWith('jevopt-')) { inspect(execFileSync(entry.name.endsWith('.tar')?'tar':'unzip',entry.name.endsWith('.tar')?['-xOf',file]:['-p',file],{maxBuffer:50_000_000,env:{...process.env,LC_ALL:'C',LANG:'C'}}),label); inspect(Buffer.from(entry.name),`${label}: archive-name`); }
   } else inspect(fs.readFileSync(file),label);
  }
 }
}
walk(root);
const workflow = fs.readFileSync(path.join(root,'.github/workflows/build.yml'),'utf8');
for (const line of workflow.split(/\r?\n/)) if (/\buses:/.test(line) && !/uses:\s+[^@\s]+@[a-f0-9]{40}(?:\s|$)/.test(line)) findings.push('.github/workflows/build.yml: unpinned action');
console.log(JSON.stringify({inspected,findings,scope:'Source, file/archive names and first-party JAR/ZIP/TAR contents; third-party bytes are not claimed identity-free'},null,2));
process.exitCode=findings.length ? 1 : 0;
