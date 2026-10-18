'use strict';
// Every variable a model reads or writes is in models/dictionary/variables.yaml, and no name looks like
// money or personal data (05 §5.1, 07 O6). FEEL in the model may not multiply or divide: arithmetic on
// money belongs to the owning domain service (07 §1.4, "amount * 0.01").
const { is } = require('bpmnlint-utils');
const { extensions, extension, feelVariables, isExpression } = require('../lib/model');
const dictionary = require('../lib/dictionary');

function expressionsOf(node) {
  const found = []; // [expression or plain name, isTargetName]
  extensions(node, 'zeebe:IoMapping').forEach((io) => {
    [...(io.inputParameters || []), ...(io.outputParameters || [])].forEach((p) => {
      found.push([p.source, false]);
      found.push([p.target, true]);
    });
  });
  const decision = extension(node, 'zeebe:CalledDecision');
  if (decision && decision.resultVariable) found.push([decision.resultVariable, true]);
  const assignment = extension(node, 'zeebe:AssignmentDefinition');
  if (assignment) ['assignee', 'candidateGroups', 'candidateUsers'].forEach((k) => found.push([assignment[k], false]));
  const schedule = extension(node, 'zeebe:TaskSchedule');
  if (schedule) ['dueDate', 'followUpDate'].forEach((k) => found.push([schedule[k], false]));
  const subscription = extension(node, 'zeebe:Subscription');
  if (subscription) found.push([subscription.correlationKey, false]);
  if (node.conditionExpression) found.push([`=${String(node.conditionExpression.body || '').replace(/^=/, '')}`, false]);
  (node.eventDefinitions || []).forEach((d) => {
    if (is(d, 'bpmn:TimerEventDefinition')) {
      ['timeDuration', 'timeDate', 'timeCycle'].forEach((k) => d[k] && found.push([d[k].body, false]));
    }
  });
  return found;
}

module.exports = function () {
  function check(node, reporter) {
    const allowed = dictionary.load().variables;
    for (const [value, isTarget] of expressionsOf(node)) {
      if (!value) continue;
      const names = isTarget ? [String(value).split('.')[0]] : feelVariables(value);
      for (const name of names) {
        if (dictionary.FORBIDDEN_NAME.test(name)) {
          reporter.report(node.id, `Variable <${name}> looks like money or personal data`);
        } else if (!allowed[name]) {
          reporter.report(node.id, `Variable <${name}> is not in models/dictionary/variables.yaml`);
        }
      }
      if (!isTarget && isExpression(value) && /[*/]/.test(value.slice(1).replace(/"(?:[^"\\]|\\.)*"/g, '""'))) {
        reporter.report(node.id, `Expression <${value}> multiplies or divides; arithmetic belongs to the domain`);
      }
    }
  }
  return { check };
};
