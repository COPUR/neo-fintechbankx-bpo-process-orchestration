'use strict';
// No instance waits without a deadline (05 §7.6): a user task or receive task carries an interrupting
// timer boundary event, and a message catch event follows an event-based gateway that also has a timer.
const { is } = require('bpmnlint-utils');

function hasTimer(event) {
  return (event.eventDefinitions || []).some((d) => is(d, 'bpmn:TimerEventDefinition'));
}

module.exports = function () {
  function check(node, reporter) {
    if (is(node, 'bpmn:UserTask') || is(node, 'bpmn:ReceiveTask')) {
      const boundaries = (node.$parent.flowElements || []).filter(
        (e) => is(e, 'bpmn:BoundaryEvent') && e.attachedToRef === node && e.cancelActivity !== false && hasTimer(e)
      );
      if (boundaries.length === 0) {
        reporter.report(node.id, 'Wait has no interrupting timer boundary event');
      }
      return;
    }
    if (is(node, 'bpmn:IntermediateCatchEvent')
        && (node.eventDefinitions || []).some((d) => is(d, 'bpmn:MessageEventDefinition'))) {
      const gateways = (node.incoming || []).map((f) => f.sourceRef).filter((s) => is(s, 'bpmn:EventBasedGateway'));
      const bounded = gateways.some((g) => (g.outgoing || []).some((f) => is(f.targetRef, 'bpmn:IntermediateCatchEvent') && hasTimer(f.targetRef)));
      if (!bounded) {
        reporter.report(node.id, 'Message wait is not raced against a timer on an event-based gateway');
      }
    }
  }
  return { check };
};
