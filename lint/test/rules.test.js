'use strict';
// Each fbx rule is shown to fire on a model that breaks it (red) and to stay quiet on the shipped models
// (green). A rule that never fires would pass the shipped models vacuously.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const { lintBpmn, lintDmn } = require('../lib');

const HEAD = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
  xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_T" targetNamespace="t"
  modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.10.0">`;
const VERSION = '<bpmn:extensionElements><zeebe:versionTag value="0.1.0" /></bpmn:extensionElements>';

// A start event, the given body between Flow_In and Flow_Out, and an end event.
function model(body, { roots = '', processId = 'acc-test-process', version = VERSION } = {}) {
  return `${HEAD}${roots}
  <bpmn:process id="${processId}" isExecutable="true">${version}
    <bpmn:startEvent id="StartEvent_T"><bpmn:outgoing>Flow_In</bpmn:outgoing></bpmn:startEvent>
    ${body}
    <bpmn:endEvent id="EndEvent_T"><bpmn:incoming>Flow_Out</bpmn:incoming></bpmn:endEvent>
  </bpmn:process>
</bpmn:definitions>`;
}

function serviceTask(id, type, extra = '') {
  return `<bpmn:serviceTask id="${id}"><bpmn:extensionElements><zeebe:taskDefinition type="${type}" />${extra}</bpmn:extensionElements>
      <bpmn:incoming>Flow_In</bpmn:incoming><bpmn:outgoing>Flow_Out</bpmn:outgoing></bpmn:serviceTask>
    <bpmn:sequenceFlow id="Flow_In" sourceRef="StartEvent_T" targetRef="${id}" />
    <bpmn:sequenceFlow id="Flow_Out" sourceRef="${id}" targetRef="EndEvent_T" />`;
}

async function fbx(xml, rule) {
  const { problems } = await lintBpmn(xml);
  return problems.filter((p) => p.rule === `fbx/${rule}`).map((p) => p.message);
}

test('the shipped models have no lint errors', async () => {
  const dir = path.resolve(__dirname, '../../models/bpmn');
  const files = ['acc/acc-tpp-account-opening.bpmn', '_lib/lib-screening-review.bpmn'];
  for (const f of files) {
    const { problems } = await lintBpmn(fs.readFileSync(path.join(dir, f), 'utf8'));
    assert.deepStrictEqual(problems.filter((p) => p.category === 'error'), [], f);
  }
});

test('a fixture with a listed job type is clean', async () => {
  assert.deepStrictEqual(await fbx(model(serviceTask('Task_A', 'cus.verify-kyc')), 'job-type'), []);
});

test('job-type: unlisted, badly formed and missing job types', async () => {
  assert.match((await fbx(model(serviceTask('Task_A', 'cus.unknown-command')), 'job-type'))[0], /not in models\/dictionary/);
  assert.match((await fbx(model(serviceTask('Task_A', 'VerifyKyc')), 'job-type'))[0], /not <context>.<command>/);
  const noType = model(serviceTask('Task_A', 'cus.verify-kyc').replace('<zeebe:taskDefinition type="cus.verify-kyc" />', ''));
  assert.match((await fbx(noType, 'job-type'))[0], /no job type/);
});

test('no-compensation: a compensation boundary event and handler', async () => {
  const body = serviceTask('Task_A', 'cus.verify-kyc') + `
    <bpmn:boundaryEvent id="BoundaryEvent_C" attachedToRef="Task_A"><bpmn:compensateEventDefinition id="Comp_1" /></bpmn:boundaryEvent>
    <bpmn:serviceTask id="Task_Undo" isForCompensation="true"><bpmn:extensionElements><zeebe:taskDefinition type="cus.verify-kyc" /></bpmn:extensionElements></bpmn:serviceTask>
    <bpmn:association id="Association_1" sourceRef="BoundaryEvent_C" targetRef="Task_Undo" />`;
  const messages = await fbx(model(body), 'no-compensation');
  assert.ok(messages.some((m) => /Compensation event/.test(m)), messages.join());
  assert.ok(messages.some((m) => /Compensation handler/.test(m)), messages.join());
});

