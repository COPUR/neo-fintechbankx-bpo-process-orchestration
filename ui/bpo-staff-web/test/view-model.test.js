import { test } from 'node:test';
import assert from 'node:assert/strict';
import { caseLink, decisionOptions, referenceLink, detailState, errorMessage, filterCounts, filterTasks, newTaskCount, slaState, taskRows, unknownOutcomeMessage } from '../src/view-model.js';

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
  const options = decisionOptions({ taskType: 'Task_AnalystReview', reference: 'CASE-9', decisions: ['CLEAR', 'REJECT', 'ESCALATE', 'SOMETHING_NEW'] }, 'en');
  assert.deepEqual(options.map((o) => o.value), ['CLEAR', 'REJECT', 'ESCALATE']);
  assert.equal(options[0].confirm, null);
  assert.equal(options[0].confirmLabel, null);
  assert.equal(options[1].confirm, 'Case CASE-9 will be rejected: the application closes and the applicant is notified.');
  assert.match(options[2].confirm, /^Case CASE-9 moves to the senior queue/);
  assert.equal(options[1].confirmLabel, 'Yes, record it for CASE-9');
});

test('a payment decision that moves money is labelled for the payment and confirmed', () => {
  const options = decisionOptions({ taskType: 'Task_SupervisorReview', decisions: ['SETTLED', 'REJECTED'] }, 'tr');
  assert.deepEqual(options.map((o) => o.label), ['Şemada ödendi', 'Şemada reddedildi']);
  assert.match(options[0].confirm, /kesin olarak düşülecek/);
  assert.equal(decisionOptions({ taskType: 'Task_SupervisorReview', reference: 'PAY-1', decisions: ['SETTLED'] }, 'tr')[0].confirmLabel,
    'Evet, PAY-1 için kaydet');
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
    { canClaim: true, canRelease: false, canDecide: false, heldByOther: false, referenceMissing: false, fourEyesBlocked: false });
  assert.equal(detailState({ assignment: 'MINE', reference: 'C-1' }).canDecide, true);
  assert.equal(detailState({ assignment: 'OTHER' }).heldByOther, true);
});

test('no decision without a reference or against four eyes; the holder can still release', () => {
  const noReference = detailState({ assignment: 'MINE', reference: null });
  assert.deepEqual([noReference.canDecide, noReference.canRelease, noReference.referenceMissing], [false, true, true]);
  const blocked = detailState({ assignment: 'MINE', reference: 'C-1', fourEyesBlocked: true });
  assert.deepEqual([blocked.canDecide, blocked.canRelease, blocked.fourEyesBlocked], [false, true, true]);
  // Four eyes also keeps the user from claiming: an unassigned task offers no claim.
  const unclaimable = detailState({ assignment: 'UNASSIGNED', reference: 'C-1', fourEyesBlocked: true });
  assert.deepEqual([unclaimable.canClaim, unclaimable.canDecide, unclaimable.canRelease], [false, false, false]);
  // Only a literal true blocks; the server sends a boolean.
  assert.equal(detailState({ assignment: 'MINE', reference: 'C-1', fourEyesBlocked: 'false' }).canDecide, true);
});

test('after an unknown outcome the single re-read says what happened', () => {
  assert.equal(unknownOutcomeMessage({ code: 'TASK_NOT_FOUND' }), 'outcome.closed');
  assert.equal(unknownOutcomeMessage({ task: { assignment: 'MINE' } }), 'outcome.notRecorded');
  assert.equal(unknownOutcomeMessage({ task: { assignment: 'OTHER' } }), 'outcome.stillOpen');
  assert.equal(unknownOutcomeMessage({ code: 'ENGINE_UNAVAILABLE' }), 'outcome.unreachable');
  assert.equal(unknownOutcomeMessage({ code: 'OUTCOME_UNKNOWN' }), 'outcome.unreachable');
});

test('filter labels count their tasks and a poll only counts what is new', () => {
  const tasks = [{ taskKey: 1, assignment: 'MINE' }, { taskKey: 2, assignment: 'UNASSIGNED' }, { taskKey: 3, assignment: 'OTHER' }];
  assert.deepEqual(filterCounts(tasks), { mine: 1, open: 1, all: 3 });
  assert.deepEqual(filterCounts([]), { mine: 0, open: 0, all: 0 });
  // Task 1 closed, task 4 arrived: one new, and a closed task is not counted as new.
  assert.equal(newTaskCount(tasks, [{ taskKey: '2' }, { taskKey: 3 }, { taskKey: 4 }]), 1);
  assert.equal(newTaskCount(tasks, tasks), 0);
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
