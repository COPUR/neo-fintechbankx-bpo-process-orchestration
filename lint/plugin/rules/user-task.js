'use strict';
// A user task is a Camunda user task (zeebe:userTask, 05 §6.2) with a candidate group (07 §1.4).
const { is } = require('bpmnlint-utils');
const { extension } = require('../lib/model');

module.exports = function () {
  function check(node, reporter) {
    if (!is(node, 'bpmn:UserTask')) return;
    if (!extension(node, 'zeebe:UserTask')) {
      reporter.report(node.id, 'User task is not a Camunda user task (zeebe:userTask)');
    }
    const assignment = extension(node, 'zeebe:AssignmentDefinition');
    if (!assignment || !assignment.candidateGroups) {
      reporter.report(node.id, 'User task has no candidate group');
    }
    if (assignment && assignment.candidateUsers) {
      reporter.report(node.id, 'User task names candidate users; route by group');
    }
  }
  return { check };
};