function userTask(extensions, boundary = true) {
  return `<bpmn:userTask id="Task_U"><bpmn:extensionElements>${extensions}</bpmn:extensionElements>
      <bpmn:incoming>Flow_In</bpmn:incoming><bpmn:outgoing>Flow_Out</bpmn:outgoing></bpmn:userTask>
    ${boundary ? `<bpmn:boundaryEvent id="BoundaryEvent_Sla" attachedToRef="Task_U"><bpmn:outgoing>Flow_Late</bpmn:outgoing>
      <bpmn:timerEventDefinition id="Timer_D"><bpmn:timeDuration>PT1H</bpmn:timeDuration></bpmn:timerEventDefinition></bpmn:boundaryEvent>
    <bpmn:endEvent id="EndEvent_Late"><bpmn:incoming>Flow_Late</bpmn:incoming></bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_Late" sourceRef="BoundaryEvent_Sla" targetRef="EndEvent_Late" />` : ''}
    <bpmn:sequenceFlow id="Flow_In" sourceRef="StartEvent_T" targetRef="Task_U" />
    <bpmn:sequenceFlow id="Flow_Out" sourceRef="Task_U" targetRef="EndEvent_T" />`;
}

test('user-task: job-worker user task, no candidate group, candidate users', async () => {
  const messages = await fbx(model(userTask('<zeebe:assignmentDefinition candidateUsers="u1" />')), 'user-task');
  assert.ok(messages.some((m) => /not a Camunda user task/.test(m)), messages.join());
  assert.ok(messages.some((m) => /no candidate group/.test(m)), messages.join());
  assert.ok(messages.some((m) => /candidate users/.test(m)), messages.join());
  assert.deepStrictEqual(await fbx(model(userTask('<zeebe:userTask /><zeebe:assignmentDefinition candidateGroups="cmp-review-l1" />')), 'user-task'), []);
});

test('bounded-wait: a user task without a timer and a bare message wait', async () => {
  const ok = '<zeebe:userTask /><zeebe:assignmentDefinition candidateGroups="cmp-review-l1" />';
  assert.match((await fbx(model(userTask(ok, false)), 'bounded-wait'))[0], /no interrupting timer/);
  assert.deepStrictEqual(await fbx(model(userTask(ok, true)), 'bounded-wait'), []);
  const roots = `<bpmn:message id="Message_K" name="Msg_KycCompleted"><bpmn:extensionElements><zeebe:subscription correlationKey="=applicationId" /></bpmn:extensionElements></bpmn:message>`;
  const bare = `<bpmn:intermediateCatchEvent id="Msg_K"><bpmn:incoming>Flow_In</bpmn:incoming><bpmn:outgoing>Flow_Out</bpmn:outgoing>
      <bpmn:messageEventDefinition id="Med_1" messageRef="Message_K" /></bpmn:intermediateCatchEvent>
    <bpmn:sequenceFlow id="Flow_In" sourceRef="StartEvent_T" targetRef="Msg_K" />
    <bpmn:sequenceFlow id="Flow_Out" sourceRef="Msg_K" targetRef="EndEvent_T" />`;
  assert.match((await fbx(model(bare, { roots }), 'bounded-wait'))[0], /not raced against a timer/);
});

test('variable-allow-list: unlisted, money-like and arithmetic', async () => {
  const io = (mapping) => serviceTask('Task_A', 'cus.verify-kyc', `<zeebe:ioMapping>${mapping}</zeebe:ioMapping>`);
  assert.deepStrictEqual(await fbx(model(io('<zeebe:input source="=applicationId" target="sourceRef" />')), 'variable-allow-list'), []);
  assert.match((await fbx(model(io('<zeebe:input source="=loanTerm" target="sourceRef" />')), 'variable-allow-list'))[0], /<loanTerm> is not in/);
  assert.match((await fbx(model(io('<zeebe:output source="=x" target="paymentAmount" />')), 'variable-allow-list')).join(), /<paymentAmount> looks like money/);
  assert.match((await fbx(model(io('<zeebe:input source="=customerEmail" target="purpose" />')), 'variable-allow-list'))[0], /personal data/);
  assert.match((await fbx(model(io('<zeebe:input source="=accountId * 0.01" target="purpose" />')), 'variable-allow-list')).join(), /multiplies or divides/);
  // A string literal is not a variable, and a function call is not one either.
  assert.deepStrictEqual(await fbx(model(io('<zeebe:input source="=string(&#34;amount * name&#34;)" target="purpose" />')), 'variable-allow-list'), []);
});

function callActivity(attributes) {
  return `<bpmn:callActivity id="Task_Call"><bpmn:extensionElements><zeebe:calledElement ${attributes} /></bpmn:extensionElements>
      <bpmn:incoming>Flow_In</bpmn:incoming><bpmn:outgoing>Flow_Out</bpmn:outgoing></bpmn:callActivity>
    <bpmn:sequenceFlow id="Flow_In" sourceRef="StartEvent_T" targetRef="Task_Call" />
    <bpmn:sequenceFlow id="Flow_Out" sourceRef="Task_Call" targetRef="EndEvent_T" />`;
}

test('binding: library by versionTag, own process by deployment, no variable propagation', async () => {
  const noProp = 'propagateAllChildVariables="false" propagateAllParentVariables="false"';
  assert.deepStrictEqual(await fbx(model(callActivity(`processId="lib-screening-review" bindingType="versionTag" versionTag="0.1.0" ${noProp}`)), 'binding'), []);
  assert.match((await fbx(model(callActivity(`processId="lib-screening-review" ${noProp}`)), 'binding'))[0], /must bind by versionTag/);
  assert.match((await fbx(model(callActivity(`processId="acc-child" bindingType="latest" ${noProp}`)), 'binding'))[0], /must bind by deployment/);
  assert.match((await fbx(model(callActivity('processId="acc-child" bindingType="deployment"')), 'binding'))[0], /propagates all variables/);
});

test('naming: element prefixes, process id and version tag', async () => {
  const bad = serviceTask('Task_A', 'cus.verify-kyc').replace(/Task_A/g, 'VerifyKyc');
  assert.match((await fbx(model(bad), 'naming'))[0], /should start with Task_/);
  const messages = await fbx(model(serviceTask('Task_A', 'cus.verify-kyc'), { processId: 'AccountOpening', version: '' }), 'naming');
  assert.ok(messages.some((m) => /kebab/.test(m)), messages.join());
  assert.ok(messages.some((m) => /no version tag/.test(m)), messages.join());
});

test('no-timer-loop: a fixed-interval polling loop', async () => {
  const loop = `<bpmn:serviceTask id="Task_Poll"><bpmn:extensionElements><zeebe:taskDefinition type="cus.verify-kyc" /></bpmn:extensionElements>
      <bpmn:incoming>Flow_In</bpmn:incoming><bpmn:incoming>Flow_Again</bpmn:incoming><bpmn:outgoing>Flow_Check</bpmn:outgoing></bpmn:serviceTask>
    <bpmn:exclusiveGateway id="Gateway_Done" default="Flow_Wait"><bpmn:incoming>Flow_Check</bpmn:incoming><bpmn:outgoing>Flow_Out</bpmn:outgoing><bpmn:outgoing>Flow_Wait</bpmn:outgoing></bpmn:exclusiveGateway>
    <bpmn:intermediateCatchEvent id="Timer_Wait"><bpmn:incoming>Flow_Wait</bpmn:incoming><bpmn:outgoing>Flow_Again</bpmn:outgoing>
      <bpmn:timerEventDefinition id="Ted_1"><bpmn:timeDuration>PT30S</bpmn:timeDuration></bpmn:timerEventDefinition></bpmn:intermediateCatchEvent>
    <bpmn:sequenceFlow id="Flow_In" sourceRef="StartEvent_T" targetRef="Task_Poll" />
    <bpmn:sequenceFlow id="Flow_Check" sourceRef="Task_Poll" targetRef="Gateway_Done" />
    <bpmn:sequenceFlow id="Flow_Out" sourceRef="Gateway_Done" targetRef="EndEvent_T"><bpmn:conditionExpression>=kycStatus = "VERIFIED"</bpmn:conditionExpression></bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_Wait" sourceRef="Gateway_Done" targetRef="Timer_Wait" />
    <bpmn:sequenceFlow id="Flow_Again" sourceRef="Timer_Wait" targetRef="Task_Poll" />`;
  assert.match((await fbx(model(loop), 'no-timer-loop'))[0], /use message correlation/);
});

const DMN = (decision) => `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="D" name="d" namespace="t">
  ${decision}
</definitions>`;
const TABLE = ({ hit = 'FIRST', input = 'jurisdiction', output = 'reviewQueue', tag = true } = {}) => DMN(`
  <decision id="cmp-test" name="t">${tag ? '<extensionElements><zeebe:versionTag value="0.1.0" /></extensionElements>' : ''}
    <decisionTable id="T" hitPolicy="${hit}">
      <input id="I"><inputExpression id="IE" typeRef="string"><text>${input}</text></inputExpression></input>
      <output id="O" name="${output}" typeRef="string" />
      <rule id="R"><inputEntry id="E1"><text>-</text></inputEntry><outputEntry id="E2"><text>"q"</text></outputEntry></rule>
    </decisionTable>
  </decision>`);

test('dmn: the shipped decision is clean and each DMN rule fires', async () => {
  const shipped = fs.readFileSync(path.resolve(__dirname, '../../models/dmn/cmp/cmp-review-routing.dmn'), 'utf8');
  assert.deepStrictEqual((await lintDmn(shipped)).problems, []);
  assert.deepStrictEqual((await lintDmn(TABLE())).problems, []);
  const messages = async (o) => (await lintDmn(TABLE(o))).problems.map((p) => p.message).join(' | ');
  assert.match(await messages({ hit: 'COLLECT' }), /not UNIQUE or FIRST/);
  assert.match(await messages({ output: 'rewardAmount' }), /<rewardAmount> looks like money/);
  assert.match(await messages({ input: 'transferAmount' }), /looks like money/);
  assert.match(await messages({ input: 'segment' }), /not in models\/dictionary/);
  assert.match(await messages({ input: 'amount * 2' }), /not a plain variable name/);
  assert.match(await messages({ tag: false }), /no version tag/);
});

test('message-no-merge: a message wait must map constants only, never the payload', async () => {
  const roots = '<bpmn:message id="Message_T" name="Msg_T"><bpmn:extensionElements><zeebe:subscription correlationKey="=applicationId" /></bpmn:extensionElements></bpmn:message>';
  const wait = (mapping) => `<bpmn:intermediateCatchEvent id="Msg_T">${mapping}
      <bpmn:incoming>Flow_In</bpmn:incoming><bpmn:outgoing>Flow_Out</bpmn:outgoing>
      <bpmn:messageEventDefinition id="Med_T" messageRef="Message_T" /></bpmn:intermediateCatchEvent>
    <bpmn:sequenceFlow id="Flow_In" sourceRef="StartEvent_T" targetRef="Msg_T" />
    <bpmn:sequenceFlow id="Flow_Out" sourceRef="Msg_T" targetRef="EndEvent_T" />`;
  const out = (source) => `<bpmn:extensionElements><zeebe:ioMapping><zeebe:output source="${source}" target="kycSignal" /></zeebe:ioMapping></bpmn:extensionElements>`;
  assert.match((await fbx(model(wait(''), { roots }), 'message-no-merge'))[0], /payload is merged/);
  assert.match((await fbx(model(wait(out('=kycStatus')), { roots }), 'message-no-merge'))[0], /reads the payload/);
  assert.deepStrictEqual(await fbx(model(wait(out('=&#34;RECEIVED&#34;')), { roots }), 'message-no-merge'), []);
});
