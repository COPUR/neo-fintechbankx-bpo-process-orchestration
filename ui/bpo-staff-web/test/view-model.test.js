import { test } from 'node:test';
import assert from 'node:assert/strict';
import { caseLink, decisionOptions, referenceLink, detailState, errorMessage, filterTasks, slaState, taskRows } from '../src/view-model.js';

const NOW = Date.parse('2026-10-18T12:00:00Z');
const task = (key, assignment, dueAt, taskType = 'Task_AnalystReview') => ({ taskKey: key, assignment, dueAt, taskType });

test('SLA states', () => {
  assert.equal(slaState('2026-10-18T11:59:00Z', NOW), 'overdue');
  assert.equal(slaState('2026-10-18T12:30:00Z', NOW), 'due-soon');
  assert.equal(slaState('2026-10-18T16:00:00Z', NOW), 'on-track');
  assert.equal(slaState(null, NOW), 'none');
  assert.equal(slaState('not a date', NOW), 'none');
});

test('filters split mine, unassigned and all; tasks held by others only appear under all', () => {
  const tasks = [task(1, 'MINE'), task(2, 'UNASSIGNED'), task(3, 'OTHER')];
  assert.deepEqual(filterTasks(tasks, 'mine').map((x) => x.taskKey), [1]);
  assert.deepEqual(filterTasks(tasks, 'open').map((x) => x.taskKey), [2]);
  assert.deepEqual(filterTasks(tasks, 'all').map((x) => x.taskKey), [1, 2, 3]);
});

test('rows put overdue first and offer claim only on unassigned tasks', () => {
  const rows = taskRows([task(1, 'OTHER', '2026-10-18T16:00:00Z'), task(2, 'UNASSIGNED', '2026-10-18T11:00:00Z')], 'tr', NOW);
  assert.deepEqual(rows.map((r) => [r.key, r.sla, r.canClaim]), [['2', 'overdue', true], ['1', 'on-track', false]]);
  assert.equal(rows[0].task, 'Tarama incelemesi');
  assert.equal(rows[1].assignmentLabel, 'Başka bir personelde');
});

test('decisions follow the server and the irreversible ones ask for confirmation', () => {
  const options = decisionOptions({ taskType: 'Task_AnalystReview', decisions: ['CLEAR', 'REJECT', 'ESCALATE', 'SOMETHING_NEW'] }, 'en');
  assert.deepEqual(options.map((o) => o.value), ['CLEAR', 'REJECT', 'ESCALATE']);
  assert.equal(options[0].confirm, null);
  assert.match(options[1].confirm, /rejected/);
  assert.match(options[2].confirm, /senior queue/);
});

test('a payment decision that moves money is labelled for the payment and confirmed', () => {
  const options = decisionOptions({ taskType: 'Task_SupervisorReview', decisions: ['SETTLED', 'REJECTED'] }, 'tr');
  assert.deepEqual(options.map((o) => o.label), ['Şemada ödendi', 'Şemada reddedildi']);
  assert.match(options[0].confirm, /kesin olarak düşülecek/);
  assert.deepEqual(decisionOptions({ taskType: 'Task_SupervisorReview', decisions: ['CLEAR'] }, 'tr'), []);
});

test('each reference kind links to its own screen', () => {
  const views = { caseId: 'https://staff.example/cases/{reference}', paymentId: 'https://staff.example/payments/{reference}' };
  assert.equal(referenceLink(views, 'paymentId', 'PAY-1'), 'https://staff.example/payments/PAY-1');
  assert.equal(referenceLink(views, 'accountId', 'ACC-1'), null);
  assert.equal(referenceLink(undefined, 'caseId', 'C-1'), null);
});

test('the detail screen only lets the holder decide', () => {
  assert.deepEqual(detailState({ assignment: 'UNASSIGNED', reference: 'C-1' }),
    { canClaim: true, canRelease: false, canDecide: false, heldByOther: false, referenceMissing: false });
  assert.equal(detailState({ assignment: 'MINE', reference: null }).canDecide, true);
  assert.equal(detailState({ assignment: 'MINE', reference: null }).referenceMissing, true);
  assert.equal(detailState({ assignment: 'OTHER' }).heldByOther, true);
});

test('the case link only carries a plain id into an https template', () => {
  const tpl = 'https://staff.example/cases/{reference}';
  assert.equal(caseLink(tpl, 'CASE-1'), 'https://staff.example/cases/CASE-1');
  assert.equal(caseLink(tpl, '../admin'), null);
  assert.equal(caseLink(tpl, 'a b'), null);
  assert.equal(caseLink('javascript:alert({reference})', 'CASE-1'), null);
  assert.equal(caseLink(tpl, null), null);
});

test('unknown refusal codes fall back to a generic message', () => {
  assert.match(errorMessage('tr', 'FOUR_EYES_REQUIRED'), /başka bir yetkili/);
  assert.equal(errorMessage('en', 'SOMETHING'), errorMessage('en', 'UNKNOWN'));
});
