'use strict';
// Shared by lint-models.js and the rule tests: lint one BPMN document with .bpmnlintrc, and lint DMN.
const fs = require('fs');
const path = require('path');
const { BpmnModdle } = require('bpmn-moddle');
const { DmnModdle } = require('dmn-moddle');
const { Linter } = require('bpmnlint');
const NodeResolver = require('bpmnlint/lib/resolver/node-resolver');
const { createScopedRequire } = require('bpmnlint/lib/resolver/helper');
const zeebe = require('zeebe-bpmn-moddle/resources/zeebe.json');
const dictionary = require('bpmnlint-plugin-fbx/lib/dictionary');

const config = JSON.parse(fs.readFileSync(path.join(__dirname, '.bpmnlintrc'), 'utf8'));
delete config.moddleExtensions;
const resolver = new NodeResolver({ require: createScopedRequire(__dirname) });

async function lintBpmn(xml) {
  const { rootElement, warnings } = await new BpmnModdle({ zeebe }).fromXML(xml);
  const problems = [];
  warnings.forEach((w) => problems.push({ rule: 'parse', id: '', message: w.message, category: 'error' }));
  const results = await new Linter({ config, resolver }).lint(rootElement);
  for (const [rule, reports] of Object.entries(results)) {
    reports.forEach((r) => problems.push({ rule, id: r.id, message: r.message, category: r.category }));
  }
  return { rootElement, problems };
}

const HIT_POLICIES = new Set(['UNIQUE', 'FIRST']);

// DMN rules (07 §1.4 models/dmn-lint): hit policy UNIQUE or FIRST; no output that looks like money or
// personal data; inputs read allow-listed variables only; a version tag for versionTag binding.
async function lintDmn(xml) {
  const { rootElement, warnings } = await new DmnModdle().fromXML(xml);
  const problems = warnings.map((w) => ({ rule: 'parse', id: '', message: w.message, category: 'error' }));
  const report = (id, message) => problems.push({ rule: 'fbx-dmn', id, message, category: 'error' });
  const allowed = dictionary.load().variables;
  for (const decision of (rootElement.drgElement || []).filter((e) => e.$type === 'dmn:Decision')) {
    // Without a zeebe DMN descriptor the extension parses as a generic element (zeebe:versionTag).
    const tags = ((decision.extensionElements && decision.extensionElements.values) || [])
      .filter((v) => v.$type === 'zeebe:versionTag' && v.value);
    if (!tags.length) report(decision.id, 'Decision has no version tag');
    const table = decision.decisionLogic;
    if (!table || table.$type !== 'dmn:DecisionTable') {
      report(decision.id, 'Decision is not a decision table');
      continue;
    }
    if (!HIT_POLICIES.has(table.hitPolicy || 'UNIQUE')) report(decision.id, `Hit policy ${table.hitPolicy} is not UNIQUE or FIRST`);
    for (const output of table.output || []) {
      if (dictionary.FORBIDDEN_NAME.test(output.name || '')) report(output.id, `Output <${output.name}> looks like money or personal data`);
    }
    for (const input of table.input || []) {
      const expression = (input.inputExpression && input.inputExpression.text) || '';
      if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(expression)) report(input.id, `Input <${expression}> is not a plain variable name`);
      else if (dictionary.FORBIDDEN_NAME.test(expression)) report(input.id, `Input <${expression}> looks like money or personal data`);
      else if (!allowed[expression]) report(input.id, `Input <${expression}> is not in models/dictionary/variables.yaml`);
    }
  }
  return { rootElement, problems };
}

module.exports = { lintBpmn, lintDmn };
