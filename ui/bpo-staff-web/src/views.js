// Screens per actor: the task list (with filter, SLA badge and claim) and the task detail (reference link,
// claim/release, decision with confirmation). Focus moves to each new screen's heading and every result is
// announced in the live region, so the flow works with a keyboard and a screen reader.

import { el, clear } from './dom.js';
import { t, formatDateTime } from './i18n.js';
import { FILTERS, decisionOptions, detailState, filterCounts, filterTasks, reasonOptions, referenceLink, taskRows } from './view-model.js';

function heading(text) {
  const h = el('h2', { tabindex: '-1', id: 'screen-heading' }, text);
  queueMicrotask(() => h.focus());
  return h;
}

export function renderActorNav(nav, { lang, actors, current, onSelect }) {
  clear(nav);
  nav.append(el('h2', { class: 'visually-hidden' }, t(lang, 'nav.actors')));
  const list = el('ul', { class: 'actor-tabs' });
  for (const actor of actors) {
    const button = el('button', { type: 'button', 'aria-current': actor === current ? 'page' : null }, t(lang, `actor.${actor}`));
    button.addEventListener('click', () => onSelect(actor));
    list.append(el('li', {}, button));
  }
  nav.append(list);
}

/**
 * The task list. Returns a function that shows how many new tasks a background poll found: it fills a polite
 * status line with an update button and leaves the table alone, so nothing moves under the user's pointer.
 */
export function renderList(main, { lang, actor, tasks, filter, onFilter, onOpen, onClaim, onRefresh }) {
  clear(main);
  main.append(heading(t(lang, `actor.${actor}`)), el('p', { class: 'hint' }, t(lang, `actor.${actor}.hint`)));

  const counts = filterCounts(tasks);
  const filterGroup = el('fieldset', { class: 'filters' }, el('legend', {}, t(lang, 'list.filter')));
  for (const f of FILTERS) {
    const id = `filter-${f}`;
    const input = el('input', { type: 'radio', name: 'filter', id, value: f, checked: f === filter });
    input.addEventListener('change', () => onFilter(f));
    const text = t(lang, 'list.filter.count', { label: t(lang, `list.filter.${f}`), count: counts[f] });
    filterGroup.append(el('span', { class: 'choice' }, input, el('label', { for: id }, text)));
  }
  const refresh = el('button', { type: 'button', class: 'secondary' }, t(lang, 'list.refresh'));
  refresh.addEventListener('click', onRefresh);
  main.append(el('div', { class: 'toolbar' }, filterGroup, refresh));

  const banner = el('div', { class: 'new-tasks', role: 'status', 'aria-live': 'polite' });
  main.append(banner);
  const showNewTasks = (count) => {
    clear(banner);
    if (count <= 0) return;
    const update = el('button', { type: 'button', class: 'secondary' }, t(lang, 'list.showNew'));
    update.addEventListener('click', onRefresh);
    banner.append(el('span', {}, t(lang, 'list.newTasks', { count })), ' ', update);
  };

  const rows = taskRows(filterTasks(tasks, filter), lang);
  if (rows.length === 0) {
    main.append(el('p', { class: 'empty' }, t(lang, 'list.empty')));
    return showNewTasks;
  }
  const table = el('table', { class: 'tasks' },
    el('caption', {}, t(lang, 'list.caption', { actor: t(lang, `actor.${actor}`) })),
    el('thead', {}, el('tr', {}, ['task', 'key', 'assignment', 'due', 'actions']
      .map((c) => el('th', { scope: 'col' }, t(lang, `list.col.${c}`))))));
  const body = el('tbody');
  for (const row of rows) {
    const open = el('button', { type: 'button', 'aria-label': `${t(lang, 'action.open')} ${row.task} ${row.key}` }, t(lang, 'action.open'));
    open.addEventListener('click', () => onOpen(row.key));
    const actions = el('td', { class: 'actions' }, open);
    if (row.canClaim) {
      const claim = el('button', { type: 'button', class: 'secondary', 'aria-label': `${t(lang, 'action.claim')} ${row.task} ${row.key}` }, t(lang, 'action.claim'));
      claim.addEventListener('click', () => onClaim(row.key));
      actions.append(claim);
    }
    body.append(el('tr', { class: `sla-${row.sla}` },
      el('th', { scope: 'row' }, row.task),
      el('td', {}, row.key),
      el('td', {}, row.assignmentLabel),
      el('td', {}, el('span', { class: `badge ${row.sla}` }, row.slaLabel), ' ', formatDateTime(lang, row.dueAt)),
      actions));
  }
  table.append(body);
  main.append(table);
  return showNewTasks;
}

