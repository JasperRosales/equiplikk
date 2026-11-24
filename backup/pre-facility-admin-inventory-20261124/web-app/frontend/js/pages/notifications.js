import { api } from '../api.js';
import { esc, fmtDate, fmtDateTime, fmtTime, statusBadge, page, table, openModal, handleError, toast, confirmDialog } from '../ui.js';

let lastDeletedIds = [];
let notificationSearch = '';
let notificationReadFilter = '';

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
        <span class="badge">${esc(unreadCount)} unread</span>
        <button class="btn small" id="markAll">Mark all read</button>
        <button class="btn small danger" id="deleteAll">Delete all</button>
        ${lastDeletedIds.length ? '<button class="btn small" id="undoAll">Undo delete all</button>' : ''}
      </div>`, `<div id="notificationTable"></div>`);

    const paint = () => {
      const search = notificationSearch.trim().toLowerCase();
      const filtered = rows.filter((item) => {
        if (category && item.category !== category) return false;
        if (notificationReadFilter === 'unread' && item.read) return false;
        if (notificationReadFilter === 'read' && !item.read) return false;
        if (!search) return true;
        return [item.title, item.message, item.requestCode, item.borrowerName,
          item.category, equipmentSummary(item)].filter(Boolean).join(' ').toLowerCase().includes(search);
      });
      const body = filtered.map((item) => [
        `<strong>${item.read ? '' : '<span class="unread-dot" title="Unread"></span>'}${esc(item.title)}</strong><div class="muted">${esc(item.message)}</div>`,
        esc(equipmentSummary(item)),
        esc(item.borrowerName || '—'),
        esc(item.requestCode || '—'),
        esc(fmtDate(item.eventDate)),
        esc(timeSummary(item)),
        item.requestStatus ? statusBadge(item.requestStatus) : statusBadge(item.action || item.category),
        esc(fmtDateTime(item.createdAt)),
        `<div class="actions">
          <button class="btn small secondary" data-detail="${item.notificationId}">Details</button>
          ${item.read ? '' : `<button class="btn small secondary" data-read="${item.notificationId}">Mark read</button>`}
          <button class="btn small danger" data-delete="${item.notificationId}">Delete</button>
        </div>`
      ]);
      container.querySelector('#notificationTable').innerHTML = table(
        ['Notification', 'Equipment / quantity', 'Borrower', 'Request', 'Event date', 'Time', 'Status', 'Received', 'Actions'],
        body, 'You have no notifications.');
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
    container.querySelector('#categoryFilter').addEventListener('change', (event) => {
      notificationSearch = container.querySelector('#notificationSearch').value;
      notificationReadFilter = container.querySelector('#readFilter').value;
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

function bindRowActions(container, rows) {
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

function equipmentSummary(item) {
  const items = item.equipmentItems || [];
  if (items.length) {
    return items.map((row) => `${row.equipmentName} (${row.requestedQuantity})`).join(', ');
  }
  return item.requestEquipment || '—';
}

function timeSummary(item) {
  if (!item.startTime && !item.endTime) return '—';
  return `${fmtTime(item.startTime)} – ${fmtTime(item.endTime)}`;
}

function openDetail(item) {
  const items = item.equipmentItems || [];
  const itemRows = items.map((row) => [
    esc(row.equipmentName), esc(row.equipmentCode || '—'), esc(row.category || '—'),
    esc(row.requestedQuantity), esc(row.assignedQuantity)
  ]);
  const modal = openModal({
    title: item.title || 'Notification details',
    body: `<div class="stack">
      <p>${esc(item.message)}</p>
      <dl class="kv">
        <dt>Borrower</dt><dd>${esc(item.borrowerName || '—')}</dd>
        <dt>Request</dt><dd>${esc(item.requestCode || '—')}</dd>
        <dt>Equipment</dt><dd>${esc(equipmentSummary(item))}</dd>
        <dt>Categories</dt><dd>${esc(item.requestCategories || '—')}</dd>
        <dt>Event date</dt><dd>${esc(fmtDate(item.eventDate))}</dd>
        <dt>Time</dt><dd>${esc(timeSummary(item))}</dd>
        <dt>Status</dt><dd>${item.requestStatus ? statusBadge(item.requestStatus) : esc(item.action || '—')}</dd>
        <dt>Department</dt><dd>${esc(item.department || '—')}</dd>
        <dt>Received</dt><dd>${esc(fmtDateTime(item.createdAt))}</dd>
      </dl>
      ${items.length ? `<h3>Requested equipment</h3>${table(['Equipment', 'Code', 'Category', 'Requested', 'Assigned'], itemRows, 'No equipment details.')}` : ''}
    </div>`,
    footer: '<button class="btn" data-close>Close</button>',
    wide: true
  });
  modal.root.querySelector('[data-close]').addEventListener('click', modal.close);
}
