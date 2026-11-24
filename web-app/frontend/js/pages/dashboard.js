import { api } from '../api.js';
import { state } from '../state.js';
import { router } from '../router.js';
import { esc, fmtDate, statusBadge, empty, handleError, showRequest } from '../ui.js';
import { countUp } from '../motion.js';

/*
 * Mirrors src/ui/DashboardPanel + DashboardPanelPart1 (desktop):
 *  - welcome card with greeting and the role's main action
 *  - stat cards with the desktop's accent colours and captions
 *  - "Recent requests" list (code, venue · date, status) and "Quick actions"
 * The counters themselves come from the same server-side counters as the desktop.
 */
const STAFF_CARDS = (dept) => [
  { label: 'Total equipment', caption: 'In your inventory', tone: 'primary' },
  { label: 'Available', caption: 'Ready to assign', tone: 'success' },
  { label: 'Reserved today', caption: 'Scheduled for today', tone: 'tint' },
  { label: 'In use', caption: 'Released today', tone: 'warning' },
  { label: 'Awaiting Decision', caption: `${dept} equipment pending`, tone: 'warning' },
  { label: 'Approved', caption: `Approved by ${dept}`, tone: 'success' },
  { label: 'Declined', caption: 'Declined requests', tone: 'danger' },
  { label: 'Overdue', caption: 'Not processed on time', tone: 'danger' }
];

const BORROWER_CARDS = [
  { label: 'My requests', caption: 'Active requests', tone: 'primary' },
  { label: 'Pending', caption: 'Awaiting department review', tone: 'warning' },
  { label: 'Approved', caption: 'Partially or fully approved', tone: 'success' },
  { label: 'Released', caption: 'Equipment released to you', tone: 'tint' }
];

const REVIEWER_CARDS = (director) => [
  { label: 'Pending review', caption: director ? 'Awaiting your decision' : 'Department-processed requests', tone: director ? 'warning' : 'primary' },
  { label: 'Approved today', caption: 'Final approvals today', tone: 'success' },
  { label: 'Declined requests', caption: 'Closed after review', tone: 'danger' },
  { label: 'Overdue', caption: 'Not processed on time', tone: 'warning' }
];

function roleKind(role) {
  if (role === 'BORROWER') return 'borrower';
  if (role === 'ICT_STAFF' || role === 'FACILITY_STAFF') return 'staff';
  if (role === 'DIRECTOR') return 'director';
  return 'admin';
}

function cardSpecs(kind, role) {
  if (kind === 'borrower') return BORROWER_CARDS;
  if (kind === 'staff') return STAFF_CARDS(role === 'ICT_STAFF' ? 'ICT' : 'Facility');
  return REVIEWER_CARDS(kind === 'director');
}

/** Same role-based routing as the desktop's "Open request queue" shortcuts. */
function queueRoute(kind) {
  return kind === 'borrower' ? 'request'
    : kind === 'director' ? 'approval'
      : kind === 'staff' ? 'requests' : 'borrowing-requests';
}

function copyFor(kind) {
  return {
    action: kind === 'borrower' ? 'Request equipment' : 'Open request queue',
    subtitle: kind === 'borrower'
      ? 'Welcome back to your campus equipment workspace.'
      : 'Review request activity and manage campus equipment.',
    recentTitle: kind === 'borrower' ? 'Recent requests'
      : kind === 'director' ? 'Pending Approval'
        : kind === 'staff' ? 'Requests to process' : 'Pending requests',
    viewAll: kind === 'borrower' ? 'View all' : kind === 'director' ? 'Review all'
      : kind === 'staff' ? 'Open requests' : 'Open queue',
    viewAllRoute: kind === 'borrower' ? 'my-requests' : queueRoute(kind),
    emptyText: kind === 'borrower' ? 'Your recent requests will appear here.' : 'No requests are waiting for action.',
    shortcuts: [
      {
        title: kind === 'borrower' ? 'Request equipment' : kind === 'director' ? 'Request approval'
          : kind === 'staff' ? 'Requests' : 'Borrowing requests',
        note: kind === 'borrower' ? 'Browse inventory and create a request.'
          : kind === 'director' ? 'Review requests requiring final approval.'
            : kind === 'staff' ? 'Check and assign requests in your queue.' : 'Review staff-assigned requests.',
        route: queueRoute(kind)
      },
      {
        title: kind === 'borrower' ? 'Borrowing history' : kind === 'director' ? 'Transaction history'
          : kind === 'staff' ? 'Schedule & assignment' : 'All requests',
        note: kind === 'borrower' ? 'See your completed and past requests.'
          : kind === 'director' ? 'Search the complete transaction history.'
            : kind === 'staff' ? 'Release and track approved equipment.' : 'Search all request records.',
        route: kind === 'borrower' ? 'history' : kind === 'director' ? 'transactions'
          : kind === 'staff' ? 'schedule' : 'all-requests'
      }
    ]
  };
}

