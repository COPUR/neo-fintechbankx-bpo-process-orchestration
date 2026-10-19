import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { MESSAGES, LANGUAGES, t, formatDateTime } from '../src/i18n.js';
import { ACTORS } from '../src/api.js';

// The task types the API serves, read from its catalogue so the two layers cannot drift apart.
const refusals = readFileSync(new URL('../../../services/bpo-process-api/src/main/java/com/bank/bpo/processapi/domain/TaskRefusal.java', import.meta.url), 'utf8');
const catalogue = readFileSync(new URL('../../../services/bpo-process-api/src/main/java/com/bank/bpo/processapi/domain/TaskTypes.java', import.meta.url), 'utf8');
const HUMAN_REASONS = catalogue.match(/HUMAN_REASONS = List\.of\(([^)]*)\)/)[1].match(/"(\w+)"/g).map((r) => r.slice(1, -1));
const TASK_TYPES = [...catalogue.matchAll(/new TaskType\(Actor\.(\w+),\s*"[^"]+", "(Task_\w+)", "\w+", List\.of\(([^)]*)\),\s*REASON, HUMAN_REASONS, (?:null|"\w+"), "(\w+)"\)/g)]
  .map(([, actor, taskType, decisions, reference]) => ({
    actor: actor.toLowerCase().replace(/_/g, '-'), taskType, reference, decisions: decisions.match(/"(\w+)"/g).map((d) => d.slice(1, -1)),
  }));

test('Turkish and English have the same keys and no empty label', () => {
  const tr = Object.keys(MESSAGES.tr).sort();
  const en = Object.keys(MESSAGES.en).sort();
  assert.deepEqual(tr, en);
  for (const lang of LANGUAGES) {
    for (const [key, text] of Object.entries(MESSAGES[lang])) assert.ok(text.trim().length > 0, `${lang} ${key}`);
  }
});

test('Turkish and English use the same placeholders in every label', () => {
  const names = (text) => [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();
  for (const key of Object.keys(MESSAGES.tr)) assert.deepEqual(names(MESSAGES.tr[key]), names(MESSAGES.en[key]), key);
});

test('every confirmation names the record it is about', () => {
  for (const lang of LANGUAGES) {
    for (const [key, text] of Object.entries(MESSAGES[lang])) {
      if (key.startsWith('confirm.') || key === 'action.confirmFor') assert.match(text, /\{reference\}/, `${lang} ${key}`);
    }
  }
});

test('placeholders are filled and an unknown key shows itself', () => {
  assert.equal(t('tr', 'app.signedInAs', { user: 'ayse.analyst' }), 'ayse.analyst olarak girildi');
  assert.equal(t('en', 'detail.heading', { task: 'Screening review', key: 7 }), 'Screening review · 7');
  assert.equal(t('en', 'no.such.key'), 'no.such.key');
});

test('the API catalogue was read and its actors are the client\'s', () => {
  assert.equal(TASK_TYPES.length, 4);
  assert.deepEqual(HUMAN_REASONS, ['OTHER']);
  assert.deepEqual([...new Set(TASK_TYPES.map((x) => x.actor))].sort(), [...ACTORS].sort());
});

test('every actor, task type, decision and reference the API serves has labels in both languages', () => {
  for (const lang of LANGUAGES) {
    for (const { actor, taskType, decisions, reference } of TASK_TYPES) {
      for (const key of [`actor.${actor}`, `actor.${actor}.hint`, `task.${taskType}`, `reference.${reference}`,
        `detail.open.${reference}`, `detail.hint.${reference}`, ...decisions.map((d) => `decision.${taskType}.${d}`),
        ...HUMAN_REASONS.map((r) => `reason.${r}`)]) {
        assert.ok(key in MESSAGES[lang], `${lang} ${key}`);
      }
      // Anything that is not a plain clear is confirmed: it rejects, moves money or hands the task on.
      for (const d of decisions.filter((x) => x !== 'CLEAR')) assert.ok(`confirm.${taskType}.${d}` in MESSAGES[lang], `${lang} confirm ${taskType} ${d}`);
    }
  }
});

test('every label the screens build from server values exists in both languages', () => {
  const dynamic = [
    'assignment.UNASSIGNED', 'assignment.MINE', 'assignment.OTHER',
    'sla.overdue', 'sla.due-soon', 'sla.on-track', 'sla.none',
    // Every refusal code bpo-process-api returns (TaskRefusal.Reason), plus the client's own.
    'error.NOT_YOUR_ACTOR', 'error.TASK_NOT_FOUND', 'error.TASK_CLAIMED_BY_OTHER', 'error.TASK_NOT_ASSIGNED_TO_YOU',
    'error.DECISION_NOT_ALLOWED', 'error.BODY_NOT_ALLOWED', 'error.FOUR_EYES_REQUIRED', 'error.ENGINE_UNAVAILABLE',
    'error.OUTCOME_UNKNOWN', 'error.REASON_NOT_ALLOWED', 'error.QUERY_NOT_ALLOWED',
    'error.SESSION', 'error.UNKNOWN',
    'outcome.closed', 'outcome.notRecorded', 'outcome.stillOpen', 'outcome.unreachable',
  ];
  for (const lang of LANGUAGES) for (const key of dynamic) assert.ok(key in MESSAGES[lang], `${lang} ${key}`);
  const reasons = refusals.slice(refusals.indexOf('enum Reason')).split('}')[0].match(/^\s+([A-Z_]+),?$/gm).map((r) => r.trim().replace(',', ''));
  assert.ok(reasons.length >= 11);
  for (const reason of reasons) assert.ok(dynamic.includes(`error.${reason}`), reason);
});

test('dates use the language locale', () => {
  assert.match(formatDateTime('tr', '2026-10-18T14:00:00Z'), /2026/);
  assert.equal(formatDateTime('en', null), '');
});
