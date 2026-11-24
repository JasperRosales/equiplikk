import { api } from '../api.js';
import { state } from '../state.js';
import { router } from '../router.js';
import { esc, fmtDate, statusBadge, table, empty, page, handleError, showRequest } from '../ui.js';

export async function renderDashboard(container) {
  container.innerHTML = '<div class="card empty">Loading dashboard…</div>';
  try {
    const data = await api.dashboard();
    const cards = (data.cards || []).map((card) => `
      <button class="card stat-card link" data-target="${esc(card.target)}">
        <span class="value">${esc(card.value)}</span>
        <span class="label">${esc(card.label)}</span>
        <span class="muted">${esc(card.unit)}</span>
      </button>`).join('');

    const recent = (data.recent || []).map((request) => [
      `<strong>${esc(request.requestCode)}</strong>`,
      esc(request.borrowerName),
      esc(fmtDate(request.eventDate)),
      statusBadge(request.status),
      `<button class="btn small secondary" data-detail="${request.requestId}">View</button>`
    ]);

    container.innerHTML = page(
      `Welcome, ${(state.user?.fullName || '').split(' ')[0] || 'there'}`,
      `${esc(data.displayRole || '')} · today's activity at a glance`,
      state.user?.role === 'BORROWER' ? `<a class="btn primary" href="#/request">New request</a>` : '',
      `<div class="grid cards">${cards || empty('No dashboard counters available.')}</div>
       <div class="card"><div class="section-title"><h3>Recent requests</h3></div>
       ${table(['Code', 'Borrower', 'Event date', 'Status', ''], recent, 'No requests with activity today.')}</div>`
    );

    container.querySelectorAll('[data-target]').forEach((button) =>
      button.addEventListener('click', () => router.go(button.dataset.target)));
    container.querySelectorAll('[data-detail]').forEach((button) =>
      button.addEventListener('click', async () => {
        try { await showRequest(Number(button.dataset.detail)); }
        catch (error) { handleError(error); }
      }));
  } catch (error) {
    container.innerHTML = page('Dashboard', '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
}