function greeting() {
  const first = (state.user?.fullName || '').trim().split(/\s+/)[0];
  return `Good day, ${first || 'there'}!`;
}

export async function renderDashboard(container) {
  container.innerHTML = '<div class="card empty">Loading dashboard…</div>';
  try {
    const data = await api.dashboard();
    const role = state.user?.role || data.role;
    const kind = roleKind(role);
    const text = copyFor(kind);
    const specs = cardSpecs(kind, role);

    const cards = (data.cards || []).map((card, index) => {
      const spec = specs[index] || {};
      return `
      <button class="card stat-card link tone-${spec.tone || 'primary'}" style="--i:${index}" data-target="${esc(card.target)}">
        <span class="label">${esc(spec.label || card.label)}</span>
        <span class="value" data-count="${esc(card.value)}">${esc(card.value)}</span>
        <span class="muted">${esc(spec.caption || card.unit)}</span>
      </button>`;
    });
    // Staff dashboards show two rows of four, like the desktop.
    const cardRows = cards.length > 4
      ? `<div class="grid cards">${cards.slice(0, 4).join('')}</div><div class="grid cards">${cards.slice(4).join('')}</div>`
      : `<div class="grid cards">${cards.join('')}</div>`;

    const recent = (data.recent || []).map((request, index) => `
      <button class="recent-row" style="--i:${index}" data-detail="${request.requestId}">
        <span><strong>${esc(request.requestCode)}</strong>
          <small>${esc(request.venue || '—')} · ${esc(fmtDate(request.eventDate))}</small></span>
        ${statusBadge(request.overallStatus || request.status)}
      </button>`).join('');

    container.innerHTML = `
      <div class="stack">
        <section class="card welcome-card">
          <div><h2>${esc(greeting())}</h2><p>${esc(text.subtitle)}</p></div>
          <a class="btn primary" href="#/${queueRoute(kind)}">${esc(text.action)}</a>
        </section>
        ${cards.length ? cardRows : `<div class="card">${empty('No dashboard counters available.')}</div>`}
        <div class="dash-bottom">
          <section class="card">
            <div class="section-title"><h3>${esc(text.recentTitle)}</h3>
              <button class="btn small secondary" data-go="${text.viewAllRoute}">${esc(text.viewAll)}</button></div>
            <div class="recent-list">${recent || `<p class="recent-empty">${esc(text.emptyText)}</p>`}</div>
          </section>
          <section class="card">
            <div class="section-title"><h3>Quick actions</h3></div>
            <div class="quick-actions">
              ${text.shortcuts.map((item, index) => `
                <button class="quick-action" style="--i:${index}" data-go="${item.route}">
                  <strong>${esc(item.title)}</strong><span class="muted quick-note">${esc(item.note)}</span>
                </button>`).join('')}
            </div>
          </section>
        </div>
      </div>`;

    container.querySelectorAll('[data-count]').forEach((node) =>
      countUp(node, node.dataset.count));
    container.querySelectorAll('[data-target]').forEach((button) =>
      button.addEventListener('click', () => router.go(button.dataset.target)));
    container.querySelectorAll('[data-go]').forEach((button) =>
      button.addEventListener('click', () => router.go(button.dataset.go)));
    container.querySelectorAll('[data-detail]').forEach((button) =>
      button.addEventListener('click', async () => {
        try { await showRequest(Number(button.dataset.detail)); }
        catch (error) { handleError(error); }
      }));
  } catch (error) {
    container.innerHTML = `<div class="stack"><div class="alert error">${esc(error.message)}</div></div>`;
  }
}
