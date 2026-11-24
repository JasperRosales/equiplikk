import { api } from './api.js';
import { syncPasswordIcons, icon } from './icons.js';

export function esc(value) {
  if (value === null || value === undefined) return '';
  return String(value)
    .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;').replaceAll("'", '&#39;');
}

export function fmtDate(value) {
  if (!value) return '—';
  const date = new Date(String(value).length > 10 ? String(value) : String(value) + 'T00:00:00');
  return Number.isNaN(date.getTime()) ? String(value)
    : date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function fmtDateTime(value) {
  if (!value) return '—';
  const raw = String(value).replace(' ', 'T');
  // Backend timestamps are Asia/Manila local times; do not reinterpret them in the browser's zone.
  const date = new Date(/[zZ]|[+-]\d{2}:?\d{2}$/.test(raw) ? raw : raw + '+08:00');
  return Number.isNaN(date.getTime()) ? String(value)
    : date.toLocaleString(undefined, {
      timeZone: 'Asia/Manila', year: 'numeric', month: 'short', day: 'numeric',
      hour: 'numeric', minute: '2-digit', hour12: true
    });
}

export function fmtTime(value) {
  if (!value) return '—';
  const match = String(value).match(/^(\d{1,2}):(\d{2})/);
  if (!match) return String(value);
  let hour = Number(match[1]);
  const suffix = hour >= 12 ? 'PM' : 'AM';
  hour = hour % 12 || 12;
  return `${hour}:${match[2]} ${suffix}`;
}

/**
 * 12-hour time control equivalent to the desktop TimeInput: hour, minute and AM/PM.
 * The value returned by readTimeInput() is always 24-hour HH:mm for the database.
 */
export function timeInput(name, value = '') {
  const match = String(value || '').match(/^(\d{1,2}):(\d{2})/);
  let hour = match ? Number(match[1]) : 9;
  const minute = match ? match[2] : '00';
  const period = match && hour >= 12 ? 'PM' : 'AM';
  if (match) hour = hour % 12 || 12;
  return `<span class="time-input" data-time="${esc(name)}">
    <input name="${esc(name)}Hour" type="text" inputmode="numeric" pattern="[0-9]{1,2}" maxlength="2" value="${esc(String(hour))}" required aria-label="${esc(name)} hour">
    <span class="time-sep">:</span>
    <input name="${esc(name)}Minute" type="text" inputmode="numeric" pattern="[0-9]{1,2}" maxlength="2" value="${esc(minute)}" required aria-label="${esc(name)} minute">
    <select name="${esc(name)}Period" aria-label="${esc(name)} AM or PM">
      <option value="AM" ${period === 'AM' ? 'selected' : ''}>AM</option>
      <option value="PM" ${period === 'PM' ? 'selected' : ''}>PM</option>
    </select>
  </span>`;
}

export function readTimeInput(root, name) {
  const wrap = root.querySelector(`[data-time="${name}"]`);
  if (!wrap) throw new Error(`Missing time field: ${name}`);
  const hour = wrap.querySelector(`[name="${name}Hour"]`).value.trim();
  const minute = wrap.querySelector(`[name="${name}Minute"]`).value.trim();
  const period = wrap.querySelector(`[name="${name}Period"]`).value;
  if (!/^\d{1,2}$/.test(hour) || !/^\d{1,2}$/.test(minute)
      || Number(hour) < 1 || Number(hour) > 12 || Number(minute) > 59
      || !['AM', 'PM'].includes(period)) {
    throw new Error('Enter a valid time with AM or PM.');
  }
  let hour24 = Number(hour) % 12;
  if (period === 'PM') hour24 += 12;
  return `${String(hour24).padStart(2, '0')}:${String(Number(minute)).padStart(2, '0')}`;
}

export function statusBadge(status) {
  const value = String(status || 'PENDING').trim().toUpperCase();
  const cls = value.toLowerCase().replace(/[^a-z0-9]+/g, '_');
  // Labels mirror src/ui/UiSupport.displayStatus() in the desktop application.
  const labels = {
    RELEASED: 'Borrowed',
    RECEIVED: 'Received',
    PENDING_STAFF: 'Staff review',
    PENDING_ADMIN: 'Awaiting Facility Admin Approval',
    PENDING_DIRECTOR: 'Awaiting Campus Director Approval',
    REJECTED: 'Declined',
    NOT_ASSIGNED: 'Not Assigned',
    NOT_PROCESSED: 'Not Processed on Time',
    UNABLE: 'Unable to fulfill',
    NEEDS_ACTION: 'Needs action'
  };
  const label = labels[value] || value.toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
  return `<span class="badge ${esc(cls)}">${esc(label)}</span>`;
}

export function loading(message = 'Loading…') {
  return `<div class="card empty">${esc(message)}</div>`;
}

export function empty(message, action = '') {
  return `<div class="empty"><p>${esc(message)}</p>${action}</div>`;
}

export function alert(message, kind = 'error') {
  return message ? `<div class="alert ${esc(kind)}">${esc(message)}</div>` : '';
}

export function page(title, subtitle, actions, body) {
  return `
    <div class="stack">
      <div class="section-title">
        <div><h2>${esc(title)}</h2><p class="muted">${esc(subtitle || '')}</p></div>
        <div class="row">${actions || ''}</div>
      </div>
      ${body}
    </div>`;
}

export function table(headers, rows, emptyMessage = 'No records found.') {
  if (!rows.length) return empty(emptyMessage);
  const head = headers.map((header) => `<th>${esc(header)}</th>`).join('');
  const body = rows.map((row) => `<tr>${row.map((cell, index) =>
    `<td data-label="${esc(headers[index] || '')}">${cell}</td>`).join('')}</tr>`).join('');
  return `<div class="table-wrap"><table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>`;
}

export function toast(message, kind = '') {
  const root = document.getElementById('toastRoot');
  const node = document.createElement('div');
  node.className = `toast ${kind}`;
  node.textContent = message;
  root.appendChild(node);
  window.setTimeout(() => {
    node.classList.add('leaving');
    node.addEventListener('animationend', () => node.remove(), { once: true });
    window.setTimeout(() => node.remove(), 400);
  }, 4000);
}

export function openModal({ title, body, footer = '', wide = false }) {
  const root = document.getElementById('modalRoot');
  root.innerHTML = `
    <div class="modal ${wide ? 'wide' : ''}" role="dialog" aria-modal="true">
      <div class="modal-head"><h2>${esc(title)}</h2><button class="icon-btn" data-close aria-label="Close">${icon('x', 16)}</button></div>
      <div class="modal-body">${body}</div>
      ${footer ? `<div class="modal-foot">${footer}</div>` : ''}
    </div>`;
  root.classList.remove('hidden');
  syncPasswordIcons(root);
  const controller = new AbortController();
  const close = () => {
    controller.abort();
    root.classList.add('hidden');
    root.innerHTML = '';
  };
  root.querySelector('[data-close]').addEventListener('click', close);
  root.addEventListener('click', (event) => { if (event.target === root) close(); }, { signal: controller.signal });
  return { root, close };
}

export function confirmDialog(message, title = 'Please confirm') {
  return new Promise((resolve) => {
    const modal = openModal({
      title,
      body: `<p>${esc(message)}</p>`,
      footer: `<button class="btn" data-cancel>Cancel</button><button class="btn primary" data-ok>Continue</button>`
    });
    modal.root.querySelector('[data-cancel]').addEventListener('click', () => { modal.close(); resolve(false); });
    modal.root.querySelector('[data-ok]').addEventListener('click', () => { modal.close(); resolve(true); });
  });
}

export async function withBusy(button, action) {
  if (!button) return action();
  const original = button.innerHTML;
  button.disabled = true;
  button.innerHTML = 'Working…';
  try {
    return await action();
  } finally {
    if (button.isConnected) {
      button.disabled = false;
      button.innerHTML = original;
    }
  }
}

export function handleError(error) {
  toast(error.message || 'Something went wrong.', 'error');
}

export function initials(user) {
  const source = user?.fullName || user?.username || 'EL';
  return source.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || 'EL';
}

export function requestFormHtml(request) {
  return `
    <div class="stack">
      <div class="row spread">
        <div><strong>${esc(request.requestCode)}</strong> ${statusBadge(request.status)}</div>
        <span class="muted">Submitted ${esc(fmtDateTime(request.submittedAt))}</span>
      </div>
      <dl class="kv">
        <dt>Borrower</dt><dd>${esc(request.borrowerName)}</dd>
        <dt>Department</dt><dd>${esc(request.borrowerDepartment)}</dd>
        <dt>Type</dt><dd>${esc(request.borrowerType)}</dd>
        <dt>Venue</dt><dd>${esc(request.venue)}</dd>
        <dt>Event date</dt><dd>${esc(fmtDate(request.eventDate))}</dd>
        <dt>Time</dt><dd>${esc(fmtTime(request.startTime))} – ${esc(fmtTime(request.endTime))}</dd>
        <dt>Purpose</dt><dd>${esc(request.purpose)}</dd>
        <dt>Participants</dt><dd>${esc(request.participants)}</dd>
        <dt>Person in charge</dt><dd>${esc(request.personInCharge)}</dd>
        <dt>Position</dt><dd>${esc(request.position)}</dd>
        <dt>Borrower remarks</dt><dd>${esc(request.borrowerRemarks) || '—'}</dd>
        ${request.urgent ? `<dt>Urgent reason</dt><dd>${esc(request.urgentReason)}</dd>` : ''}
      </dl>
      ${(request.occurrences || []).length ? `<h3>Schedules</h3>${table(['Date', 'Start', 'End', 'Venue'],
        request.occurrences.map((o) => [esc(fmtDate(o.eventDate)), esc(fmtTime(o.startTime)), esc(fmtTime(o.endTime)), esc(o.venue)]))}` : ''}
      <h3>Equipment</h3>
      ${table(['Code', 'Equipment', 'Category', 'Requested', 'Assigned', 'Released', 'Returned'],
        (request.equipmentItems || []).map((item) => [
          esc(item.equipmentCode), esc(item.equipmentName), esc(item.category),
          esc(item.requestedQuantity), esc(item.assignedQuantity), esc(item.releasedQuantity), esc(item.returnedQuantity)
        ]))}
    </div>`;
}

export async function showRequest(id, options = {}) {
  const [request, audit] = await Promise.all([
    api.request(id),
    api.requestAudit(id).catch(() => [])
  ]);
  const departments = request.departments || [];
  const extras = `
    ${departments.length ? `<h3>Department decisions</h3>${table(['Department', 'Status', 'Remarks'],
      departments.map((d) => [esc(d.custodianRole), statusBadge(d.status), esc(d.remarks) || '—']))}` : ''}
    ${(audit.length && options.audit !== false) ? `<h3>Approval trail</h3><div class="timeline">${audit.map((event) => `
      <div class="timeline-item"><strong>${esc(event.eventType || event.status || 'Update')}</strong>
      <div class="muted">${esc(fmtDateTime(event.createdAt))} · ${esc(event.actorName || 'System')}</div>
      <div>${esc(event.remarks || '')}</div></div>`).join('')}</div>` : ''}`;
  const footer = options.footer || `<button class="btn" data-close-modal>Close</button>`;
  const modal = openModal({ title: 'Request details', body: requestFormHtml(request) + extras, footer, wide: true });
  modal.root.querySelector('[data-close-modal]')?.addEventListener('click', modal.close);
  return { modal, request };
}
