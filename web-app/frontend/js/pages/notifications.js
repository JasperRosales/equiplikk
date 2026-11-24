import { api } from '../api.js';
import {
  esc, fmtDate, fmtDateTime, fmtTime, statusBadge, page, openModal,
  handleError, toast, confirmDialog, showRequest, empty
} from '../ui.js';

let lastDeletedIds = [];
let notificationSearch = '';
let notificationReadFilter = '';
let notificationDate = '';
let notificationSort = 'newest';

export async function renderNotifications(container) {
  container.innerHTML = '<div class="card empty">Loading notifications…</div>';
  try {
    const rows = await api.notifications();
    const category = new URLSearchParams(location.hash.split('?')[1] || '').get('category') || '';
    const categories = [...new Set(rows.map((row) => row.category).filter(Boolean))];
    const unreadCount = rows.filter((row) => !row.read).length;

    container.innerHTML = page('Notifications', 'Requests, releases, returns and account updates', `
      <div class="filters">
        <label class="field">Search
          <input id="notificationSearch" type="search" value="${esc(notificationSearch)}" placeholder="Title, message, request or borrower">
        </label>
        <label class="field">Category
          <select id="categoryFilter"><option value="">All categories</option>
            ${categories.map((value) => `<option value="${esc(value)}" ${category === value ? 'selected' : ''}>${esc(value.replaceAll('_', ' '))}</option>`).join('')}
          </select>
        </label>
        <label class="field">Read status
          <select id="readFilter">
            <option value="">All notifications</option>
            <option value="unread" ${notificationReadFilter === 'unread' ? 'selected' : ''}>Unread only</option>
            <option value="read" ${notificationReadFilter === 'read' ? 'selected' : ''}>Read only</option>
          </select>
        </label>
        <label class="field">Received date
          <input id="notificationDate" type="date" value="${esc(notificationDate)}">
        </label>
        <label class="field">Sort
          <select id="notificationSort">
            <option value="newest" ${notificationSort === 'newest' ? 'selected' : ''}>Newest first</option>
            <option value="oldest" ${notificationSort === 'oldest' ? 'selected' : ''}>Oldest first</option>
          </select>
        </label>
        <span class="badge">${esc(unreadCount)} unread</span>
        <button class="btn small" id="refreshNotifications">Refresh</button>
        <button class="btn small" id="clearNotificationDate">Clear date filter</button>
        <button class="btn small" id="markAll">Mark all read</button>
        <button class="btn small danger" id="deleteAll">Delete all</button>
        ${lastDeletedIds.length ? '<button class="btn small" id="undoAll">Undo delete all</button>' : ''}
      </div>
      ${activeFilterHtml(category)}
    `, `<div id="notificationList"></div>`);

    const paint = () => {
      const term = notificationSearch.trim().toLowerCase();
      const filtered = rows.filter((item) => {
        if (category && item.category !== category) return false;
        if (notificationReadFilter === 'unread' && item.read) return false;
        if (notificationReadFilter === 'read' && !item.read) return false;
        if (notificationDate && dayOnly(item.createdAt) !== notificationDate) return false;
        if (!term) return true;
        return [item.title, item.message, item.requestCode, item.borrowerName,
          item.category, equipmentSummary(item, false)]
          .filter(Boolean).join(' ').toLowerCase().includes(term);
      });
      filtered.sort((a, b) => {
        const left = String(a.createdAt || '');
        const right = String(b.createdAt || '');
        return notificationSort === 'oldest' ? left.localeCompare(right) : right.localeCompare(left);
      });
      container.querySelector('#notificationList').innerHTML = filtered.length
        ? `<div class="notification-list">${filtered.map(notificationCard).join('')}</div>`
        : empty('You have no notifications.');
      bindRowActions(container, rows);
    };

    container.querySelector('#notificationSearch').addEventListener('input', (event) => {
      notificationSearch = event.target.value;
      paint();
    });
    container.querySelector('#readFilter').addEventListener('change', (event) => {
      notificationReadFilter = event.target.value;
      paint();
    });
    container.querySelector('#notificationDate').addEventListener('change', (event) => {
      notificationDate = event.target.value;
      paint();
    });
    container.querySelector('#notificationSort').addEventListener('change', (event) => {
      notificationSort = event.target.value;
      paint();
    });
    container.querySelector('#clearNotificationDate').addEventListener('click', () => {
      notificationDate = '';
      container.querySelector('#notificationDate').value = '';
      paint();
    });
    container.querySelector('#refreshNotifications').addEventListener('click', () =>
      renderNotifications(container));
    container.querySelector('#categoryFilter').addEventListener('change', (event) => {
      notificationSearch = container.querySelector('#notificationSearch').value;
      notificationReadFilter = container.querySelector('#readFilter').value;
      notificationDate = container.querySelector('#notificationDate').value;
      notificationSort = container.querySelector('#notificationSort').value;
      location.hash = `#/notifications${event.target.value ? '?category=' + encodeURIComponent(event.target.value) : ''}`;
    });
    container.querySelector('#markAll').addEventListener('click', async () => {
      try { await api.markAllRead(); toast('All notifications marked read.', 'success'); renderNotifications(container); window.refreshUnread?.(); }
      catch (error) { handleError(error); }
    });
    container.querySelector('#deleteAll').addEventListener('click', async () => {
      if (!await confirmDialog('Delete all notifications? You can undo once.', 'Delete notifications')) return;
      try {
        lastDeletedIds = rows.map((row) => row.notificationId);
        const result = await api.deleteAllNotifications();
        toast(`${result.deleted ?? lastDeletedIds.length} notification(s) deleted.`, 'success');
        renderNotifications(container); window.refreshUnread?.();
      } catch (error) { handleError(error); }
    });
    container.querySelector('#undoAll')?.addEventListener('click', async () => {
      try {
        const result = await api.restoreAllNotifications(lastDeletedIds);
        lastDeletedIds = [];
        toast(`${result.restored ?? 0} notification(s) restored.`, 'success');
        renderNotifications(container); window.refreshUnread?.();
      } catch (error) { handleError(error); }
    });
    paint();
  } catch (error) {
    container.innerHTML = page('Notifications', '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
}

function activeFilterHtml(category) {
  const chips = [];
  if (category) chips.push(`<span class="filter-chip">Category: ${esc(category.replaceAll('_', ' '))}</span>`);
  if (notificationReadFilter) chips.push(`<span class="filter-chip">Read: ${esc(notificationReadFilter)}</span>`);
  if (notificationDate) chips.push(`<span class="filter-chip">Received: ${esc(fmtDate(notificationDate))}</span>`);
  if (notificationSearch.trim()) chips.push(`<span class="filter-chip">Search: ${esc(notificationSearch.trim())}</span>`);
  if (!chips.length) return '';
  return `<div class="active-filters"><span class="muted">Active filters:</span>${chips.join('')}</div>`;
}

function notificationCard(item) {
  const requestNumber = item.requestCode || (item.requestId ? `#${item.requestId}` : '—');
  const badge = item.requestStatus
    ? statusBadge(item.requestStatus)
    : `<span class="badge">${esc(item.categoryLabel || item.action || 'Notification')}</span>`;
  return `
    <article class="notification-item ${item.read ? '' : 'unread'}">
      <div class="notification-main" data-notification-open="${item.notificationId}"
           role="button" tabindex="0" aria-label="Open details for ${esc(item.title || 'notification')}">
        <div class="notification-head">
          ${item.read ? '' : '<span class="unread-dot" title="Unread"></span>'}
          <strong>${esc(item.title || 'Notification')}</strong>
          ${badge}
        </div>
        <p class="notification-message">${esc(item.message || '')}</p>
        <div class="notification-equipment">
          <span class="meta-label">Requested equipment</span>
          ${esc(equipmentSummary(item))}
        </div>
        <div class="notification-meta">
          <span><span class="meta-label">Received</span> ${esc(fmtDateTime(item.createdAt))}</span>
          <span><span class="meta-label">Request</span> ${esc(requestNumber)}</span>
          <span><span class="meta-label">Event date</span> ${esc(eventDateSummary(item))}</span>
        </div>
      </div>
      <div class="notification-actions">
        <button class="btn small secondary" data-detail="${item.notificationId}">Details</button>
        ${item.read ? '' : `<button class="btn small secondary" data-read="${item.notificationId}">Mark read</button>`}
        <button class="btn small danger" data-delete="${item.notificationId}">Delete</button>
      </div>
    </article>`;
}

function bindRowActions(container, rows) {
  container.querySelectorAll('[data-notification-open]').forEach((node) => {
    const open = () => {
      const item = rows.find((row) => row.notificationId === Number(node.dataset.notificationOpen));
      if (item) openDetail(item);
    };
    node.addEventListener('click', open);
    node.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        open();
      }
    });
  });
  container.querySelectorAll('[data-detail]').forEach((button) =>
    button.addEventListener('click', () => {
      const item = rows.find((row) => row.notificationId === Number(button.dataset.detail));
      if (item) openDetail(item);
    }));
  container.querySelectorAll('[data-read]').forEach((button) =>
    button.addEventListener('click', async () => {
      try { await api.markRead(Number(button.dataset.read)); renderNotifications(container); window.refreshUnread?.(); }
      catch (error) { handleError(error); }
    }));
  container.querySelectorAll('[data-delete]').forEach((button) =>
    button.addEventListener('click', async () => {
      try {
        await api.deleteNotification(Number(button.dataset.delete));
        toast('Notification deleted.', 'success'); renderNotifications(container); window.refreshUnread?.();
      } catch (error) { handleError(error); }
    }));
}

