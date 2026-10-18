'use strict';
// No polling loop (07 O4, 3.4): a cycle in the flow that passes a timer means the model polls. External
// results arrive as messages; a timer is a deadline or a growing fallback, never a fixed loop.
const { is } = require('bpmnlint-utils');

function isTimer(node) {
  return (is(node, 'bpmn:IntermediateCatchEvent') || is(node, 'bpmn:BoundaryEvent'))
    && (node.eventDefinitions || []).some((d) => is(d, 'bpmn:TimerEventDefinition'));
}

module.exports = function () {
  function check(node, reporter) {
    if (!is(node, 'bpmn:Process') && !is(node, 'bpmn:SubProcess')) return;
    const elements = node.flowElements || [];
    const next = new Map();
    const add = (from, to) => { if (!next.has(from)) next.set(from, []); next.get(from).push(to); };
    elements.forEach((e) => {
      if (is(e, 'bpmn:SequenceFlow')) add(e.sourceRef, e.targetRef);
      if (is(e, 'bpmn:BoundaryEvent')) add(e.attachedToRef, e);
    });
    const state = new Map(); // 1 on stack, 2 done
    const stack = [];
    const reported = new Set();
    const visit = (n) => {
      state.set(n, 1);
      stack.push(n);
      for (const m of next.get(n) || []) {
        if (state.get(m) === 1) {
          const cycle = stack.slice(stack.indexOf(m));
          const timer = cycle.find(isTimer);
          if (timer && !reported.has(timer.id)) {
            reported.add(timer.id);
            reporter.report(timer.id, 'Timer inside a loop: use message correlation, not polling');
          }
        } else if (!state.get(m)) {
          visit(m);
        }
      }
      stack.pop();
      state.set(n, 2);
    };
    elements.filter((e) => !is(e, 'bpmn:SequenceFlow')).forEach((e) => { if (!state.get(e)) visit(e); });
  }
  return { check };
};
