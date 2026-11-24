import { api } from '../api.js';
import { state } from '../state.js';
import { router } from '../router.js';
import {
  esc, fmtDate, statusBadge, table, page, empty, handleError, toast,
  openModal, confirmDialog, requestFormHtml, withBusy
} from '../ui.js';
import { openStaffActions } from './staff.js';

const LOADERS = {
  active: api.activeRequests,
  history: api.requestHistory,
  all: api.allRequests,
  queue: api.queue,
  'borrowing-requests': api.queue,
  approval: api.queue,
  transactions: api.transactions
};

const TITLES = {
  active: ['My Requests', 'Requests currently moving through review, release and return'],
  history: ['Borrowing Request', 'Completed and historical borrowing records'],
  all: ['All Requests', 'Every request visible to your account'],
  queue: ['Requests', 'Requests waiting for your department'],
  'borrowing-requests': ['Borrowing Requests', 'Requests waiting for Facility Admin review'],
  approval: ['Request Approval', 'Requests waiting for the final Campus Director decision'],
  transactions: ['Transaction History', 'Audit trail of requests handled in the system']
};

const DATE_FILTERS = [
  ['request', 'Request date'],
  ['event', 'Event / schedule date'],
  ['approval', 'Approval date']
];

/** Search text survives a hash-driven status/date rerender without polluting the URL. */
const searchByMode = new Map();

