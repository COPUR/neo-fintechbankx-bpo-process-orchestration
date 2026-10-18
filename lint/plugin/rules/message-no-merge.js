'use strict';
// A message only wakes an instance (DevSecOps B-4). Camunda 8 merges a correlated message's variables into the
// process unless the catch event has output mappings, so every message catch event, message boundary event,
// receive task and message start event maps its outputs explicitly, and no mapping reads a message variable:
// each output source is a constant. The instance re-reads what it needs from the owning service.
const { is } = require('bpmnlint-utils');
const { extensions } = require('../lib/model');

function isMessageWait(node) {
  if (is(node, 'bpmn:ReceiveTask')) return true;
  if (!(is(node, 'bpmn:IntermediateCatchEvent') || is(node, 'bpmn:BoundaryEvent') || is(node, 'bpmn:StartEvent'))) return false;
  return (node.eventDefinitions || []).some((d) => is(d, 'bpmn:MessageEventDefinition'));
}

const CONSTANT = /^=\s*("[A-Z0-9_]+"|true|false|null)\s*$/;

module.exports = function () {
  function check(node, reporter) {
    if (!isMessageWait(node)) return;
    const mappings = extensions(node, 'zeebe:IoMapping');
    const outputs = mappings.flatMap((m) => m.outputParameters || []);
    if (outputs.length === 0) {
      reporter.report(node.id, 'Message wait has no output mapping, so the message payload is merged into the process');
      return;
    }
    for (const o of outputs) {
      if (!CONSTANT.test(o.source || '')) {
        reporter.report(node.id, `Message output ${o.target} reads the payload; map a constant and re-read the owning service`);
      }
    }
  }
  return { check };
};
