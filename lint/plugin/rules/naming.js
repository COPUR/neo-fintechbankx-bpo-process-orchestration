'use strict';
// Ids carry their element prefix (07 O7, [B3]) and a process id is <context>-<name> in kebab case with a
// version tag, so library binding by versionTag can resolve it.
const { is } = require('bpmnlint-utils');
const { extension } = require('../lib/model');

const PREFIXES = [
  ['bpmn:StartEvent', ['StartEvent_']],
  ['bpmn:EndEvent', ['EndEvent_']],
  ['bpmn:BoundaryEvent', ['BoundaryEvent_']],
  ['bpmn:IntermediateCatchEvent', ['Msg_', 'Timer_', 'Event_']],
  ['bpmn:IntermediateThrowEvent', ['Event_', 'Msg_']],
  ['bpmn:Gateway', ['Gateway_']],
  ['bpmn:Activity', ['Task_']],
  ['bpmn:SequenceFlow', ['Flow_']],
  ['bpmn:Error', ['Error_']],
  ['bpmn:Message', ['Message_']]
];
const PROCESS_ID = /^[a-z][a-z0-9]*(-[a-z0-9]+)+$/;

module.exports = function () {
  function check(node, reporter) {
    if (is(node, 'bpmn:Process')) {
      if (!PROCESS_ID.test(node.id)) reporter.report(node.id, 'Process id is not <context>-<name> in kebab case');
      if (!extension(node, 'zeebe:VersionTag')) reporter.report(node.id, 'Process has no version tag');
      return;
    }
    if (is(node, 'bpmn:Error') && node.errorCode && !/^Err_[A-Z][A-Za-z0-9]*$/.test(node.errorCode)) {
      reporter.report(node.id, `Error code <${node.errorCode}> is not Err_<Name>`);
    }
    for (const [type, prefixes] of PREFIXES) {
      if (is(node, type)) {
        if (!prefixes.some((p) => String(node.id).startsWith(p))) {
          reporter.report(node.id, `Id should start with ${prefixes.join(' or ')}`);
        }
        return;
      }
    }
  }
  return { check };
};