export async function renderRequests(container, mode = 'active') {
  const [title, subtitle] = TITLES[mode] || TITLES.active;
  container.innerHTML = '<div class="card empty">Loading requests…</div>';
  try {
    const loader = LOADERS[mode] || LOADERS.active;
    const rows = await loader();

    const params = state.params;
    const status = params.get('status') || '';
    const statusList = (params.get('statuses') || '').split(',')
      .map((value) => value.trim()).filter(Boolean);
    const approvedToday = params.get('approvedToday') === '1';
    const today = manilaDate();
    const dateField = params.get('dateField') || (approvedToday ? 'approval' : '');
    const dateStart = params.get('dateStart') || (approvedToday ? today : '');
    const dateEnd = params.get('dateEnd') || (approvedToday ? today : '');
    const showDateFilters = mode === 'all';
    const search = searchByMode.get(mode) || '';
    const statuses = [...new Set([
      ...rows.map((row) => row.status), status, ...statusList
    ].filter(Boolean))].sort();
    const selectedStatus = status || (statusList.length === 1 ? statusList[0] : '');

    container.innerHTML = page(title, subtitle, `
      <div class="filters">
        <label class="field">Search requests
          <input id="requestSearch" type="search" value="${esc(search)}" placeholder="Code, borrower, venue or purpose">
        </label>
        <label class="field">Status filter
          <select id="statusFilter"><option value="">All statuses</option>
            ${statuses.map((value) => `<option value="${esc(value)}" ${selectedStatus === value ? 'selected' : ''}>${esc(value.replaceAll('_', ' '))}</option>`).join('')}
          </select>
        </label>
      </div>
      ${showDateFilters ? `
      <div class="filters date-filters">
        <label class="field">Date filter
          <select id="requestDateField">
            <option value="">No date filter</option>
            ${DATE_FILTERS.map(([value, label]) => `<option value="${value}" ${dateField === value ? 'selected' : ''}>${label}</option>`).join('')}
          </select>
        </label>
        <label class="field">From
          <input id="requestDateStart" type="date" value="${esc(dateStart)}">
        </label>
        <label class="field">To
          <input id="requestDateEnd" type="date" value="${esc(dateEnd)}">
        </label>
        <button type="button" class="btn small" id="applyDateFilter">Apply dates</button>
        <button type="button" class="btn small" id="clearDateFilter">Clear date filter</button>
      </div>` : ''}
      ${activeFiltersHtml({ status, statusList, approvedToday, dateField, dateStart, dateEnd, today })}
    `, `<div id="requestTable"></div>`);

    const paint = () => {
      const value = container.querySelector('#requestSearch').value.trim().toLowerCase();
      searchByMode.set(mode, container.querySelector('#requestSearch').value);
      const filtered = rows.filter((request) => {
        if (status && request.status !== status) return false;
        if (statusList.length && !statusList.includes(request.status)) return false;
        if (showDateFilters && approvedToday) {
          const approvedDay = dayOnly(request.finalApprovedAt);
          if (!approvedDay || approvedDay !== today
            || ['REJECTED', 'CANCELLED'].includes(request.status)) return false;
        }
        if (showDateFilters && dateField
          && !matchesDate(request, dateField, dateStart, dateEnd)) return false;
        if (!value) return true;
        return [request.requestCode, request.borrowerName, request.borrowerDepartment,
          request.venue, request.purpose]
          .filter(Boolean).join(' ').toLowerCase().includes(value);
      });
      const body = filtered.map((request) => [
        `<strong>${esc(request.requestCode)}</strong>`,
        esc(request.borrowerName),
        dateSummary(request),
        esc(request.venue),
        statusBadge(request.status),
        request.urgent ? '<span class="badge urgent">Urgent</span>' : '—',
        `<button class="btn small secondary" data-detail="${request.requestId}">View Details</button>`
      ]);
      container.querySelector('#requestTable').innerHTML = table(
        ['Code', 'Borrower', 'Event date(s)', 'Venue', 'Status', 'Priority', 'Details'],
        body, 'No requests match this view.');
      container.querySelectorAll('[data-detail]').forEach((button) =>
        button.addEventListener('click', async () => {
          try { await openRequestDetail(Number(button.dataset.detail), mode, () => renderRequests(container, mode)); }
          catch (error) { handleError(error); }
        }));
    };

    container.querySelector('#requestSearch').addEventListener('input', paint);
    container.querySelector('#statusFilter').addEventListener('change', (event) => {
      const values = readFilters(container, { statusList, approvedToday });
      values.status = event.target.value;
      values.statusList = [];
      router.go(routeWithParams(mode, buildParams(values)));
    });
    container.querySelector('#applyDateFilter')?.addEventListener('click', () => {
      const values = readFilters(container, { statusList, approvedToday });
      values.approvedToday = false;
      router.go(routeWithParams(mode, buildParams(values)));
    });
    container.querySelector('#clearDateFilter')?.addEventListener('click', () => {
      const values = readFilters(container, { statusList, approvedToday });
      values.dateField = '';
      values.dateStart = '';
      values.dateEnd = '';
      values.approvedToday = false;
      router.go(routeWithParams(mode, buildParams(values)));
    });
    container.querySelectorAll('[data-clear-filter]').forEach((button) =>
      button.addEventListener('click', () => {
        const values = readFilters(container, { statusList, approvedToday });
        switch (button.dataset.clearFilter) {
          case 'approvedToday':
          case 'date':
            values.dateField = '';
            values.dateStart = '';
            values.dateEnd = '';
            values.approvedToday = false;
            break;
          case 'status':
            values.status = '';
            values.statusList = [];
            break;
          default:
            searchByMode.delete(mode);
            router.go(mode);
            return;
        }
        router.go(routeWithParams(mode, buildParams(values)));
      }));
    paint();
  } catch (error) {
    container.innerHTML = page(title, '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
}

function readFilters(container, current) {
  return {
    status: container.querySelector('#statusFilter')?.value || '',
    statusList: current.statusList || [],
    approvedToday: Boolean(current.approvedToday),
    dateField: container.querySelector('#requestDateField')?.value || '',
    dateStart: container.querySelector('#requestDateStart')?.value || '',
    dateEnd: container.querySelector('#requestDateEnd')?.value || ''
  };
}

function buildParams(values) {
  const next = new URLSearchParams();
  if (values.status) next.set('status', values.status);
  if (values.statusList?.length) next.set('statuses', values.statusList.join(','));
  if (values.approvedToday) next.set('approvedToday', '1');
  if (values.dateField) {
    next.set('dateField', values.dateField);
    if (values.dateStart) next.set('dateStart', values.dateStart);
    if (values.dateEnd) next.set('dateEnd', values.dateEnd);
  }
  return next;
}

function routeWithParams(mode, params) {
  const query = params.toString();
  return query ? `${mode}?${query}` : mode;
}

function activeFiltersHtml(filters) {
  const chips = [];
  if (filters.approvedToday) {
    chips.push(`<span class="filter-chip">Approved today · ${esc(fmtDate(filters.today))}
      <button type="button" data-clear-filter="approvedToday" aria-label="Clear approved today filter">×</button></span>`);
  }
  if (filters.status) {
    chips.push(`<span class="filter-chip">Status: ${esc(filters.status.replaceAll('_', ' '))}
      <button type="button" data-clear-filter="status" aria-label="Clear status filter">×</button></span>`);
  } else if (filters.statusList.length) {
    chips.push(`<span class="filter-chip">Status: ${esc(filters.statusList.map((value) => value.replaceAll('_', ' ')).join(' / '))}
      <button type="button" data-clear-filter="status" aria-label="Clear status filter">×</button></span>`);
  }
  if (filters.dateField && !filters.approvedToday) {
    const label = DATE_FILTERS.find(([value]) => value === filters.dateField)?.[1] || 'Date';
    chips.push(`<span class="filter-chip">${esc(label)}: ${esc(filters.dateStart || 'any')} – ${esc(filters.dateEnd || 'any')}
      <button type="button" data-clear-filter="date" aria-label="Clear date filter">×</button></span>`);
  }
  if (!chips.length) return '';
  return `<div class="active-filters"><span class="muted">Active filters:</span>${chips.join('')}
    <button type="button" class="btn small" data-clear-filter="all">Clear all</button></div>`;
}

function manilaDate() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Manila', year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(new Date());
  const part = (type) => parts.find((item) => item.type === type)?.value || '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

function dayOnly(value) {
  return value ? String(value).slice(0, 10) : '';
}

function inRange(value, start, end) {
  const day = dayOnly(value);
  if (!day) return false;
  if (start && day < start) return false;
  if (end && day > end) return false;
  return true;
}

function matchesDate(request, field, start, end) {
  if (field === 'request') return inRange(request.submittedAt, start, end);
  if (field === 'approval') return inRange(request.finalApprovedAt, start, end);
  if (field === 'event') {
    const occurrences = request.occurrences || [];
    const dates = occurrences.length
      ? occurrences.map((occurrence) => occurrence.eventDate)
      : [request.eventDate];
    return dates.some((date) => inRange(date, start, end));
  }
  return true;
}

function dateSummary(request) {
  const occurrences = request.occurrences || [];
  const dates = (occurrences.length ? occurrences : [{ eventDate: request.eventDate }])
    .map((occurrence) => fmtDate(occurrence.eventDate)).filter((date) => date && date !== '—');
  if (!dates.length) return '—';
  const summary = dates.length <= 2 ? dates.join(' · ') : `${dates[0]} · ${dates[1]} · +${dates.length - 2} more`;
  return `<span class="date-summary" title="${esc(dates.join('\n'))}">${esc(summary)}</span>`;
}

export async function openRequestDetail(id, mode, refresh) {
  const request = await api.request(id);
  const role = state.user.role;
  let footer = '<button class="btn" data-close-modal>Close</button>';

  if (role === 'BORROWER') {
    if (['RELEASED', 'RECEIVED', 'PARTIALLY_RELEASED'].includes(request.status)) {
      footer = `<button class="btn" data-close-modal>Close</button><button class="btn danger" data-delete-request>Delete request</button><button class="btn primary" data-receive>Confirm receipt</button>`;
    } else if (['PENDING', 'NEEDS_ACTION', 'PENDING_STAFF'].includes(request.status)) {
      footer = `<button class="btn" data-close-modal>Close</button><button class="btn danger" data-delete-request>Delete request</button><a class="btn secondary" href="#/request?edit=${id}">Edit request</a>`;
    } else {
      footer = `<button class="btn" data-close-modal>Close</button><button class="btn danger" data-delete-request>Delete request</button>`;
    }
  } else if (role === 'ADMIN' || role === 'DIRECTOR') {
    footer = `<button class="btn" data-close-modal>Close</button>
      <a class="btn secondary" href="${api.exportForm(id)}" target="_blank" rel="noopener">Print form</a>
      <button class="btn danger" data-review="false">Decline</button>
      <button class="btn primary" data-review="true">Approve</button>`;
  } else {
    footer = `<button class="btn" data-close-modal>Close</button>
      <a class="btn secondary" href="${api.exportForm(id)}" target="_blank" rel="noopener">Print form</a>
      <button class="btn primary" id="staffActions">Staff actions</button>`;
  }

  const reviewBox = (role === 'ADMIN' || role === 'DIRECTOR')
    ? `<label class="field">Review remarks<textarea id="reviewRemarks" maxlength="1000" placeholder="Optional reason or note"></textarea></label><div class="divider"></div>`
    : '';
  const modal = openModal({
    title: `Request ${request.requestCode}`,
    body: reviewBox + requestFormHtml(request),
    footer,
    wide: true
  });
  modal.root.querySelector('[data-close-modal]')?.addEventListener('click', modal.close);

  modal.root.querySelector('[data-receive]')?.addEventListener('click', async (event) => {
    await withBusy(event.currentTarget, async () => {
      try {
        await api.receive(id);
        modal.close(); toast('Receipt confirmed.', 'success'); refresh?.();
      } catch (error) { handleError(error); }
    });
  });

  modal.root.querySelector('[data-delete-request]')?.addEventListener('click', async () => {
    if (!await confirmDialog('Delete this request? You can restore it briefly from My Requests.', 'Delete request')) return;
    try {
      await api.deleteRequest(id);
      modal.close(); toast('Request deleted.', 'success'); refresh?.();
    } catch (error) { handleError(error); }
  });

  modal.root.querySelectorAll('[data-review]').forEach((button) =>
    button.addEventListener('click', async () => {
      const approve = button.dataset.review === 'true';
      const remarks = modal.root.querySelector('#reviewRemarks')?.value || '';
      await withBusy(button, async () => {
        try {
          if (role === 'ADMIN') await api.adminReview(id, approve, remarks);
          else await api.directorReview(id, approve, remarks);
          modal.close(); toast(approve ? 'Request approved.' : 'Request declined.', 'success'); refresh?.();
        } catch (error) { handleError(error); }
      });
    }));

  modal.root.querySelector('#staffActions')?.addEventListener('click', async () => {
    modal.close();
    await openStaffActions(id, refresh);
  });
}
