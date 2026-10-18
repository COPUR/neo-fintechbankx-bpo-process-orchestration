// Pure presentation rules, kept apart from the DOM so they are tested in Node: filters, SLA state, which
// decisions to offer, which need a confirmation, and the message for a refusal.

import { t } from './i18n.js';

export const FILTERS = ['mine', 'open', 'all'];
const DUE_SOON_MS = 60 * 60 * 1000;

export function slaState(dueAt, now = Date.now()) {
  if (!dueAt) return 'none';
  const left = Date.parse(dueAt) - now;
  if (Number.isNaN(left)) return 'none';
  if (left < 0) return 'overdue';
  return left < DUE_SOON_MS ? 'due-soon' : 'on-track';
}

export function filterTasks(tasks, filter) {
  switch (filter) {
    case 'mine': return tasks.filter((x) => x.assignment === 'MINE');
    case 'open': return tasks.filter((x) => x.assignment === 'UNASSIGNED');
    default: return tasks.slice();
  }
}

/** Rows for the task table: overdue first, then by due date (the server already sorts by due date). */
export function taskRows(tasks, lang, now = Date.now()) {
  const rank = { overdue: 0, 'due-soon': 1, 'on-track': 2, none: 3 };
  return tasks
    .map((task) => ({
      key: String(task.taskKey),
      task: t(lang, `task.${task.taskType}`),
      assignment: task.assignment,
      assignmentLabel: t(lang, `assignment.${task.assignment}`),
      sla: slaState(task.dueAt, now),
      slaLabel: t(lang, `sla.${slaState(task.dueAt, now)}`),
      dueAt: task.dueAt ?? null,
      canClaim: task.assignment === 'UNASSIGNED',
    }))
    .sort((a, b) => rank[a.sla] - rank[b.sla]);
}

function label(lang, key) {
  const text = t(lang, key);
  return text === key ? null : text;
}

/**
 * The decisions to offer: those the server allows for this task type that the UI has a label for. A decision
 * that moves money, rejects or hands the task on has a confirmation text and is asked twice.
 */
export function decisionOptions(task, lang) {
  return (task.decisions ?? [])
    .map((d) => ({ value: d, label: label(lang, `decision.${task.taskType}.${d}`), confirm: label(lang, `confirm.${task.taskType}.${d}`) }))
    .filter((o) => o.label !== null);
}

/** What the detail screen lets the user do now. */
export function detailState(task) {
  return {
    canClaim: task.assignment === 'UNASSIGNED',
    canRelease: task.assignment === 'MINE',
    canDecide: task.assignment === 'MINE',
    heldByOther: task.assignment === 'OTHER',
    referenceMissing: !task.reference,
  };
}

/** The link to the owning service's screen for this reference kind (caseId, paymentId); only a plain id goes in. */
export function referenceLink(views, referenceName, reference) {
  return caseLink(views?.[referenceName], reference);
}

/** Fills the template's {reference} with the encoded id; no other value is put in it, and only https is linked. */
export function caseLink(template, reference) {
  if (!template || !reference || !/^[A-Za-z0-9_-]{1,64}$/.test(reference)) return null;
  const url = template.replace('{reference}', encodeURIComponent(reference));
  return url.startsWith('https://') ? url : null;
}

export function errorMessage(lang, code) {
  const key = `error.${code}`;
  const text = t(lang, key);
  return text === key ? t(lang, 'error.UNKNOWN') : text;
}
