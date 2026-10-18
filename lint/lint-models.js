'use strict';
// Lints every model under models/: BPMN with .bpmnlintrc (bpmnlint recommended, camunda-compat 8.10 and
// the fbx rules), DMN with the fbx DMN rules, and checks that a model's file name is its process or
// decision id (07 §1.2). Exits 1 on any error, and on a run that found no models at all.
const fs = require('fs');
const path = require('path');
const { lintBpmn, lintDmn } = require('./lib');

const modelsDir = path.resolve(__dirname, '../models');

function walk(dir, ext, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, ext, out);
    else if (e.name.endsWith(ext)) out.push(p);
  }
  return out;
}

async function main() {
  const bpmn = walk(path.join(modelsDir, 'bpmn'), '.bpmn');
  const dmn = walk(path.join(modelsDir, 'dmn'), '.dmn');
  if (bpmn.length === 0) throw new Error('no BPMN models found');
  let errors = 0;
  let warnings = 0;
  const print = (file, p) => {
    console.log(`${path.relative(modelsDir, file)}  ${p.category}  ${p.id || '-'}  ${p.message}  (${p.rule})`);
    if (p.category === 'error') errors++; else warnings++;
  };
  for (const file of bpmn) {
    const { rootElement, problems } = await lintBpmn(fs.readFileSync(file, 'utf8'));
    problems.forEach((p) => print(file, p));
    const processes = (rootElement.rootElements || []).filter((e) => e.$type === 'bpmn:Process');
    if (processes.length !== 1 || `${processes[0].id}.bpmn` !== path.basename(file)) {
      print(file, { rule: 'fbx-file-name', id: '', category: 'error', message: 'File name must be <process id>.bpmn with one process' });
    }
  }
  for (const file of dmn) {
    const { rootElement, problems } = await lintDmn(fs.readFileSync(file, 'utf8'));
    problems.forEach((p) => print(file, p));
    const decisions = (rootElement.drgElement || []).filter((e) => e.$type === 'dmn:Decision');
    if (decisions.length !== 1 || `${decisions[0].id}.dmn` !== path.basename(file)) {
      print(file, { rule: 'fbx-file-name', id: '', category: 'error', message: 'File name must be <decision id>.dmn with one decision' });
    }
  }
  console.log(`${bpmn.length} BPMN and ${dmn.length} DMN models: ${errors} errors, ${warnings} warnings`);
  process.exit(errors === 0 ? 0 : 1);
}

main().catch((e) => { console.error(e); process.exit(1); });
