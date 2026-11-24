import { api } from '../api.js';
import { state } from '../state.js';
import { esc, fmtDate, fmtTime, statusBadge, table, page, empty, handleError, toast, openModal, confirmDialog, withBusy } from '../ui.js';

export async function renderStaffQueue(container) {
  container.innerHTML = '<div class="card empty">Loading staff queue…</div>';
  try {
    const rows = await api.queue();
    const body = rows.map((request) => [
      `<strong>${esc(request.requestCode)}</strong>`, esc(request.borrowerName),
      esc(fmtDate(request.eventDate)), esc(request.venue), statusBadge(request.status),
      request.urgent ? '<span class="badge urgent">Urgent</span>' : '—',
      `<button class="btn small primary" data-open="${request.requestId}">Process</button>`
    ]);
    container.innerHTML = page('Requests', 'Assign, decline, release and inspect equipment for your department', '',
      table(['Code', 'Borrower', 'Event date', 'Venue', 'Status', 'Priority', ''], body, 'No requests are waiting for your department.'));
    container.querySelectorAll('[data-open]').forEach((button) =>
      button.addEventListener('click', async () => {
        try { await openStaffActions(Number(button.dataset.open), () => renderStaffQueue(container)); }
        catch (error) { handleError(error); }
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
    const rows = schedule.map((request) => [
      esc(request.requestCode), esc(fmtDate(request.eventDate)),
      esc(fmtTime(request.startTime)), esc(fmtTime(request.endTime)), esc(request.venue),
      esc(request.borrowerName), statusBadge(request.status),
      `<button class="btn small secondary" data-open="${request.requestId}">Open</button>`
    ]);
    const conflictRows = conflicts.map((conflict) => [
      esc(conflict.venue || conflict.room), esc(fmtDate(conflict.eventDate || conflict.date)),
      esc(conflict.firstRequestCode || conflict.requestCodeA), esc(conflict.secondRequestCode || conflict.requestCodeB),
      esc(conflict.note || '')
    ]);
    container.innerHTML = page('Schedule & Assignment', 'Reserved venues and overlapping bookings', '', `
      <div class="card"><div class="section-title"><h3>Upcoming schedule</h3></div>
        ${table(['Code', 'Date', 'Start', 'End', 'Venue', 'Borrower', 'Status', ''], rows, 'No scheduled requests.')}</div>
      <div class="card"><div class="section-title"><h3>Room conflicts</h3></div>
        ${table(['Venue', 'Date', 'Request A', 'Request B', 'Note'], conflictRows, 'No overlapping room bookings detected.')}</div>`);
    container.querySelectorAll('[data-open]').forEach((button) =>
      button.addEventListener('click', async () => {
        try { await openStaffActions(Number(button.dataset.open), () => renderSchedule(container)); }
        catch (error) { handleError(error); }
      }));
  } catch (error) {
    container.innerHTML = page('Schedule & Assignment', '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
}

export async function renderTransactions(container) {
  container.innerHTML = '<div class="card empty">Loading transaction history…</div>';
  try {
    const rows = await api.transactions();
    const body = rows.map((request) => [
      esc(request.requestCode), esc(request.borrowerName), esc(fmtDate(request.eventDate)),
      esc(request.venue), statusBadge(request.status), esc(fmtDate(request.finalApprovedAt)),
      `<a class="btn small secondary" href="${api.exportForm(request.requestId)}" target="_blank" rel="noopener">Form</a>`
    ]);
    container.innerHTML = page('Transaction History', 'Append-only history of processed requests', '',
      table(['Code', 'Borrower', 'Event date', 'Venue', 'Status', 'Approved', ''], body, 'No transactions found.'));
  } catch (error) {
    container.innerHTML = page('Transaction History', '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
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
