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

export async function renderRequests(container, mode = 'active') {
  const [title, subtitle] = TITLES[mode] || TITLES.active;
  container.innerHTML = '<div class="card empty">Loading requests…</div>';
  try {
    const loader = LOADERS[mode] || LOADERS.active;
    const rows = await loader();
    const selected = state.params.get('status') || '';
    const statuses = [...new Set(rows.map((row) => row.status).filter(Boolean))].sort();

    container.innerHTML = page(title, subtitle, `
      <div class="filters">
        <label class="field">Search requests
          <input id="requestSearch" type="search" placeholder="Code, borrower, venue or purpose">
        </label>
        <label class="field">Status filter
          <select id="statusFilter"><option value="">All statuses</option>
            ${statuses.map((status) => `<option value="${esc(status)}" ${selected === status ? 'selected' : ''}>${esc(status.replaceAll('_', ' '))}</option>`).join('')}
          </select>
        </label>
      </div>`, `<div id="requestTable"></div>`);

    const paint = () => {
      const search = container.querySelector('#requestSearch').value.trim().toLowerCase();
      const filtered = rows.filter((request) => {
        if (selected && request.status !== selected) return false;
        if (!search) return true;
        return [request.requestCode, request.borrowerName, request.venue, request.purpose]
          .filter(Boolean).join(' ').toLowerCase().includes(search);
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
      const value = event.target.value;
      router.go(value ? `${location.hash.replace(/^#\/?/, '').split('?')[0]}?status=${encodeURIComponent(value)}` : mode);
    });
    paint();
  } catch (error) {
    container.innerHTML = page(title, '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
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