export function renderDetail(main, { lang, actor, task, referenceViews, onBack, onClaim, onRelease, onDecide }) {
  clear(main);
  const state = detailState(task);
  const back = el('button', { type: 'button', class: 'link' }, t(lang, 'action.back'));
  back.addEventListener('click', onBack);
  main.append(back, heading(t(lang, 'detail.heading', { task: t(lang, `task.${task.taskType}`), key: task.taskKey })));

  const facts = el('dl', { class: 'facts' },
    el('dt', {}, t(lang, 'list.col.assignment')), el('dd', {}, t(lang, `assignment.${task.assignment}`)),
    el('dt', {}, t(lang, 'list.col.due')), el('dd', {}, formatDateTime(lang, task.dueAt) || t(lang, 'sla.none')),
    el('dt', {}, t(lang, `reference.${task.referenceName}`)), el('dd', {}, task.reference ?? '—'));
  main.append(facts, el('p', { class: 'hint' }, t(lang, `detail.hint.${task.referenceName}`)));

  const link = referenceLink(referenceViews, task.referenceName, task.reference);
  if (link) main.append(el('p', {}, el('a', { href: link, target: '_blank', rel: 'noopener noreferrer' }, t(lang, `detail.open.${task.referenceName}`))));
  if (state.referenceMissing) main.append(el('p', { class: 'warning', role: 'note' }, t(lang, 'detail.referenceMissing')));
  if (task.fourEyes && !state.fourEyesBlocked) main.append(el('p', { class: 'note' }, t(lang, 'detail.fourEyes')));

  const release = el('button', { type: 'button', class: 'secondary' }, t(lang, 'action.release'));
  release.addEventListener('click', onRelease);
  if (state.fourEyesBlocked) {
    // No claim and no decision form; a task already held (for example claimed in Tasklist) can be released.
    main.append(el('p', { class: 'warning', role: 'note' }, t(lang, 'detail.fourEyesBlocked')));
    if (state.canRelease) main.append(el('div', { class: 'buttons' }, release));
    return;
  }

  if (state.heldByOther) {
    main.append(el('p', { class: 'note' }, t(lang, 'detail.otherHolds')));
    return;
  }
  if (state.canClaim) {
    const claim = el('button', { type: 'button' }, t(lang, 'action.claim'));
    claim.addEventListener('click', onClaim);
    main.append(el('p', {}, t(lang, 'detail.claimFirst')), claim);
    return;
  }

  if (!state.canDecide) {
    // A missing reference: no decision form, only the way out.
    main.append(el('div', { class: 'buttons' }, release));
    return;
  }

  const form = el('form', { class: 'decision', novalidate: true });
  const group = el('fieldset', {}, el('legend', {}, t(lang, 'decision.legend')));
  for (const option of decisionOptions(task, lang)) {
    const id = `decision-${option.value}`;
    group.append(el('span', { class: 'choice' },
      el('input', { type: 'radio', name: 'decision', id, value: option.value, required: true }),
      el('label', { for: id }, option.label)));
  }
  // U4: a coded reason is required with every decision; codes only, no free text, so no personal data is sent.
  const reasons = el('fieldset', {}, el('legend', {}, t(lang, 'reason.legend')));
  for (const option of reasonOptions(task, lang)) {
    const id = `reason-${option.value}`;
    reasons.append(el('span', { class: 'choice' },
      el('input', { type: 'radio', name: 'reason', id, value: option.value, required: true }),
      el('label', { for: id }, option.label)));
  }
  const confirmBox = el('div', { class: 'confirm', hidden: true, role: 'alertdialog', 'aria-labelledby': 'confirm-text' });
  const submit = el('button', { type: 'submit' }, t(lang, 'action.submit'));
  form.append(group, reasons, el('div', { class: 'buttons' }, submit, release), confirmBox);

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const chosen = form.querySelector('input[name="decision"]:checked');
    if (!chosen) {
      group.querySelector('input')?.focus();
      return;
    }
    const reason = form.querySelector('input[name="reason"]:checked');
    if (!reason) {
      reasons.querySelector('input')?.focus();
      return;
    }
    const option = decisionOptions(task, lang).find((o) => o.value === chosen.value);
    if (!option?.confirm) {
      onDecide(chosen.value, reason.value);
      return;
    }
    clear(confirmBox);
    const yes = el('button', { type: 'button' }, option.confirmLabel);
    const no = el('button', { type: 'button', class: 'secondary' }, t(lang, 'action.cancel'));
    yes.addEventListener('click', () => onDecide(chosen.value, reason.value));
    no.addEventListener('click', () => { confirmBox.hidden = true; submit.focus(); });
    confirmBox.append(el('p', { id: 'confirm-text' }, option.confirm), yes, no);
    confirmBox.hidden = false;
    yes.focus();
  });
  main.append(form);
}
