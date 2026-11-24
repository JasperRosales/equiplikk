import { api } from '../api.js';
import { state } from '../state.js';
import { esc, fmtDate, fmtTime, statusBadge, table, page, empty, handleError, toast, openModal, confirmDialog, withBusy, showRequest } from '../ui.js';

let scheduleView = null;

export async function renderStaffQueue(container) {
  container.innerHTML = '<div class="card empty">Loading staff queue…</div>';
  try {
    const rows = await api.queue();
    const body = rows.map((request) => [
      `<strong>${esc(request.requestCode)}</strong>`, esc(request.borrowerName),
      dateLabel(request.eventDate), esc(request.venue), statusBadge(request.status),
      request.urgent ? '<span class="badge urgent">Urgent</span>' : '—',
      `<button class="btn small primary" data-open="${request.requestId}">View Details</button>`
    ]);
    container.innerHTML = page('Requests', 'Assign, decline, release and inspect equipment for your department', '',
      table(['Code', 'Borrower', 'Event date', 'Venue', 'Status', 'Priority', 'Details'], body, 'No requests are waiting for your department.'));
    container.querySelectorAll('[data-open]').forEach((button) =>
      button.addEventListener('click', async () => {
        const id = Number(button.dataset.open);
        try {
          const { modal } = await showRequest(id, {
            footer: `<button class="btn" data-close-modal>Close</button><button class="btn primary" id="queueStaffActions">Staff actions</button>`
          });
          modal.root.querySelector('#queueStaffActions').addEventListener('click', async () => {
            modal.close();
            await openStaffActions(id, () => renderStaffQueue(container));
          });
        } catch (error) { handleError(error); }
      }));
  } catch (error) {
    container.innerHTML = page('Requests', '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
}

export async function renderSchedule(container) {
  container.innerHTML = '<div class="card empty">Loading schedule…</div>';
  try {
    const [schedule, conflicts] = await Promise.all([
      api.schedule(), api.roomConflicts().catch(() => [])
    ]);
    const entries = scheduleEntries(schedule);
    const view = ensureScheduleView();
    const counts = countByDate(entries);
    const statuses = [...new Set(schedule.map((request) => request.status).filter(Boolean))].sort();

    container.innerHTML = page('Schedule & Assignment', 'Reserved venues, overlapping bookings and dated assignments', `
      <div class="filters">
        <label class="field">Search schedule
          <input id="scheduleSearch" type="search" value="${esc(view.search)}" placeholder="Code, borrower or venue">
        </label>
        <label class="field">Status
          <select id="scheduleStatus"><option value="">All statuses</option>
            ${statuses.map((status) => `<option value="${esc(status)}" ${view.status === status ? 'selected' : ''}>${esc(status.replaceAll('_', ' '))}</option>`).join('')}
          </select>
        </label>
      </div>`, `
      <div id="scheduleCalendar"></div>
      <div class="card stack">
        <div class="section-title"><h3>Selected date requests</h3><span class="hint" id="selectedDateLabel"></span></div>
        <div id="selectedDateTable"></div>
      </div>
      <div class="card stack">
        <div class="section-title"><h3>Upcoming requests</h3><span class="hint">Future occurrences from the same shared schedule</span></div>
        <div id="upcomingScheduleTable"></div>
      </div>
      <div class="card"><div class="section-title"><h3>Room conflicts</h3></div>
        ${table(['Venue', 'Date', 'Request A', 'Request B', 'Note'], conflicts.map((conflict) => [
          esc(conflict.venue || conflict.room), dateLabel(conflict.eventDate || conflict.date),
          esc(conflict.firstRequestCode || conflict.requestCodeA), esc(conflict.secondRequestCode || conflict.requestCodeB),
          esc(conflict.note || '')
        ]), 'No overlapping room bookings detected.')}</div>`);

    const paintCalendar = () => {
      container.querySelector('#scheduleCalendar').innerHTML = calendarHtml(view, counts);
      container.querySelector('[data-cal-prev]').addEventListener('click', () => shiftMonth(view, -1, paintAll));
      container.querySelector('[data-cal-next]').addEventListener('click', () => shiftMonth(view, 1, paintAll));
      container.querySelector('[data-cal-today]').addEventListener('click', () => {
        const today = manilaToday();
        const parts = dateParts(today);
        view.selectedDate = today; view.year = parts.year; view.month = parts.month;
        paintAll();
      });
      container.querySelectorAll('[data-date]').forEach((button) =>
        button.addEventListener('click', () => {
          view.selectedDate = button.dataset.date;
          const parts = dateParts(view.selectedDate);
          view.year = parts.year; view.month = parts.month;
          paintAll();
        }));
    };

    const paintTables = () => {
      const filtered = filterEntries(entries, view);
      const selected = filtered.filter((entry) => entry.dateKey === view.selectedDate).sort(byTime);
      const upcoming = filtered.filter((entry) => entry.dateKey >= manilaToday()).sort(byDateThenTime);
      container.querySelector('#selectedDateLabel').textContent = fmtDate(view.selectedDate);
      container.querySelector('#selectedDateTable').innerHTML = table(
        ['Code', 'Date', 'Start', 'End', 'Venue', 'Borrower', 'Request', 'Dept. status', ''],
        selected.map((entry) => scheduleRow(entry)), 'No requests are scheduled on this date.');
      container.querySelector('#upcomingScheduleTable').innerHTML = table(
        ['Code', 'Date', 'Start', 'End', 'Venue', 'Borrower', 'Request', 'Dept. status', ''],
        upcoming.map((entry) => scheduleRow(entry)), 'No upcoming requests match these filters.');
      bindScheduleDetails(container, () => renderSchedule(container));
    };

    const paintAll = () => { paintCalendar(); paintTables(); };

    container.querySelector('#scheduleSearch').addEventListener('input', (event) => {
      view.search = event.target.value;
      paintTables();
    });
    container.querySelector('#scheduleStatus').addEventListener('change', (event) => {
      view.status = event.target.value;
      paintTables();
    });
    paintAll();
  } catch (error) {
    container.innerHTML = page('Schedule & Assignment', '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
}

export async function renderTransactions(container) {
  container.innerHTML = '<div class="card empty">Loading transaction history…</div>';
  try {
    const rows = await api.transactions();
    const body = rows.map((request) => [
      esc(request.requestCode), esc(request.borrowerName), dateLabel(request.eventDate),
      esc(request.venue), statusBadge(request.status), dateLabel(request.finalApprovedAt),
      `<a class="btn small secondary" href="${api.exportForm(request.requestId)}" target="_blank" rel="noopener">Form</a>`
    ]);
    container.innerHTML = page('Transaction History', 'Append-only history of processed requests', '',
      table(['Code', 'Borrower', 'Event date', 'Venue', 'Status', 'Approved', ''], body, 'No transactions found.'));
  } catch (error) {
    container.innerHTML = page('Transaction History', '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
}

function ensureScheduleView() {
  if (!scheduleView) {
    const today = manilaToday();
    const parts = dateParts(today);
    scheduleView = { year: parts.year, month: parts.month, selectedDate: today, search: '', status: '' };
  }
  return scheduleView;
}

function manilaToday() {
  return new Date().toLocaleDateString('en-CA', {
    timeZone: 'Asia/Manila', year: 'numeric', month: '2-digit', day: '2-digit'
  });
}

function dateParts(value) {
  const pieces = String(value).slice(0, 10).split('-').map(Number);
  if (pieces.length !== 3 || pieces.some((piece) => !Number.isFinite(piece))) {
    const today = dateParts(manilaToday());
    return today;
  }
  return { year: pieces[0], month: pieces[1], day: pieces[2] };
}

function dateKey(value) {
  return String(value || '').slice(0, 10);
}

function pad(value) {
  return String(value).padStart(2, '0');
}

function scheduleEntries(requests) {
  const entries = [];
  (requests || []).forEach((request) => {
    const occurrences = request.occurrences?.length
      ? request.occurrences
      : [{ eventDate: request.eventDate, startTime: request.startTime, endTime: request.endTime, venue: request.venue }];
    occurrences.forEach((occurrence) => {
      const key = dateKey(occurrence.eventDate || request.eventDate);
      if (!key) return;
      entries.push({ request, occurrence, dateKey: key });
    });
  });
  return entries;
}

function countByDate(entries) {
  const counts = new Map();
  entries.forEach((entry) => counts.set(entry.dateKey, (counts.get(entry.dateKey) || 0) + 1));
  return counts;
}

function calendarHtml(view, counts) {
  const firstDay = new Date(view.year, view.month - 1, 1);
  const leading = firstDay.getDay();
  const days = new Date(view.year, view.month, 0).getDate();
  const today = manilaToday();
  const weekdays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  const cells = [];
  for (let index = 0; index < leading; index += 1) {
    cells.push('<span class="calendar-day blank" aria-hidden="true"></span>');
  }
  for (let day = 1; day <= days; day += 1) {
    const key = `${view.year}-${pad(view.month)}-${pad(day)}`;
    const count = counts.get(key) || 0;
    const classes = ['calendar-day'];
    if (key === view.selectedDate) classes.push('selected');
    if (key === today) classes.push('today');
    if (count) classes.push('has-requests');
    cells.push(`<button type="button" class="${classes.join(' ')}" data-date="${key}" aria-label="${key}${count ? `, ${count} request${count === 1 ? '' : 's'}` : ''}">
      <span class="day-number">${day}</span>
      ${count ? `<span class="calendar-count">${count}</span>` : ''}
    </button>`);
  }
  return `<div class="card calendar-card stack">
    <div class="section-title">
      <div><h3>${esc(firstDay.toLocaleDateString(undefined, { year: 'numeric', month: 'long' }))}</h3>
        <p class="hint">Highlighted dates have scheduled requests. Click a date to inspect it.</p></div>
      <div class="row">
        <button type="button" class="btn small" data-cal-prev>‹ Previous</button>
        <button type="button" class="btn small" data-cal-today>Today</button>
        <button type="button" class="btn small" data-cal-next>Next ›</button>
      </div>
    </div>
    <div class="calendar-grid calendar-weekdays">${weekdays.map((day) => `<span>${day}</span>`).join('')}</div>
    <div class="calendar-grid">${cells.join('')}</div>
  </div>`;
}

function shiftMonth(view, amount, repaint) {
  let month = view.month + amount;
  let year = view.year;
  if (month < 1) { month = 12; year -= 1; }
  if (month > 12) { month = 1; year += 1; }
  view.year = year;
  view.month = month;
  const selected = dateParts(view.selectedDate);
  if (selected.year !== year || selected.month !== month) {
    view.selectedDate = `${year}-${pad(month)}-01`;
  }
  repaint();
}

function filterEntries(entries, view) {
  const search = view.search.trim().toLowerCase();
  return entries.filter((entry) => {
    if (view.status && entry.request.status !== view.status) return false;
    if (!search) return true;
    return [entry.request.requestCode, entry.request.borrowerName, entry.occurrence.venue,
      entry.request.purpose].filter(Boolean).join(' ').toLowerCase().includes(search);
  });
}

function byTime(left, right) {
  return String(left.occurrence.startTime || '').localeCompare(String(right.occurrence.startTime || ''));
}

function byDateThenTime(left, right) {
  return left.dateKey.localeCompare(right.dateKey) || byTime(left, right);
}

function dateLabel(value) {
  return esc(fmtDate(value));
}

function scheduleRow(entry) {
  const request = entry.request;
  const occurrence = entry.occurrence;
  const group = (request.departments || []).find((item) => item.custodianRole === state.user?.role);
  return [
    `<strong>${esc(request.requestCode)}</strong>`,
    dateLabel(occurrence.eventDate),
    esc(fmtTime(occurrence.startTime)),
    esc(fmtTime(occurrence.endTime)),
    esc(occurrence.venue || request.venue),
    esc(request.borrowerName),
    statusBadge(request.status),
    group ? statusBadge(group.status) : '—',
    `<button class="btn small secondary" data-view-detail="${request.requestId}">View Details</button>`
  ];
}

function bindScheduleDetails(container, refresh) {
  container.querySelectorAll('[data-view-detail]').forEach((button) =>
    button.addEventListener('click', async () => {
      try { await openStaffActions(Number(button.dataset.viewDetail), refresh); }
      catch (error) { handleError(error); }
    }));
}

export async function openStaffActions(id, refresh) {
  const [request, availability] = await Promise.all([
    api.request(id), api.assignmentAvailability(id).catch(() => ({}))
  ]);
  const groups = (request.departments || []).filter((d) => d.custodianRole === state.user.role);
  const status = String(request.status || '');
  const early = ['PENDING', 'PENDING_STAFF', 'NEEDS_ACTION', 'PARTIALLY_UNABLE'].some((s) => status.includes(s));
  const ready = ['APPROVED', 'PARTIALLY_APPROVED'].some((s) => status.includes(s));
  const released = ['RELEASED', 'PARTIALLY_RELEASED', 'RECEIVED'].some((s) => status.includes(s));

  const buttons = [];
  if (early) {
    buttons.push('<button class="btn primary" data-action="assign">Assign equipment</button>');
    buttons.push('<button class="btn danger" data-action="decline">Decline</button>');
    buttons.push('<button class="btn" data-action="needs-action">Needs action</button>');
  }
  if (ready) buttons.push('<button class="btn primary" data-action="release">Release equipment</button>');
  if (released) buttons.push('<button class="btn primary" data-action="return">Return inspection</button>');
  if (!buttons.length) buttons.push('<button class="btn" data-close-modal>Close</button>');

  const modal = openModal({
    title: `Process ${request.requestCode}`,
    body: `<div class="stack">
      <div class="row">${statusBadge(request.status)} <span class="muted">${esc(request.borrowerName)} · ${esc(fmtDate(request.eventDate))}</span></div>
      <p class="muted">Choose an action. The server re-checks your role and the live request status.</p>
      <div class="grid two">
        ${(request.equipmentItems || []).map((item) => `<div class="card">
          <strong>${esc(item.equipmentName)}</strong>
          <div class="muted">${esc(item.equipmentCode)} · requested ${esc(item.requestedQuantity)} · assigned ${esc(item.assignedQuantity)} · released ${esc(item.releasedQuantity)}</div>
          <div class="badge">${esc(availability[item.equipmentId] ?? '—')} available</div>
        </div>`).join('') || empty('No equipment items.')}
      </div>
    </div>`,
    footer: `<button class="btn" data-close-modal>Close</button>${buttons.join('')}`
  });
  modal.root.querySelector('[data-close-modal]').addEventListener('click', modal.close);
  modal.root.querySelectorAll('[data-action]').forEach((button) =>
    button.addEventListener('click', () => {
      const action = button.dataset.action;
      modal.close();
      openActionModal(action, request, groups, availability, refresh);
    }));
}

function openActionModal(action, request, groups, availability, refresh) {
  const titles = {
    assign: 'Assign equipment', decline: 'Decline request', 'needs-action': 'Ask borrower for changes',
    release: 'Release equipment', return: 'Return inspection'
  };
  let body = '';
  if (action === 'assign') {
    body = `<form id="actionForm" class="stack">
      ${groupSelect(groups)}
      ${(request.equipmentItems || []).map((item) => {
        const max = Number(availability[item.equipmentId] ?? 0);
        const value = Math.max(0, Math.min(item.requestedQuantity - item.assignedQuantity, max || item.requestedQuantity));
        return `<div class="item-row">
          <label class="field">${esc(item.equipmentName)} <span class="muted">(requested ${esc(item.requestedQuantity)}, assigned ${esc(item.assignedQuantity)})</span>
            <input type="number" min="0" max="${esc(max || item.requestedQuantity)}" value="${esc(value)}" data-assign="${item.equipmentId}"></label>
          <label class="field">Available<input value="${esc(max)}" disabled></label><span></span>
        </div>`;
      }).join('')}
      <label class="field">Remarks for partial or unfulfilled items<textarea name="remarks" maxlength="1000"></textarea></label>
    </form>`;
  } else if (action === 'decline' || action === 'needs-action') {
    body = `<form id="actionForm" class="stack">
      ${groupSelect(groups)}
      <label class="field">Reason<textarea name="remarks" maxlength="1000" required></textarea></label>
    </form>`;
  } else if (action === 'release') {
    body = `<form id="actionForm" class="stack">
      ${groupSelect(groups)}
      ${(request.equipmentItems || []).filter((item) => item.assignedQuantity > 0).map((item) => `
        <div class="grid two">
          <label class="field">${esc(item.equipmentName)} — initial condition
            <input name="condition-${item.equipmentId}" value="Good" required></label>
          <label class="field">Remarks<input name="remarks-${item.equipmentId}"></label>
        </div>`).join('') || empty('No assigned equipment to release.')}
    </form>`;
  } else if (action === 'return') {
    body = `<form id="actionForm" class="stack">
      ${(request.equipmentItems || []).filter((item) => item.releasedQuantity > 0).map((item) => `
        <div class="card stack">
          <strong>${esc(item.equipmentName)}</strong>
          <div class="grid three">
            <label class="field">Returned condition<input name="condition-${item.equipmentId}" value="Good" required></label>
            <label class="field">Damaged qty<input name="damaged-${item.equipmentId}" type="number" min="0" max="${item.releasedQuantity}" value="0"></label>
            <label class="field">Missing qty<input name="missing-${item.equipmentId}" type="number" min="0" max="${item.releasedQuantity}" value="0"></label>
          </div>
          <label class="field">Inspection remarks<input name="remarks-${item.equipmentId}"></label>
        </div>`).join('') || empty('No released equipment to inspect.')}
    </form>`;
  }

  const modal = openModal({
    title: titles[action] || 'Action',
    body,
    footer: `<button class="btn" data-close>Cancel</button><button class="btn primary" id="submitAction">Confirm</button>`
  });
  modal.root.querySelector('[data-close]').addEventListener('click', modal.close);
  modal.root.querySelector('#submitAction').addEventListener('click', async (event) => {
    const form = modal.root.querySelector('#actionForm');
    await withBusy(event.currentTarget, async () => {
      try {
        const groupId = Number(form.querySelector('[name=groupId]')?.value || 0);
        if (action === 'assign') {
          const assignments = [...form.querySelectorAll('[data-assign]')].map((input) => ({
            equipmentId: Number(input.dataset.assign), quantity: Number(input.value),
            remarks: form.remarks.value
          }));
          await api.assign(request.requestId, assignments);
        } else if (action === 'decline') {
          await api.decline(request.requestId, form.remarks.value);
        } else if (action === 'needs-action') {
          await api.needsAction(request.requestId, form.remarks.value);
        } else if (action === 'release') {
          const conditions = (request.equipmentItems || []).filter((item) => item.assignedQuantity > 0).map((item) => ({
            equipmentId: item.equipmentId,
            condition: form.querySelector(`[name="condition-${item.equipmentId}"]`).value,
            remarks: form.querySelector(`[name="remarks-${item.equipmentId}"]`).value
          }));
          await api.release(request.requestId, groupId || null, conditions);
        } else if (action === 'return') {
          const inspections = (request.equipmentItems || []).filter((item) => item.releasedQuantity > 0).map((item) => ({
            equipmentId: item.equipmentId,
            condition: form.querySelector(`[name="condition-${item.equipmentId}"]`).value,
            damagedQuantity: Number(form.querySelector(`[name="damaged-${item.equipmentId}"]`).value),
            missingQuantity: Number(form.querySelector(`[name="missing-${item.equipmentId}"]`).value),
            remarks: form.querySelector(`[name="remarks-${item.equipmentId}"]`).value
          }));
          await api.returned(request.requestId, inspections);
        }
        modal.close();
        toast('Request updated.', 'success');
        refresh?.();
      } catch (error) { handleError(error); }
    });
  });
}

function groupSelect(groups) {
  if (groups.length < 2) return groups.length === 1
    ? `<input type="hidden" name="groupId" value="${groups[0].groupId}">` : '';
  return `<label class="field">Department group
    <select name="groupId">${groups.map((group) => `<option value="${group.groupId}">${esc(group.custodianRole)} · ${esc(group.status || '')}</option>`).join('')}</select>
  </label>`;
}