function dayOnly(value) {
  return value ? String(value).slice(0, 10) : '';
}

function equipmentSummary(item, concise = true) {
  const items = item.equipmentItems || [];
  if (!items.length) return item.requestEquipment || '—';
  const parts = items.map((row) => `${row.equipmentName} (${row.requestedQuantity})`);
  if (!concise || parts.length <= 2) return parts.join(', ');
  return `${parts.slice(0, 2).join(', ')} +${parts.length - 2} more`;
}

function eventDateSummary(item) {
  const dates = (item.eventDates?.length ? item.eventDates : [item.eventDate])
    .filter(Boolean).map((date) => fmtDate(date)).filter((date) => date && date !== '—');
  if (!dates.length) return '—';
  if (dates.length <= 2) return dates.join(' · ');
  return `${dates[0]} · ${dates[1]} · +${dates.length - 2} more`;
}

function timeSummary(item) {
  if (!item.startTime && !item.endTime) return '—';
  return `${fmtTime(item.startTime)} – ${fmtTime(item.endTime)}`;
}

async function openDetail(item) {
  if (item.requestId) {
    try {
      await showRequest(item.requestId);
      return;
    } catch (error) {
      handleError(error);
    }
  }
  const modal = openModal({
    title: item.title || 'Notification details',
    body: `<div class="stack">
      <p>${esc(item.message || '')}</p>
      <dl class="kv">
        <dt>Borrower</dt><dd>${esc(item.borrowerName || '—')}</dd>
        <dt>Department</dt><dd>${esc(item.department || '—')}</dd>
        <dt>Request</dt><dd>${esc(item.requestCode || '—')}</dd>
        <dt>Requested equipment</dt><dd>${esc(equipmentSummary(item, false))}</dd>
        <dt>Categories</dt><dd>${esc(item.requestCategories || '—')}</dd>
        <dt>Event date</dt><dd>${esc(eventDateSummary(item))}</dd>
        <dt>Time</dt><dd>${esc(timeSummary(item))}</dd>
        <dt>Received</dt><dd>${esc(fmtDateTime(item.createdAt))}</dd>
        <dt>Status</dt><dd>${item.requestStatus ? statusBadge(item.requestStatus) : esc(item.action || '—')}</dd>
      </dl>
    </div>`,
    footer: '<button class="btn" data-close>Close</button>',
    wide: true
  });
  modal.root.querySelector('[data-close]').addEventListener('click', modal.close);
}
