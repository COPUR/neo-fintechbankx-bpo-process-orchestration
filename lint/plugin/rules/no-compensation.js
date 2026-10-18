'use strict';
// Compensation events are not used (05 §7, D7-K8): compensation is an explicit path of domain commands.
const { is } = require('bpmnlint-utils');

module.exports = function () {
  function check(node, reporter) {
    if (is(node, 'bpmn:CompensateEventDefinition')) {
      reporter.report(node.id || '', 'Compensation event: model compensation as an explicit path');
    }
    if (node.isForCompensation) {
      reporter.report(node.id, 'Compensation handler: model compensation as an explicit path');
    }
    (node.eventDefinitions || []).forEach((d) => {
      if (is(d, 'bpmn:CompensateEventDefinition')) {
        reporter.report(node.id, 'Compensation event: model compensation as an explicit path');
      }
    });
  }
  return { check };
};
