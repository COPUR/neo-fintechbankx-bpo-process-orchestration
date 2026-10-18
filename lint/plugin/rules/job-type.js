'use strict';
// Every job-based task has a job type of the form <context>.<command>[.vN] that is listed in
// models/dictionary/job-types.yaml (07 O7). An unlisted type has no owner and no contract.
const { is } = require('bpmnlint-utils');
const { extension } = require('../lib/model');
const dictionary = require('../lib/dictionary');

const FORMAT = /^[a-z][a-z0-9-]*\.[a-z][a-z0-9-]*(\.v[0-9]+)?$/;
const JOB_TASKS = ['bpmn:ServiceTask', 'bpmn:SendTask', 'bpmn:ScriptTask'];

module.exports = function () {
  function check(node, reporter) {
    if (!JOB_TASKS.some((t) => is(node, t))) return;
    const definition = extension(node, 'zeebe:TaskDefinition');
    if (!definition || !definition.type) {
      reporter.report(node.id, 'Job task has no job type');
      return;
    }
    if (!FORMAT.test(definition.type)) {
      reporter.report(node.id, `Job type <${definition.type}> is not <context>.<command>`);
      return;
    }
    if (!dictionary.load().jobTypes[definition.type]) {
      reporter.report(node.id, `Job type <${definition.type}> is not in models/dictionary/job-types.yaml`);
    }
  }
  return { check };
};
