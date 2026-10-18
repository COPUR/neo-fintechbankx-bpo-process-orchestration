'use strict';
// Call activities and business rule tasks never bind to "latest" (07 O11, [B10]): a library process or a
// decision binds by versionTag, a process of the same package by deployment.
const { is } = require('bpmnlint-utils');
const { extension } = require('../lib/model');

module.exports = function () {
  function check(node, reporter) {
    if (is(node, 'bpmn:CallActivity')) {
      const called = extension(node, 'zeebe:CalledElement');
      if (!called) return;
      const lib = String(called.processId || '').startsWith('lib-');
      if (lib && (called.bindingType !== 'versionTag' || !called.versionTag)) {
        reporter.report(node.id, `Library process <${called.processId}> must bind by versionTag`);
      }
      if (!lib && called.bindingType !== 'deployment') {
        reporter.report(node.id, `Process <${called.processId}> must bind by deployment`);
      }
      if (called.propagateAllChildVariables !== false || called.propagateAllParentVariables !== false) {
        reporter.report(node.id, 'Call activity propagates all variables; map inputs and outputs explicitly');
      }
    }
    if (is(node, 'bpmn:BusinessRuleTask')) {
      const decision = extension(node, 'zeebe:CalledDecision');
      if (decision && (decision.bindingType !== 'versionTag' || !decision.versionTag)) {
        reporter.report(node.id, `Decision <${decision.decisionId}> must bind by versionTag`);
      }
    }
  }
  return { check };
};
